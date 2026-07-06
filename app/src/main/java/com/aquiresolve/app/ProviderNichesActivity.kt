package com.aquiresolve.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.databinding.ActivityProviderNichesBinding
import com.aquiresolve.app.utils.ServiceNicheCatalog
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class ProviderNichesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProviderNichesBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var currentNiches: List<String> = emptyList()
    private var providerName: String = ""

    private var docPhotoUri: Uri? = null
    private var selfieUri: Uri? = null
    private val proofPhotoUris = mutableListOf<Uri>()

    // Android Photo Picker — não exige permissão de mídia (política do Google Play)
    private val docPhotoLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            docPhotoUri = uri
            Glide.with(this).load(uri).into(binding.ivDocPhoto)
            binding.btnAttachDoc.visibility = View.GONE
        }
    }

    private val selfieLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selfieUri = uri
            Glide.with(this).load(uri).into(binding.ivSelfie)
            binding.btnTakeSelfie.visibility = View.GONE
        }
    }

    private val proofLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        if (uris.isNotEmpty()) {
            val remainingSlots = 3 - proofPhotoUris.size
            val urisToAdd = uris.take(remainingSlots)
            proofPhotoUris.addAll(urisToAdd)
            renderProofPhotos()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderNichesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupPhotoPickers()
        
        lifecycleScope.launch {
            setupNicheChips()
            loadCurrentNiches()
            loadPendingRequest()
        }

        binding.btnSubmitRequest.setOnClickListener {
            submitRequest()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupPhotoPickers() {
        val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        binding.btnAttachDoc.setOnClickListener { docPhotoLauncher.launch(imageOnly) }
        binding.ivDocPhoto.setOnClickListener { docPhotoLauncher.launch(imageOnly) }

        binding.btnTakeSelfie.setOnClickListener { selfieLauncher.launch(imageOnly) }
        binding.ivSelfie.setOnClickListener { selfieLauncher.launch(imageOnly) }

        binding.btnAttachProof.setOnClickListener {
            if (proofPhotoUris.size < 3) {
                proofLauncher.launch(imageOnly)
            } else {
                Toast.makeText(this, "Máximo de 3 comprovantes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderProofPhotos() {
        binding.llProofPhotos.removeAllViews()
        for ((index, uri) in proofPhotoUris.withIndex()) {
            val view = layoutInflater.inflate(R.layout.item_home_banner, binding.llProofPhotos, false)
            val imageView = view.findViewById<ImageView>(R.id.ivBannerImage) // Reusing banner image view or we can just create an ImageView dynamically
            
            val dynamicImageView = ImageView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(300, 300).apply {
                    setMargins(0, 0, 16, 0)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setOnClickListener {
                    proofPhotoUris.removeAt(index)
                    renderProofPhotos()
                }
            }
            Glide.with(this).load(uri).into(dynamicImageView)
            binding.llProofPhotos.addView(dynamicImageView)
        }
        binding.btnAttachProof.isEnabled = proofPhotoUris.size < 3
    }

    private suspend fun setupNicheChips() {
        val niches = ServiceNicheCatalog.selectableNiches()
        binding.chipGroupEditNiches.removeAllViews()
        
        for (niche in niches) {
            val chip = Chip(this).apply {
                text = niche
                isCheckable = true
                isClickable = true
            }
            binding.chipGroupEditNiches.addView(chip)
        }
    }

    private suspend fun loadCurrentNiches() {
        val uid = auth.uid ?: return
        try {
            val doc = db.collection("providers").document(uid).get().await()
            if (doc.exists()) {
                providerName = doc.getString("fullName") ?: ""
                @Suppress("UNCHECKED_CAST")
                currentNiches = (doc.get("services") as? List<String>) ?: emptyList()
                
                binding.chipGroupCurrentNiches.removeAllViews()
                if (currentNiches.isEmpty()) {
                    binding.tvNoNiches.visibility = View.VISIBLE
                } else {
                    binding.tvNoNiches.visibility = View.GONE
                    for (niche in currentNiches) {
                        val chip = Chip(this).apply {
                            text = niche
                            isCheckable = false
                            isClickable = false
                        }
                        binding.chipGroupCurrentNiches.addView(chip)
                        
                        // Check corresponding chip in edit group
                        for (i in 0 until binding.chipGroupEditNiches.childCount) {
                            val editChip = binding.chipGroupEditNiches.getChildAt(i) as Chip
                            if (editChip.text.toString() == niche) {
                                editChip.isChecked = true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao carregar nichos atuais", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun loadPendingRequest() {
        val uid = auth.uid ?: return
        try {
            val result = db.collection("provider_specialty_requests")
                .whereEqualTo("providerId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .get().await()

            if (!result.isEmpty) {
                val request = result.documents[0]
                val status = request.getString("status") ?: ""
                
                if (status == "pending" || status == "rejected") {
                    binding.cardPendingRequest.visibility = View.VISIBLE
                    
                    if (status == "pending") {
                        binding.cardEditNiches.visibility = View.GONE
                        binding.tvRequestStatus.text = "Pendente"
                        binding.tvRequestStatus.setTextColor(getColor(R.color.warning))
                        binding.tvRequestStatus.background = null
                    } else {
                        binding.cardEditNiches.visibility = View.VISIBLE
                        binding.tvRequestStatus.text = "Rejeitada"
                        binding.tvRequestStatus.setTextColor(getColor(R.color.error))
                        binding.tvRequestStatus.background = null
                        
                        binding.llRejectionReason.visibility = View.VISIBLE
                        binding.tvRejectionReason.text = request.getString("rejectionReason") ?: "Sem motivo informado."
                    }
                    
                    binding.tvRequestJustification.text = request.getString("justificationText") ?: ""
                    
                    @Suppress("UNCHECKED_CAST")
                    val requestedServices = (request.get("requestedServices") as? List<String>) ?: emptyList()
                    binding.chipGroupRequestedNiches.removeAllViews()
                    for (niche in requestedServices) {
                        val chip = Chip(this).apply {
                            text = niche
                            isCheckable = false
                        }
                        binding.chipGroupRequestedNiches.addView(chip)
                    }
                } else {
                    binding.cardPendingRequest.visibility = View.GONE
                    binding.cardEditNiches.visibility = View.VISIBLE
                }
            } else {
                binding.cardPendingRequest.visibility = View.GONE
                binding.cardEditNiches.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            // Ignored, maybe no requests yet or no index
            binding.cardPendingRequest.visibility = View.GONE
            binding.cardEditNiches.visibility = View.VISIBLE
        }
    }

    private fun getSelectedNiches(): List<String> {
        val selected = mutableListOf<String>()
        for (i in 0 until binding.chipGroupEditNiches.childCount) {
            val chip = binding.chipGroupEditNiches.getChildAt(i) as Chip
            if (chip.isChecked) {
                selected.add(chip.text.toString())
            }
        }
        return ServiceNicheCatalog.canonicalizeProviderServices(selected)
    }

    private fun submitRequest() {
        val selectedNiches = getSelectedNiches()
        val justification = binding.etJustification.text.toString().trim()
        
        if (selectedNiches.isEmpty()) {
            Toast.makeText(this, "Selecione pelo menos um nicho", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedNiches.toSet() == currentNiches.toSet()) {
            Toast.makeText(this, "Os nichos selecionados são iguais aos atuais", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (justification.length < 20) {
            Toast.makeText(this, "A justificativa deve ter no mínimo 20 caracteres", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (docPhotoUri == null) {
            Toast.makeText(this, "A foto do documento é obrigatória", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selfieUri == null) {
            Toast.makeText(this, "A selfie com documento é obrigatória", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (proofPhotoUris.isEmpty()) {
            Toast.makeText(this, "Anexe pelo menos 1 comprovante", Toast.LENGTH_SHORT).show()
            return
        }
        
        val uid = auth.uid ?: return
        
        binding.btnSubmitRequest.isEnabled = false
        binding.progressSubmit.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                
                // Upload document photo
                val docRef = storage.reference.child("provider_documents/$uid/niche_doc_$timestamp.jpg")
                docRef.putFile(docPhotoUri!!).await()
                val docUrl = docRef.downloadUrl.await().toString()
                
                // Upload selfie
                val selfieRef = storage.reference.child("provider_documents/$uid/niche_selfie_$timestamp.jpg")
                selfieRef.putFile(selfieUri!!).await()
                val selfieUrl = selfieRef.downloadUrl.await().toString()
                
                // Upload proofs
                val proofUrls = mutableListOf<String>()
                for ((index, uri) in proofPhotoUris.withIndex()) {
                    val proofRef = storage.reference.child("provider_documents/$uid/niche_proof_${timestamp}_$index.jpg")
                    proofRef.putFile(uri).await()
                    proofUrls.add(proofRef.downloadUrl.await().toString())
                }
                
                val allUrls = mutableListOf<String>().apply {
                    add(docUrl)
                    add(selfieUrl)
                    addAll(proofUrls)
                }
                
                val requestData = mapOf(
                    "providerId" to uid,
                    "providerName" to providerName,
                    "requestedServices" to selectedNiches,
                    "currentServices" to currentNiches,
                    "justificationText" to justification,
                    "documentPhotoUrl" to docUrl,
                    "selfiePhotoUrl" to selfieUrl,
                    "proofPhotoUrls" to proofUrls,
                    "documentUrls" to allUrls,
                    "status" to "pending",
                    "createdAt" to Timestamp.now()
                )
                
                db.collection("provider_specialty_requests").add(requestData).await()
                
                Toast.makeText(this@ProviderNichesActivity, "Solicitação enviada com sucesso!", Toast.LENGTH_LONG).show()
                loadPendingRequest()
            } catch (e: Exception) {
                Toast.makeText(this@ProviderNichesActivity, "Erro ao enviar solicitação: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSubmitRequest.isEnabled = true
                binding.progressSubmit.visibility = View.GONE
            }
        }
    }
}
