package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.databinding.ActivityChecklistBinding
import com.aquiresolve.app.models.OsChecklistData
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

class ChecklistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChecklistBinding
    private lateinit var checklistManager: FirebaseChecklistManager
    private var orderId: String? = null
    private var isProviderView = false
    private var existingChecklist: OsChecklistData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChecklistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        orderId = intent.getStringExtra("order_id")
        isProviderView = intent.getBooleanExtra("is_provider_view", false)

        if (orderId == null) {
            Toast.makeText(this, "Pedido não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.secondary_color)
        checklistManager = FirebaseChecklistManager()

        setupClickListeners()
        setupPendingSummaryWatchers()
        loadOrderContext()
        loadExistingChecklist()
        updatePendingSummary()
    }

    /**
     * Carrega o pedido para exibir um cartão de contexto (serviço, cliente, endereço)
     * como referência para o prestador preencher a OS. Falha silenciosa.
     */
    private fun loadOrderContext() {
        val id = orderId ?: return
        lifecycleScope.launch {
            try {
                val order = FirebaseOrderManager().getOrderById(id).getOrNull() ?: return@launch

                val service = buildString {
                    if (order.serviceName.isNotBlank()) append(order.serviceName)
                    if (order.serviceType.isNotBlank() && order.serviceType != order.serviceName) {
                        if (isNotEmpty()) append(" · ")
                        append(order.serviceType)
                    }
                }.ifBlank { "Serviço" }
                binding.tvContextService.text = service

                if (order.clientName.isNotBlank()) {
                    binding.tvContextClient.text = "👤 ${order.clientName}"
                    binding.tvContextClient.visibility = android.view.View.VISIBLE
                }

                val address = buildString {
                    if (order.address.isNotBlank()) append(order.address)
                    val cityState = listOf(order.city, order.state).filter { it.isNotBlank() }.joinToString(" - ")
                    if (cityState.isNotBlank()) {
                        if (isNotEmpty()) append(", ")
                        append(cityState)
                    }
                }
                if (address.isNotBlank()) {
                    binding.tvContextAddress.text = "📍 $address"
                    binding.tvContextAddress.visibility = android.view.View.VISIBLE
                }

                binding.cardOrderContext.visibility = android.view.View.VISIBLE
            } catch (_: Exception) {
                // contexto é opcional — ignora falhas
            }
        }
    }

    /**
     * Liga ouvintes em todos os campos obrigatórios para atualizar, em tempo real,
     * o resumo do que ainda falta preencher antes de avançar.
     */
    private fun setupPendingSummaryWatchers() {
        val serviceBoxes = listOf(
            binding.cbServiceElectric, binding.cbServicePlumber, binding.cbServiceClog,
            binding.cbServiceCheckup, binding.cbServiceWaterTankCleaning,
            binding.cbServiceGutterCleaning, binding.cbServiceGreaseTrapCleaning,
            binding.cbServiceLocksmith, binding.cbServiceFanInstallation
        )
        serviceBoxes.forEach { it.setOnCheckedChangeListener { _, _ -> updatePendingSummary() } }
        binding.cbDeclarationAccepted.setOnCheckedChangeListener { _, _ -> updatePendingSummary() }
        binding.rgProblemResolution.setOnCheckedChangeListener { _, _ -> updatePendingSummary() }
        binding.cbMaterialsUsed.setOnCheckedChangeListener { _, checked ->
            binding.tilMaterialsDescription.visibility = if (checked) android.view.View.VISIBLE else android.view.View.GONE
            if (!checked) {
                binding.etMaterialsDescription.text?.clear()
                binding.tilMaterialsDescription.error = null
            }
            updatePendingSummary()
        }
        binding.etMaterialsDescription.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updatePendingSummary() }
        })
        binding.etExecutionDescription.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updatePendingSummary() }
        })
    }

    /**
     * Recalcula e exibe os itens obrigatórios ainda pendentes (mesmas regras de
     * validateStep1). Quando tudo está ok, mostra confirmação verde.
     */
    private fun updatePendingSummary() {
        val pending = mutableListOf<String>()
        if (collectServiceDescriptions().isEmpty()) pending.add("• Descrição do serviço (marque ao menos uma)")
        if (binding.etExecutionDescription.text?.toString()?.trim().isNullOrEmpty()) {
            pending.add("• Descrição detalhada do serviço realizado")
        }
        if (binding.rgProblemResolution.checkedRadioButtonId == -1) pending.add("• Indicar se o problema foi solucionado")
        if (binding.cbMaterialsUsed.isChecked && binding.etMaterialsDescription.text?.toString()?.trim().isNullOrEmpty()) {
            pending.add("• Descrever os materiais/suprimentos usados")
        }
        if (!binding.cbDeclarationAccepted.isChecked) pending.add("• Aceitar a declaração de veracidade")

        if (pending.isEmpty()) {
            binding.cardPendingSummary.visibility = android.view.View.VISIBLE
            binding.cardPendingSummary.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.surface_color)
            )
            binding.cardPendingSummary.setStrokeColor(ContextCompat.getColor(this, R.color.secondary_color))
            binding.tvPendingSummary.text = "✅ Tudo preenchido! Você já pode avançar para as fotos."
        } else {
            binding.cardPendingSummary.visibility = android.view.View.VISIBLE
            binding.cardPendingSummary.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.orange_50)
            )
            binding.cardPendingSummary.strokeColor = ContextCompat.getColor(this, R.color.primary_color)
            binding.tvPendingSummary.text = "Antes de avançar, falta:\n" + pending.joinToString("\n")
        }
    }

    private fun loadExistingChecklist() {
        lifecycleScope.launch {
            val result = checklistManager.getChecklist(orderId!!)
            if (result.isSuccess) {
                existingChecklist = result.getOrNull()
                existingChecklist?.let { populateChecklist(it) }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.btnNext.setOnClickListener {
            if (validateStep1()) {
                saveChecklist()
            }
        }

        binding.btnSaveDraft.setOnClickListener {
            saveDraft()
        }
    }

    private fun validateStep1(): Boolean {
        if (collectServiceDescriptions().isEmpty()) {
            Toast.makeText(this, "Selecione pelo menos uma descrição do serviço", Toast.LENGTH_LONG).show()
            return false
        }

        if (binding.etExecutionDescription.text?.toString()?.trim().isNullOrEmpty()) {
            Toast.makeText(this, "Preencha a descrição detalhada do serviço realizado", Toast.LENGTH_LONG).show()
            binding.etExecutionDescription.error = "Campo obrigatório"
            return false
        }

        if (binding.rgProblemResolution.checkedRadioButtonId == -1) {
            Toast.makeText(this, "Selecione se o problema foi solucionado", Toast.LENGTH_LONG).show()
            return false
        }

        if (binding.cbMaterialsUsed.isChecked &&
            binding.etMaterialsDescription.text?.toString()?.trim().isNullOrEmpty()
        ) {
            binding.tilMaterialsDescription.error = "Informe os materiais usados"
            Toast.makeText(this, "Descreva os materiais/suprimentos usados", Toast.LENGTH_LONG).show()
            return false
        }
        binding.tilMaterialsDescription.error = null

        if (!binding.cbDeclarationAccepted.isChecked) {
            Toast.makeText(this, "Confirme a declaração de veracidade das informações", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    private fun collectAnswers(): Map<String, Boolean?> {
        return mapOf(
            "clientPresent" to binding.cbClientPresent.isChecked,
            "serviceMatches" to binding.cbServiceMatches.isChecked,
            "visibleDamage" to binding.cbVisibleDamage.isChecked,
            "materialAvailable" to binding.cbMaterialAvailable.isChecked,
            "materialsUsed" to binding.cbMaterialsUsed.isChecked,
            "clientObservations" to binding.cbClientObservations.isChecked,
            "executedAsRequested" to binding.cbExecutedAsRequested.isChecked,
            "additionalService" to binding.cbAdditionalService.isChecked,
            "partsReplaced" to binding.cbPartsReplaced.isChecked,
            "valueChanged" to binding.cbValueChanged.isChecked,
            "serviceCompleted" to binding.cbServiceCompleted.isChecked,
            "cleanAfterService" to binding.cbCleanAfterService.isChecked,
            "declarationAccepted" to binding.cbDeclarationAccepted.isChecked
        )
    }

    private fun collectServiceDescriptions(): List<String> {
        val descriptions = mutableListOf<String>()
        if (binding.cbServiceElectric.isChecked) descriptions.add("Elétrico")
        if (binding.cbServicePlumber.isChecked) descriptions.add("Encanador")
        if (binding.cbServiceClog.isChecked) descriptions.add("Desentupimento")
        if (binding.cbServiceCheckup.isChecked) descriptions.add("Check-Up")
        if (binding.cbServiceWaterTankCleaning.isChecked) descriptions.add("Limpeza de Caixa d'água")
        if (binding.cbServiceGutterCleaning.isChecked) descriptions.add("Limpeza de Calhas e Rufos")
        if (binding.cbServiceGreaseTrapCleaning.isChecked) descriptions.add("Limpeza de Caixa de Gordura")
        if (binding.cbServiceLocksmith.isChecked) descriptions.add("Chaveiro")
        if (binding.cbServiceFanInstallation.isChecked) descriptions.add("Instalação de ventilador")
        return descriptions
    }

    private fun getProblemResolution(): String {
        return when (binding.rgProblemResolution.checkedRadioButtonId) {
            R.id.rbResolved -> "resolved"
            R.id.rbReturnNeeded -> "return_needed"
            R.id.rbNotResolved -> "not_resolved"
            else -> ""
        }
    }

    private fun saveChecklist() {
        lifecycleScope.launch {
            try {
                val answers = collectAnswers()
                val description = binding.etExecutionDescription.text?.toString()?.trim() ?: ""
                val serviceDescriptions = collectServiceDescriptions()
                val preExistingDamages = binding.etPreExistingDamages.text?.toString()?.trim() ?: ""
                val observations = binding.etObservations.text?.toString()?.trim() ?: ""
                val problemResolution = getProblemResolution()
                val materialsUsed = binding.cbMaterialsUsed.isChecked
                val materialsDescription = binding.etMaterialsDescription.text?.toString()?.trim().orEmpty()

                val result = checklistManager.saveChecklistAnswers(
                    orderId!!,
                    answers,
                    description,
                    serviceDescriptions,
                    preExistingDamages,
                    observations,
                    problemResolution,
                    materialsUsed,
                    materialsDescription
                )

                if (result.isSuccess) {
                    val intent = Intent(this@ChecklistActivity, PhotoEvidenceActivity::class.java).apply {
                        putExtra("order_id", orderId)
                        putExtra("is_provider_view", isProviderView)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@ChecklistActivity, "Erro ao salvar checklist", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChecklistActivity, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveDraft() {
        lifecycleScope.launch {
            try {
                val answers = collectAnswers()
                val description = binding.etExecutionDescription.text?.toString()?.trim() ?: ""
                val serviceDescriptions = collectServiceDescriptions()
                val preExistingDamages = binding.etPreExistingDamages.text?.toString()?.trim() ?: ""
                val observations = binding.etObservations.text?.toString()?.trim() ?: ""
                val problemResolution = getProblemResolution()

                val currentChecklist = existingChecklist
                val checklist = if (currentChecklist != null) {
                    currentChecklist.copy(
                        clientPresent = answers["clientPresent"],
                        serviceMatches = answers["serviceMatches"],
                        visibleDamage = answers["visibleDamage"],
                        materialAvailable = answers["materialAvailable"],
                        materialsUsed = answers["materialsUsed"],
                        materialsDescription = if (answers["materialsUsed"] == true) {
                            binding.etMaterialsDescription.text?.toString()?.trim().orEmpty()
                        } else {
                            ""
                        },
                        clientObservations = answers["clientObservations"],
                        executedAsRequested = answers["executedAsRequested"],
                        additionalService = answers["additionalService"],
                        partsReplaced = answers["partsReplaced"],
                        valueChanged = answers["valueChanged"],
                        serviceCompleted = answers["serviceCompleted"],
                        cleanAfterService = answers["cleanAfterService"],
                        serviceDescription = serviceDescriptions,
                        preExistingDamages = preExistingDamages,
                        problemResolution = problemResolution,
                        declarationAccepted = answers["declarationAccepted"],
                        executionDescription = description,
                        observations = observations,
                        updatedAt = Timestamp.now()
                    )
                } else {
                    OsChecklistData(
                        orderId = orderId!!,
                        status = OsChecklistData.STATUS_CHECKLIST_PENDING,
                        clientPresent = answers["clientPresent"],
                        serviceMatches = answers["serviceMatches"],
                        visibleDamage = answers["visibleDamage"],
                        materialAvailable = answers["materialAvailable"],
                        materialsUsed = answers["materialsUsed"],
                        materialsDescription = if (answers["materialsUsed"] == true) {
                            binding.etMaterialsDescription.text?.toString()?.trim().orEmpty()
                        } else {
                            ""
                        },
                        clientObservations = answers["clientObservations"],
                        executedAsRequested = answers["executedAsRequested"],
                        additionalService = answers["additionalService"],
                        partsReplaced = answers["partsReplaced"],
                        valueChanged = answers["valueChanged"],
                        serviceCompleted = answers["serviceCompleted"],
                        cleanAfterService = answers["cleanAfterService"],
                        serviceDescription = serviceDescriptions,
                        preExistingDamages = preExistingDamages,
                        problemResolution = problemResolution,
                        declarationAccepted = answers["declarationAccepted"],
                        executionDescription = description,
                        observations = observations,
                        createdAt = Timestamp.now(),
                        updatedAt = Timestamp.now()
                    )
                }

                checklistManager.createOrUpdateChecklist(checklist)
                Toast.makeText(this@ChecklistActivity, "Rascunho salvo!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ChecklistActivity, "Erro ao salvar rascunho", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populateChecklist(checklist: OsChecklistData) {
        binding.cbClientPresent.isChecked = checklist.clientPresent == true
        binding.cbServiceMatches.isChecked = checklist.serviceMatches == true
        binding.cbVisibleDamage.isChecked = checklist.visibleDamage == true
        binding.cbMaterialAvailable.isChecked = checklist.materialAvailable == true
        binding.cbMaterialsUsed.isChecked = checklist.materialsUsed == true
        binding.etMaterialsDescription.setText(checklist.materialsDescription)
        binding.tilMaterialsDescription.visibility = if (checklist.materialsUsed == true) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        binding.cbClientObservations.isChecked = checklist.clientObservations == true
        binding.cbExecutedAsRequested.isChecked = checklist.executedAsRequested == true
        binding.cbAdditionalService.isChecked = checklist.additionalService == true
        binding.cbPartsReplaced.isChecked = checklist.partsReplaced == true
        binding.cbValueChanged.isChecked = checklist.valueChanged == true
        binding.cbServiceCompleted.isChecked = checklist.serviceCompleted == true
        binding.cbCleanAfterService.isChecked = checklist.cleanAfterService == true
        binding.cbDeclarationAccepted.isChecked = checklist.declarationAccepted == true

        val descriptions = checklist.serviceDescription.toSet()
        binding.cbServiceElectric.isChecked = "Elétrico" in descriptions
        binding.cbServicePlumber.isChecked = "Encanador" in descriptions
        binding.cbServiceClog.isChecked = "Desentupimento" in descriptions
        binding.cbServiceCheckup.isChecked = "Check-Up" in descriptions
        binding.cbServiceWaterTankCleaning.isChecked = "Limpeza de Caixa d'água" in descriptions
        binding.cbServiceGutterCleaning.isChecked = "Limpeza de Calhas e Rufos" in descriptions
        binding.cbServiceGreaseTrapCleaning.isChecked = "Limpeza de Caixa de Gordura" in descriptions
        binding.cbServiceLocksmith.isChecked = "Chaveiro" in descriptions
        binding.cbServiceFanInstallation.isChecked = "Instalação de ventilador" in descriptions

        binding.etPreExistingDamages.setText(checklist.preExistingDamages)
        binding.etExecutionDescription.setText(checklist.executionDescription)
        binding.etObservations.setText(checklist.observations)

        when (checklist.problemResolution) {
            "resolved" -> binding.rgProblemResolution.check(R.id.rbResolved)
            "return_needed" -> binding.rgProblemResolution.check(R.id.rbReturnNeeded)
            "not_resolved" -> binding.rgProblemResolution.check(R.id.rbNotResolved)
        }
    }
}
