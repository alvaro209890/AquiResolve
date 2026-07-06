package com.aquiresolve.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.aquiresolve.app.databinding.FragmentProviderProfileBinding
import com.aquiresolve.app.utils.ActivityPermissionManager
import com.aquiresolve.app.utils.ServiceNicheCatalog
import com.google.android.material.chip.Chip
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * ProviderProfileFragment - Fragment para configurações de perfil do prestador
 */
class ProviderProfileFragment : Fragment() {

    private var _binding: FragmentProviderProfileBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var authManager: FirebaseAuthManager
    private lateinit var permissionManager: ActivityPermissionManager
    private lateinit var firebaseImageManager: FirebaseImageManager
    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()
    
    // Launcher para galeria (Android Photo Picker — sem permissão de mídia)
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { launchCrop(it) }
    }

    // Launcher para câmera
    private var cameraImageUri: Uri? = null
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraImageUri?.let { launchCrop(it) }
        cameraImageUri = null
    }

    // Documentos comprobatórios anexados à solicitação de alteração de nichos (ainda não enviados)
    private val pendingServiceDocs = mutableListOf<Uri>()
    private val serviceDocsLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        uris.forEach { uri ->
            if (pendingServiceDocs.size >= MAX_SERVICE_DOCS) {
                showToast("⚠️ Máximo de $MAX_SERVICE_DOCS documentos")
                return@forEach
            }
            if (pendingServiceDocs.none { it.toString() == uri.toString() }) {
                pendingServiceDocs.add(uri)
            }
        }
        renderServiceDocs()
    }

    // Launcher para UCrop (recorte 1:1 estilo WhatsApp)
    private val uCropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when {
            result.resultCode == Activity.RESULT_OK -> {
                result.data?.let { data ->
                    UCrop.getOutput(data)?.let { uploadAndUpdateProfile(it) }
                }
            }
            result.resultCode == UCrop.RESULT_ERROR -> {
                result.data?.let { data ->
                    val cropError = UCrop.getError(data)
                    showToast("Erro ao recortar: ${cropError?.message ?: "Desconhecido"}")
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProviderProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Garantir Firebase inicializado
        if (!FirebaseConfig.isInitialized()) {
            FirebaseConfig.initialize(requireContext())
        }
        
        // Inicializar managers
        authManager = FirebaseAuthManager(requireContext())
        permissionManager = ActivityPermissionManager(requireActivity())
        firebaseImageManager = FirebaseImageManager()
        
        setupClickListeners()
        setupServiceNicheChips()
        loadProviderData()
        loadPricingTable()
        loadProviderRating()
        loadPendingSpecialtyRequest()
    }

    /**
     * Configura os listeners de clique
     */
    private fun setupClickListeners() {
        // Foto de perfil
        binding.ivProfilePhoto.setOnClickListener {
            showImagePickerDialog()
        }
        
        binding.btnChangePhoto.setOnClickListener {
            showImagePickerDialog()
        }
        
        // Salvar informações pessoais
        binding.btnSavePersonalInfo.setOnClickListener {
            savePersonalInfo()
        }
        
        // Salvar serviços
        binding.btnSaveServices.setOnClickListener {
            saveServices()
        }

        // Anexar documento comprobatório dos nichos
        binding.btnAttachServiceDoc.setOnClickListener {
            serviceDocsLauncher.launch("*/*")
        }
        
        // Salvar dados bancários
        binding.btnSaveBankData.setOnClickListener {
            saveBankData()
        }

        // Ver avaliações do prestador
        binding.btnViewReviews.setOnClickListener {
            val userId = getCurrentUserId() ?: return@setOnClickListener
            val name = binding.etFullName.text.toString().ifBlank { "Prestador" }
            val intent = android.content.Intent(requireContext(), ProviderReviewsActivity::class.java)
            intent.putExtra("provider_id", userId)
            intent.putExtra("provider_name", name)
            startActivity(intent)
        }
    }

    /**
     * Carrega os dados do prestador
     */
    private fun loadProviderData() {
        val localUser = authManager.getLocalUserData()
        val userId = localUser?.uid
            ?: authManager.getCurrentUser()?.uid
            ?: firebaseAuth.currentUser?.uid
            ?: return

        lifecycleScope.launch {
            try {
                val providerDoc = firestore.collection("providers")
                    .document(userId)
                    .get()
                    .await()

                val providerFullName = providerDoc.getString("fullName").orEmpty()
                val providerPhone = providerDoc.getString("phone").orEmpty()
                val providerCpf = providerDoc.getString("cpf").orEmpty()
                val providerPhoto = providerDoc.getString("profileImageUrl")

                binding.etFullName.setText(providerFullName.ifEmpty { localUser?.fullName.orEmpty() })
                binding.etPhone.setText(providerPhone.ifEmpty { localUser?.phone.orEmpty() })
                binding.etCpf.setText(providerCpf)

                val rawServices = (providerDoc.get("services") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()
                loadSelectedServices(rawServices)

                val bankData = providerDoc.get("bank") as? Map<*, *>
                binding.etBankName.setText(bankData?.get("bankName") as? String ?: "")
                binding.etAgency.setText(bankData?.get("agency") as? String ?: "")
                binding.etAccount.setText(bankData?.get("account") as? String ?: "")

                // Foto da CONTA DE PRESTADOR apenas (providers/{uid}.profileImageUrl).
                // SEM fallback para a foto de cliente (users): as fotos são separadas, então
                // a tela do prestador mostra só a foto de prestador (ou o placeholder padrão).
                loadProfileImage(providerPhoto?.takeIf { it.isNotBlank() })
            } catch (e: Exception) {
                android.util.Log.e("ProviderProfileFragment", "Erro ao carregar dados do prestador: ${e.message}")
                loadProfileImage(null)
                showToast("❌ Erro ao carregar perfil")
            }
        }
    }

    private fun setupServiceNicheChips() {
        val chipGroup = binding.chipGroupServices
        chipGroup.removeAllViews()

        ServiceNicheCatalog.selectableNiches().forEach { niche ->
            val chip = Chip(requireContext()).apply {
                text = niche
                isCheckable = true
                isClickable = true
                isFocusable = true
                isCloseIconVisible = false
            }
            chipGroup.addView(chip)
        }
    }

    /**
     * Renderiza a lista de documentos comprobatórios anexados (ainda não enviados).
     */
    private fun renderServiceDocs() {
        val container = binding.llServiceDocs
        container.removeAllViews()
        val ctx = requireContext()
        pendingServiceDocs.forEachIndexed { index, uri ->
            val row = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val name = android.widget.TextView(ctx).apply {
                text = "📎 ${resolveDisplayName(uri)}"
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_primary, null))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }
            val remove = android.widget.ImageButton(ctx).apply {
                setImageResource(R.drawable.ic_close)
                background = null
                contentDescription = "Remover documento"
                setOnClickListener {
                    if (index < pendingServiceDocs.size) {
                        pendingServiceDocs.removeAt(index)
                        renderServiceDocs()
                    }
                }
            }
            row.addView(name)
            row.addView(remove)
            container.addView(row)
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        return try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            } ?: uri.lastPathSegment ?: "documento"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "documento"
        }
    }

    /**
     * Carrega a imagem do perfil
     */
    private fun loadProfileImage(imageUrl: String?) {
        // Remover tint para que a foto carregue com cores corretas (evita aparência cinza)
        binding.ivProfilePhoto.imageTintList = null
        
        val requestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .placeholder(R.drawable.ic_person)
            .error(R.drawable.ic_person)
        
        Glide.with(this)
            .load(imageUrl?.takeIf { it.isNotEmpty() })
            .apply(requestOptions)
            .into(binding.ivProfilePhoto)
    }

    /**
     * Carrega os serviços selecionados
     */
    private fun loadSelectedServices(services: List<String>) {
        val selectedNormalized = ServiceNicheCatalog.normalizeProviderServices(services)
        val chipGroup = binding.chipGroupServices
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i)
            if (chip is com.google.android.material.chip.Chip) {
                val serviceName = chip.text.toString()
                val serviceKey = ServiceNicheCatalog.normalizeProviderServices(listOf(serviceName)).firstOrNull()
                chip.isChecked = serviceKey != null && selectedNormalized.contains(serviceKey)
            }
        }
    }

    /**
     * Carrega a nota média do prestador
     */
    private fun loadProviderRating() {
        val userId = getCurrentUserId() ?: return
        lifecycleScope.launch {
            try {
                val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("providers").document(userId).get().await()
                if (doc.exists()) {
                    val rating = doc.getDouble("rating") ?: 0.0
                    val totalRatings = (doc.getLong("totalRatings") ?: 0L).toInt()
                    if (rating > 0) {
                        binding.tvProfileRating.text = "⭐ ${String.format("%.1f", rating)}"
                        binding.tvProfileRatingCount.text = "$totalRatings avaliações"
                    } else {
                        binding.tvProfileRating.text = "⭐ —"
                        binding.tvProfileRatingCount.text = "Sem avaliações ainda"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ProviderProfile", "Erro ao carregar rating: ${e.message}")
            }
        }
    }

    /**
     * Carrega a tabela de valores do prestador
     */
    private fun loadPricingTable() {
        val container = binding.llPricingTable
        container.removeAllViews()
        val ctx = requireContext()
        val table = com.aquiresolve.app.models.ServicePricing.getProviderPricingTable()

        for ((category, services) in table) {
            // Título da categoria
            val categoryTitle = android.widget.TextView(ctx).apply {
                text = category
                setTextColor(resources.getColor(R.color.primary_color, null))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 24, 0, 8)
            }
            container.addView(categoryTitle)

            // Linha separadora
            val divider = android.view.View(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2
                ).apply { bottomMargin = 8 }
                setBackgroundColor(resources.getColor(R.color.gray_200, null))
            }
            container.addView(divider)

            for ((serviceName, value) in services) {
                val row = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 6 }
                }

                val nameView = android.widget.TextView(ctx).apply {
                    text = serviceName
                    textSize = 13f
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }

                val valueView = android.widget.TextView(ctx).apply {
                    text = "R$ ${String.format(java.util.Locale("pt", "BR"), "%.2f", value)}"
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(resources.getColor(R.color.success_color, null))
                }

                row.addView(nameView)
                row.addView(valueView)
                container.addView(row)
            }
        }
    }

    /**
     * Mostra diálogo para selecionar imagem
     */
    private fun showImagePickerDialog() {
        val userId = authManager.getLocalUserData()?.uid
            ?: authManager.getCurrentUser()?.uid
            ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            showToast("❌ Usuário não autenticado. Faça login novamente.")
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Foto do Perfil")
            .setItems(arrayOf("📷 Tirar Foto", "🖼️ Galeria")) { _, which ->
                when (which) {
                    // Câmera continua exigindo a permissão CAMERA
                    0 -> permissionManager.checkAndRequestCameraPermission(
                        onGranted = { takeProfilePhoto() },
                        onDenied = { showToast("Permissão de câmera necessária para tirar foto") }
                    )
                    // Galeria via Photo Picker — sem permissão de mídia
                    1 -> galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun takeProfilePhoto() {
        try {
            val photoFile = File(requireContext().getExternalFilesDir(null), "profile_${System.currentTimeMillis()}.jpg")
            cameraImageUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", photoFile)
            cameraLauncher.launch(cameraImageUri!!)
        } catch (e: Exception) {
            showToast("Erro ao abrir câmera: ${e.message}")
        }
    }

    private fun launchCrop(sourceUri: Uri) {
        val fileName = "profile_crop_${System.currentTimeMillis()}.jpg"
        val destinationFile = File(requireContext().cacheDir, fileName)
        val destinationUri = Uri.fromFile(destinationFile)

        val options = UCrop.Options().apply {
            setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
        }

        val uCropIntent = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withOptions(options)
            .getIntent(requireContext())

        uCropLauncher.launch(uCropIntent)
    }

    private fun uploadAndUpdateProfile(croppedUri: Uri) {
        val userId = authManager.getLocalUserData()?.uid
            ?: authManager.getCurrentUser()?.uid
            ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: return

        // Mostrar barra de carregamento durante o upload
        binding.profileImageProgress.visibility = View.VISIBLE
        binding.ivProfilePhoto.isClickable = false
        binding.btnChangePhoto.isEnabled = false

        lifecycleScope.launch {
            try {
                val uploadData = FirebaseImageManager.ImageUploadData(
                    uri = croppedUri,
                    // Arquivo dedicado do prestador, separado da foto de cliente
                    // (profile_<uid>.jpg) — assim a conta de prestador pode ter uma foto
                    // diferente da conta de cliente do mesmo usuário.
                    fileName = "provider_${userId}.jpg",
                    folder = FirebaseImageManager.FOLDER_PROFILE_IMAGES,
                    userId = userId,
                    orderId = null
                )
                when (val result = firebaseImageManager.uploadImage(requireContext(), uploadData)) {
                    is FirebaseImageManager.UploadResult.Success -> updateProfileImage(result.downloadUrl)
                    is FirebaseImageManager.UploadResult.Error -> showToast("❌ Erro no upload: ${result.message}")
                    else -> showToast("❌ Erro ao enviar foto")
                }
            } catch (e: Exception) {
                showToast("❌ Erro: ${e.message}")
            } finally {
                binding.profileImageProgress.visibility = View.GONE
                binding.ivProfilePhoto.isClickable = true
                binding.btnChangePhoto.isEnabled = true
            }
        }
    }

    /**
     * Atualiza a imagem do perfil
     */
    private fun updateProfileImage(imageUrl: String) {
        lifecycleScope.launch {
            try {
                // Esconder barra de carregamento
                binding.profileImageProgress.visibility = View.GONE
                binding.ivProfilePhoto.isClickable = true
                binding.btnChangePhoto.isEnabled = true
                
                // Remover tint para foto carregar com cores corretas
                binding.ivProfilePhoto.imageTintList = null
                
                // Carregar imagem com Glide
                val requestOptions = RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                
                Glide.with(this@ProviderProfileFragment)
                    .load(imageUrl)
                    .apply(requestOptions)
                    .into(binding.ivProfilePhoto)
                
                // Salvar URL da imagem no Firestore
                val userId = authManager.getLocalUserData()?.uid 
                    ?: authManager.getCurrentUser()?.uid
                    ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    
                if (userId != null) {
                    updateProfileImageInFirestore(userId, imageUrl)
                } else {
                    showToast("❌ Não foi possível identificar o usuário")
                }
                
            } catch (e: Exception) {
                showToast("❌ Erro ao atualizar foto: ${e.message}")
            }
        }
    }

    /**
     * Salva as informações pessoais
     */
    private fun savePersonalInfo() {
        val fullName = binding.etFullName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val cpf = binding.etCpf.text.toString().trim()
        
        if (fullName.isEmpty() || phone.isEmpty() || cpf.isEmpty()) {
            showToast("❌ Preencha todos os campos obrigatórios")
            return
        }

        val userId = getCurrentUserId()
        if (userId.isNullOrBlank()) {
            showToast("❌ Usuário não autenticado")
            return
        }

        lifecycleScope.launch {
            try {
                val updates = mapOf(
                    "fullName" to fullName,
                    "phone" to phone,
                    "cpf" to cpf,
                    "updatedAt" to Timestamp.now()
                )

                firestore.collection("providers")
                    .document(userId)
                    .set(updates, SetOptions.merge())
                    .await()

                val localUser = authManager.getLocalUserData()
                if (localUser != null) {
                    val updatedLocalUser = localUser.copy(
                        fullName = fullName,
                        phone = phone
                    )
                    val userUpdateResult = authManager.updateUserProfile(updatedLocalUser)
                    if (userUpdateResult.isFailure) {
                        throw (userUpdateResult.exceptionOrNull() ?: IllegalStateException("Falha ao salvar dados do usuário"))
                    }
                } else {
                    firestore.collection("users")
                        .document(userId)
                        .set(
                            mapOf(
                                "fullName" to fullName,
                                "phone" to phone,
                                "updatedAt" to Timestamp.now()
                            ),
                            SetOptions.merge()
                        )
                        .await()
                }

                showToast("✅ Informações pessoais salvas com sucesso!")
                loadProviderData()
            } catch (e: Exception) {
                showToast("❌ Erro ao salvar informações: ${e.message}")
            }
        }
    }

    /**
     * Verifica se há solicitação de especialidades pendente e bloqueia o botão se houver.
     */
    private fun loadPendingSpecialtyRequest() {
        val userId = getCurrentUserId() ?: return
        lifecycleScope.launch {
            try {
                val pending = firestore.collection("provider_specialty_requests")
                    .whereEqualTo("providerId", userId)
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()
                if (!pending.isEmpty) {
                    binding.btnSaveServices.isEnabled = false
                    showToast("⏳ Há uma solicitação de especialidades aguardando aprovação do admin")
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Envia solicitação de alteração de especialidades para aprovação do admin.
     * Não grava diretamente em providers/{uid}.services — a aprovação é feita pelo painel.
     */
    private fun saveServices() {
        val selectedServices = mutableListOf<String>()
        val chipGroup = binding.chipGroupServices

        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i)
            if (chip is com.google.android.material.chip.Chip && chip.isChecked) {
                selectedServices.add(chip.text.toString())
            }
        }

        val canonicalServices = ServiceNicheCatalog.canonicalizeProviderServices(selectedServices)
        if (canonicalServices.isEmpty()) {
            showToast("❌ Selecione pelo menos um serviço")
            return
        }

        val userId = authManager.getLocalUserData()?.uid
            ?: authManager.getCurrentUser()?.uid
            ?: firebaseAuth.currentUser?.uid
            ?: run {
                showToast("❌ Usuário não autenticado")
                return
            }

        lifecycleScope.launch {
            try {
                // Bloqueia se já há solicitação pendente
                val existingPending = firestore.collection("provider_specialty_requests")
                    .whereEqualTo("providerId", userId)
                    .whereEqualTo("status", "pending")
                    .get()
                    .await()

                if (!existingPending.isEmpty) {
                    showToast("⏳ Você já tem uma solicitação aguardando aprovação")
                    binding.btnSaveServices.isEnabled = false
                    return@launch
                }

                // Busca especialidades aprovadas atualmente
                val providerDoc = firestore.collection("providers").document(userId).get().await()
                val currentServices = (providerDoc.get("services") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()

                if (canonicalServices.toSet() == currentServices.toSet()) {
                    showToast("ℹ️ Nenhuma alteração detectada")
                    return@launch
                }

                val providerName = providerDoc.getString("fullName")
                    ?: authManager.getLocalUserData()?.fullName
                    ?: "Prestador"

                // Sobe os documentos comprobatórios (se houver) para o Storage
                binding.btnSaveServices.isEnabled = false
                val documentUrls = if (pendingServiceDocs.isNotEmpty()) {
                    showToast("⏫ Enviando documentos...")
                    uploadServiceDocuments(userId)
                } else emptyList()

                // Cria solicitação pendente — admin aprova/rejeita no painel
                firestore.collection("provider_specialty_requests")
                    .add(
                        mapOf(
                            "providerId" to userId,
                            "providerName" to providerName,
                            "requestedServices" to canonicalServices,
                            "currentServices" to currentServices,
                            "documentUrls" to documentUrls,
                            "status" to "pending",
                            "createdAt" to Timestamp.now()
                        )
                    )
                    .await()

                pendingServiceDocs.clear()
                renderServiceDocs()
                binding.btnSaveServices.isEnabled = false
                showToast("✅ Solicitação enviada! Aguardando aprovação do administrador.")
            } catch (e: Exception) {
                binding.btnSaveServices.isEnabled = true
                showToast("❌ Erro ao enviar solicitação: ${e.message}")
            }
        }
    }

    /**
     * Faz upload dos documentos comprobatórios para o Storage e devolve as URLs.
     * Caminho: provider_documents/{uid}/specialty_<timestamp>_<n>.<ext>
     * (coberto pela regra ownerCanModifyImageOrPdf — imagem/PDF até 10 MB).
     */
    private suspend fun uploadServiceDocuments(userId: String): List<String> {
        val storage = FirebaseStorage.getInstance().reference
        val resolver = requireContext().contentResolver
        val urls = mutableListOf<String>()
        pendingServiceDocs.forEachIndexed { index, uri ->
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val ext = when {
                mime.contains("pdf") -> "pdf"
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                else -> "jpg"
            }
            val ref = storage.child(
                "provider_documents/$userId/specialty_${System.currentTimeMillis()}_$index.$ext"
            )
            ref.putFile(uri).await()
            urls.add(ref.downloadUrl.await().toString())
        }
        return urls
    }

    /**
     * Salva os dados bancários
     */
    private fun saveBankData() {
        val bankName = binding.etBankName.text.toString().trim()
        val agency = binding.etAgency.text.toString().trim()
        val account = binding.etAccount.text.toString().trim()
        
        if (bankName.isEmpty() || agency.isEmpty() || account.isEmpty()) {
            showToast("❌ Preencha todos os campos bancários")
            return
        }

        val userId = getCurrentUserId()
        if (userId.isNullOrBlank()) {
            showToast("❌ Usuário não autenticado")
            return
        }

        lifecycleScope.launch {
            try {
                val bankMap = mapOf(
                    "bankName" to bankName,
                    "agency" to agency,
                    "account" to account
                )

                firestore.collection("providers")
                    .document(userId)
                    .set(
                        mapOf(
                            "bank" to bankMap,
                            "updatedAt" to Timestamp.now()
                        ),
                        SetOptions.merge()
                    )
                    .await()

                showToast("✅ Dados bancários salvos com sucesso!")
                loadProviderData()
            } catch (e: Exception) {
                showToast("❌ Erro ao salvar dados bancários: ${e.message}")
            }
        }
    }

    private fun getCurrentUserId(): String? {
        return authManager.getLocalUserData()?.uid
            ?: authManager.getCurrentUser()?.uid
            ?: firebaseAuth.currentUser?.uid
    }

    /**
     * Atualiza a URL da imagem no Firestore (users e providers para prestadores)
     */
    private fun updateProfileImageInFirestore(userId: String, imageUrl: String) {
        lifecycleScope.launch {
            try {
                // Foto da CONTA DE PRESTADOR — grava SÓ em providers/{uid}.profileImageUrl.
                // Não toca em users/{uid} (foto de cliente), para que a conta de prestador
                // possa ter uma foto diferente da conta de cliente do mesmo usuário.
                val providerManager = FirebaseProviderManager()
                val result = providerManager.updateProfileImage(userId, imageUrl)
                if (result !is FirebaseProviderManager.ProviderResult.Success) {
                    showToast("❌ Erro ao salvar foto do prestador")
                    return@launch
                }

                showToast("✅ Foto do perfil de prestador atualizada com sucesso!")
                loadProviderData()
            } catch (e: Exception) {
                showToast("❌ Erro ao salvar foto: ${e.message}")
            }
        }
    }

    /**
     * Exibe uma mensagem toast para o usuário
     */
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MAX_SERVICE_DOCS = 5
    }
}
