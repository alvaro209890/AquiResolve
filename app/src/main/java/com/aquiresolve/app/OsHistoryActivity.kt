package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.aquiresolve.app.adapters.ImageAdapter
import com.aquiresolve.app.databinding.ActivityOsHistoryBinding
import com.aquiresolve.app.models.OsChecklistData
import com.aquiresolve.app.models.OrderData
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class OsHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOsHistoryBinding
    private lateinit var checklistManager: FirebaseChecklistManager
    private lateinit var orderManager: FirebaseOrderManager

    private var orderId: String? = null
    private var loadedOrder: OrderData? = null
    private var loadedChecklist: OsChecklistData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOsHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderId = intent.getStringExtra("order_id")

        if (orderId == null) {
            Toast.makeText(this, "Pedido não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)
        checklistManager = FirebaseChecklistManager()
        orderManager = FirebaseOrderManager()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnExportPdf.setOnClickListener { exportToPdf() }

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                binding.loadingState.visibility = View.VISIBLE
                binding.scrollContent.visibility = View.GONE

                val orderResult = orderManager.getOrderById(orderId!!)
                val checklistResult = checklistManager.getChecklist(orderId!!)

                val order = orderResult.getOrNull()
                val checklist = checklistResult.getOrNull()
                loadedOrder = order
                loadedChecklist = checklist

                if (order != null) {
                    updateOrderInfo(order)
                }

                if (checklist != null) {
                    updateChecklistInfo(checklist)
                    updatePhotos(checklist)
                    updateSignatures(checklist)
                    updateGpsInfo(checklist)
                }

                binding.loadingState.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE

            } catch (e: Exception) {
                binding.loadingState.visibility = View.GONE
                Toast.makeText(this@OsHistoryActivity, "Erro ao carregar histórico", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateOrderInfo(order: OrderData) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

        binding.tvOsProtocol.text = "Protocolo: ${order.protocol}"
        binding.tvOsClient.text = "Cliente: ${order.clientName}"
        binding.tvOsProvider.text = "Prestador: ${order.assignedProviderName ?: "Não atribuído"}"
        binding.tvOsService.text = "Serviço: ${order.serviceName}"
        binding.tvOsAddress.text = "Endereço: ${order.address}"
        binding.tvOsDate.text = "Data: ${dateFormat.format(order.createdAt.toDate())}"
    }

    private fun updateChecklistInfo(checklist: OsChecklistData) {
        binding.tvClientPresent.text = checkboxText("Cliente presente", checklist.clientPresent)
        binding.tvServiceMatches.text = checkboxText("Serviço corresponde", checklist.serviceMatches)
        binding.tvVisibleDamage.text = checkboxText("Avarias visíveis", checklist.visibleDamage)
        binding.tvMaterialAvailable.text = checkboxText("Material disponível", checklist.materialAvailable)
        binding.tvClientObservations.text = checkboxText("Observações do cliente", checklist.clientObservations)
        binding.tvExecutedAsRequested.text = checkboxText("Executado conforme solicitado", checklist.executedAsRequested)
        binding.tvAdditionalService.text = checkboxText("Serviço adicional", checklist.additionalService)
        binding.tvPartsReplaced.text = checkboxText("Troca de peças", checklist.partsReplaced)
        binding.tvValueChanged.text = checkboxText("Alteração no valor", checklist.valueChanged)
        binding.tvServiceCompleted.text = checkboxText("Concluído com sucesso", checklist.serviceCompleted)
        binding.tvExecutionDescription.text = "Descrição: ${checklist.executionDescription}"
    }

    private fun checkboxText(label: String, value: Boolean?): String {
        return "$label: ${when (value) {
            true -> "✅ Sim"
            false -> "❌ Não"
            null -> "—"
        }}"
    }

    private fun updatePhotos(checklist: OsChecklistData) {
        setupPhotoRecycler(binding.rvPhotosBefore, checklist.photosBefore, binding.cardPhotosBefore)
        setupPhotoRecycler(binding.rvPhotosDuring, checklist.photosDuring, binding.cardPhotosDuring)
        setupPhotoRecycler(binding.rvPhotosAfter, checklist.photosAfter, binding.cardPhotosAfter)
    }

    private fun setupPhotoRecycler(recyclerView: androidx.recyclerview.widget.RecyclerView, photos: List<String>, card: View) {
        if (photos.isEmpty()) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        val adapter = ImageAdapter(
            context = this,
            imageUrls = photos,
            onImageClick = { url, _ ->
                val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                    putStringArrayListExtra("image_uris", ArrayList(photos))
                    putStringArrayListExtra("file_names", ArrayList(List(photos.size) { "foto_${it + 1}.jpg" }))
                    putExtra("file_sizes", LongArray(photos.size) { 0L })
                    putStringArrayListExtra("image_types", ArrayList(List(photos.size) { "ORDER" }))
                    putExtra("current_position", photos.indexOf(url))
                }
                startActivity(intent)
            }
        )
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter
    }

    private fun updateSignatures(checklist: OsChecklistData) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

        if (checklist.providerSignatureName != null) {
            binding.tvProviderSignature.text = "Prestador: ${checklist.providerSignatureName}"
            val signedAt = checklist.providerSignedAt?.toDate()
            binding.tvProviderSignedAt.text = if (signedAt != null) "Data/Hora: ${dateFormat.format(signedAt)}" else "Data/Hora: —"
        } else {
            binding.tvProviderSignature.text = "Prestador: —"
            binding.tvProviderSignedAt.text = "Data/Hora: —"
        }

        if (checklist.clientSignatureName != null) {
            binding.tvClientSignature.text = "Cliente: ${checklist.clientSignatureName}"
            binding.tvClientDocument.text = "Documento: ${checklist.clientSignatureDocument ?: "—"}"
            val signedAt = checklist.clientSignedAt?.toDate()
            binding.tvClientSignedAt.text = if (signedAt != null) "Data/Hora: ${dateFormat.format(signedAt)}" else "Data/Hora: —"
        } else {
            binding.tvClientSignature.text = "Cliente: —"
            binding.tvClientDocument.text = "Documento: —"
            binding.tvClientSignedAt.text = "Data/Hora: —"
        }
    }

    private fun updateGpsInfo(checklist: OsChecklistData) {
        if (checklist.startLatitude != null && checklist.startLongitude != null) {
            binding.tvGpsCoordinates.text = "Coordenadas: ${String.format("%.6f", checklist.startLatitude)}, ${String.format("%.6f", checklist.startLongitude)}"
        } else {
            binding.tvGpsCoordinates.text = "Coordenadas: —"
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

        val startedAt = checklist.startedAt?.toDate()
        binding.tvOsStartedAt.text = if (startedAt != null) "Iniciado em: ${dateFormat.format(startedAt)}" else "Iniciado em: —"

        val completedAt = checklist.completedAt?.toDate()
        binding.tvOsCompletedAt.text = if (completedAt != null) "Concluído em: ${dateFormat.format(completedAt)}" else "Concluído em: —"
    }

    private fun exportToPdf() {
        val order = loadedOrder
        val checklist = loadedChecklist
        if (order == null || checklist == null) {
            Toast.makeText(this, "Aguarde o carregamento da OS", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnExportPdf.isEnabled = false
        Toast.makeText(this, "Gerando PDF da OS...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val file = OsPdfGenerator.generate(this@OsHistoryActivity, order, checklist)
                sharePdf(file, order.protocol.ifBlank { order.id })
            } catch (e: Exception) {
                Toast.makeText(this@OsHistoryActivity, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnExportPdf.isEnabled = true
            }
        }
    }

    private fun sharePdf(file: File, protocol: String) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ordem de Serviço $protocol — AquiResolve")
            putExtra(
                Intent.EXTRA_TEXT,
                "Segue a Ordem de Serviço $protocol referente ao atendimento AquiResolve."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartilhar OS via..."))
    }
}
