package com.aquiresolve.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aquiresolve.app.databinding.ActivityAssistantBinding
import kotlinx.coroutines.launch

/**
 * Assistente AquiResolve (plano 06). O cliente descreve o problema em linguagem natural e a IA
 * (via proxy no backend — [AssistantClient]) identifica o nicho do catálogo e direciona para o
 * fluxo de pedido. A IA é conveniência: qualquer falha cai no fallback "Ver todos os serviços",
 * nunca bloqueando a contratação.
 */
class AssistantActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssistantBinding
    private var lastDescription = ""
    private var voiceManager: VoiceInputManager? = null

    // Permissão do microfone: se concedida, já começa a ouvir; senão, avisa de forma amigável.
    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoice()
            else toast("Permita o microfone para falar com o assistente.")
        }

    companion object {
        /** Extra opcional: pré-preenche a descrição (ex.: vindo da busca sem resultado). */
        const val EXTRA_PREFILL = "prefill_description"
        // Abaixo deste valor a IA "não tem certeza" → ainda sugerimos, mas sem insistir.
        private const val CONFIDENCE_HINT = 0.35
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!FirebaseConfig.isInitialized()) {
            FirebaseConfig.initialize(this)
        }
        binding = ActivityAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)
        logEvent("ia_assistente_open", null)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAsk.setOnClickListener { ask() }
        binding.btnSeeAll.setOnClickListener { openServices() }
        binding.btnVoice.setOnClickListener { onMicClick() }
        setupVoice()

        // Garante o catálogo de nichos em cache (a IA classifica DENTRO dessa lista).
        lifecycleScope.launch {
            try {
                CatalogRepository.load()
            } catch (_: Exception) {
            }
        }

        val prefill = intent.getStringExtra(EXTRA_PREFILL)?.trim().orEmpty()
        if (prefill.isNotEmpty()) {
            binding.etDescription.setText(prefill)
            binding.etDescription.setSelection(prefill.length)
            ask()
        }
    }

    private fun ask() {
        val description = binding.etDescription.text.toString().trim()
        if (description.length < 3) {
            binding.etDescription.error = "Descreva um pouco mais o que aconteceu"
            return
        }
        lastDescription = description
        hideKeyboard()
        showLoading(true)
        binding.resultCard.visibility = View.GONE

        lifecycleScope.launch {
            val niches = CatalogRepository.cachedNicheNames().ifEmpty {
                try {
                    CatalogRepository.load()
                } catch (_: Exception) {
                }
                CatalogRepository.cachedNicheNames()
            }

            if (niches.isEmpty()) {
                showLoading(false)
                showResult(message = "Não consegui carregar os serviços agora. Veja todos os serviços disponíveis.", niche = null, serviceType = null)
                return@launch
            }

            val result = AssistantClient.classify(description, niches)
            showLoading(false)
            when (result) {
                is AssistantClient.Result.Ok -> {
                    val s = result.suggestion
                    logEvent("ia_nicho_sugerido", Bundle().apply {
                        putString("niche", s.niche ?: "null")
                        putDouble("confidence", s.confidence)
                    })
                    showResult(message = s.message, niche = s.niche, serviceType = s.serviceType, confidence = s.confidence)
                }
                is AssistantClient.Result.Error -> {
                    showResult(message = result.message, niche = null, serviceType = null)
                }
            }
        }
    }

    private fun showResult(message: String, niche: String?, serviceType: String?, confidence: Double = 0.0) {
        binding.resultCard.visibility = View.VISIBLE
        binding.tvResultMessage.text = message

        if (niche != null) {
            binding.tvNicheChip.visibility = View.VISIBLE
            binding.tvNicheChip.text = niche
            binding.btnContinue.visibility = View.VISIBLE
            val cta = if (confidence in 0.0..CONFIDENCE_HINT) "Acho que é isso — continuar" else "Sim, continuar"
            binding.btnContinue.text = cta
            binding.btnContinue.setOnClickListener {
                logEvent("ia_sugestao_aceita", Bundle().apply { putString("niche", niche) })
                val intent = Intent(this, CreateOrderActivity::class.java)
                    .putExtra("service_category_name", niche)
                // Palpite da IA do serviço específico → o CreateOrderActivity casa contra o catálogo
                // real e pré-seleciona (se não casar, é ignorado sem prejuízo).
                if (!serviceType.isNullOrBlank()) intent.putExtra("preselect_service", serviceType)
                startActivity(intent)
            }
        } else {
            binding.tvNicheChip.visibility = View.GONE
            binding.btnContinue.visibility = View.GONE
        }
    }

    // ---- Entrada por voz (fala → texto) -------------------------------------------------

    private fun setupVoice() {
        voiceManager = VoiceInputManager(
            context = this,
            onReadyForSpeech = { setVoiceListening(true) },
            onPartial = { text ->
                binding.etDescription.setText(text)
                binding.etDescription.setSelection(text.length)
            },
            onResult = { text ->
                binding.etDescription.setText(text)
                binding.etDescription.setSelection(text.length)
                logEvent("ia_voz_reconhecida", Bundle().apply { putInt("len", text.length) })
                ask()
            },
            onError = { msg -> if (msg.isNotBlank()) toast(msg) },
            onEnd = { setVoiceListening(false) }
        )
        // Sem reconhecedor no aparelho → esconde o botão (o cliente ainda digita normalmente).
        if (voiceManager?.isAvailable() != true) {
            binding.btnVoice.visibility = View.GONE
        }
    }

    private fun onMicClick() {
        val vm = voiceManager ?: return
        if (vm.isListening) {
            vm.stop()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startVoice()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoice() {
        hideKeyboard()
        binding.etDescription.setText("")
        logEvent("ia_voz_iniciada", null)
        voiceManager?.start()
    }

    private fun setVoiceListening(listening: Boolean) {
        binding.tvVoiceStatus.visibility = if (listening) View.VISIBLE else View.GONE
        binding.btnVoice.text = if (listening) "Ouvindo… toque para parar" else "Falar com o assistente"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        voiceManager?.destroy()
        voiceManager = null
        super.onDestroy()
    }

    private fun openServices() {
        val intent = Intent(this, ServicesActivity::class.java)
        if (lastDescription.isNotBlank()) intent.putExtra("search_query", lastDescription)
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        binding.loadingLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnAsk.isEnabled = !show
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etDescription.windowToken, 0)
        } catch (_: Exception) {
        }
    }

    private fun logEvent(name: String, params: Bundle?) {
        try {
            FirebaseConfig.getAnalytics()?.logEvent(name, params)
        } catch (_: Exception) {
        }
    }
}
