package com.aquiresolve.app

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Botão flutuante de microfone — visível em todas as telas do cliente (exceto no
 * chat da Helô). Posicionado no canto inferior-esquerdo, com animação de pulse,
 * texto "Pergunte a Helô" e long-press para gravar áudio.
 *
 * Uso na Activity:
 * ```
 * private val floatingMic = FloatingMicHelper()
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     setContentView(...)
 *     floatingMic.attach(this)
 * }
 * override fun onDestroy() {
 *     floatingMic.detach()
 *     super.onDestroy()
 * }
 * ```
 */
class FloatingMicHelper {

    private var fab: FloatingActionButton? = null
    private var textLabel: TextView? = null
    private var container: LinearLayout? = null
    private var pulseAnimator: ValueAnimator? = null
    private var micPermissionLauncher: ActivityResultLauncher<String>? = null
    private var voiceManager: VoiceInputManager? = null
    private var attached = false

    fun attach(activity: AppCompatActivity) {
        if (attached) return

        // Não mostra o FAB no próprio chat da Helô
        if (activity is AssistantChatActivity) return

        micPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                // Depois de conceder permissão pelo long-press, inicia gravação
                startVoiceRecording(activity)
            } else {
                Toast.makeText(activity, "Permita o microfone para falar com o Helô.", Toast.LENGTH_SHORT).show()
            }
        }

        // Container horizontal: FAB + texto
        container = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            id = View.generateViewId()
        }

        // FAB do microfone
        fab = FloatingActionButton(activity).apply {
            id = View.generateViewId()
            setImageResource(R.drawable.ic_mic)
            imageTintList = ContextCompat.getColorStateList(activity, android.R.color.white)
            backgroundTintList = ContextCompat.getColorStateList(activity, R.color.primary_color)
            contentDescription = "Falar com o Helô"
            elevation = 6f
            size = FloatingActionButton.SIZE_NORMAL
            scaleType = ImageView.ScaleType.CENTER
            visibility = View.VISIBLE

            // TAP normal: abre o chat com voz ativa (fluxo existente)
            setOnClickListener {
                openHelo(activity)
            }

            // LONG PRESS: grava áudio e abre chat com o texto transcrito
            setOnLongClickListener {
                // Vibração sutil para feedback tátil
                try {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val manager = activity.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                        manager?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        activity.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(30)
                    }
                } catch (_: Exception) {}

                if (hasMicPermission(activity)) {
                    startVoiceRecording(activity)
                } else {
                    micPermissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
                }
                true // consome o evento
            }
        }

        // Texto "Pergunte a Helô" ao lado do FAB
        textLabel = TextView(activity).apply {
            id = View.generateViewId()
            text = "Pergunte a Helô"
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 1
            elevation = 6f

            // Fundo semi-transparente com cantos arredondados
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(activity, 20).toFloat()
                setColor(0xDD1B1B2F.toInt())
                setStroke(1, 0x30FFFFFF.toInt())
            }
            setPadding(
                dpToPx(activity, 14),
                dpToPx(activity, 10),
                dpToPx(activity, 14),
                dpToPx(activity, 10)
            )
            visibility = View.VISIBLE
        }

        container?.addView(fab)
        container?.addView(
            textLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dpToPx(activity, 10)
            }
        )

        // Adiciona ao decorView para ficar SOBRE a contentView (incluindo BottomNavigation)
        val decorView = activity.window.decorView as FrameLayout
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            // Margem padrão; será ajustada com os insets
            setMargins(dpToPx(activity, 16), 0, 0, dpToPx(activity, 80))
        }
        decorView.addView(container, params)

        // Ajustar margem inferior com base nos insets do sistema (barra de navegação).
        // NUNCA registrar listener no decorView: isso substitui o onApplyWindowInsets
        // interno do DecorView e quebra o desenho das barras do sistema. Só é preciso
        // somar o inset quando a janela é edge-to-edge (Android 15+ ou tela que gerencia
        // os próprios insets); nas demais a janela já termina acima da barra de navegação.
        fun updateBottomMargin() {
            val navBarHeight = if (EdgeToEdgeInsets.isEdgeToEdge(activity)) {
                ViewCompat.getRootWindowInsets(decorView)
                    ?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
            } else 0
            container?.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = navBarHeight + dpToPx(activity, 80)
            }
        }
        container?.let { c ->
            ViewCompat.setOnApplyWindowInsetsListener(c) { _, insets ->
                updateBottomMargin()
                insets
            }
            // O tratador global consome os insets no content; garante uma leitura
            // após o attach (quando getRootWindowInsets passa a devolver valores).
            c.post { updateBottomMargin() }
        }
        updateBottomMargin()

        // Animação de pulse sutil no FAB
        startPulse(activity)

        attached = true
    }

    /**
     * Inicia gravação de voz via SpeechRecognizer.
     * Ao terminar, abre o AssistantChatActivity com o texto transcrito como prefill.
     */
    private fun startVoiceRecording(activity: AppCompatActivity) {
        val lbl = textLabel ?: return

        // Feedback visual: muda o texto enquanto grava
        val originalText = lbl.text.toString()
        lbl.text = "🎤 Ouvindo..."

        voiceManager = VoiceInputManager(
            context = activity,
            onReadyForSpeech = {
                activity.runOnUiThread {
                    lbl.text = "🎤 Fale agora..."
                }
            },
            onPartial = { /* não precisamos de texto parcial aqui */ },
            onResult = { text ->
                activity.runOnUiThread {
                    lbl.text = originalText
                    openHeloWithPrefill(activity, text)
                }
            },
            onError = { msg ->
                activity.runOnUiThread {
                    lbl.text = originalText
                    if (msg.isNotBlank()) {
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                        // Mesmo com erro, abre o chat (sem prefill)
                        openHelo(activity)
                    }
                    // Se msg vazia = cancelamento silencioso, não abre nada
                }
            },
            onEnd = {
                activity.runOnUiThread {
                    lbl.text = originalText
                }
            }
        )

        if (voiceManager?.isAvailable() != true) {
            voiceManager = null
            lbl.text = originalText
            Toast.makeText(activity, "Reconhecimento de voz indisponível.", Toast.LENGTH_SHORT).show()
            return
        }

        voiceManager?.start()
    }

    fun detach() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        voiceManager?.destroy()
        voiceManager = null
        container?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        container = null
        fab = null
        textLabel = null
        micPermissionLauncher = null
        attached = false
    }

    /** Abre o chat com voz ativa (tap normal — fluxo existente) */
    private fun openHelo(activity: AppCompatActivity) {
        val intent = Intent(activity, AssistantChatActivity::class.java).apply {
            putExtra("start_with_voice", true)
        }
        activity.startActivity(intent)
    }

    /** Abre o chat com texto transcrito como prefill (long-press — novo fluxo) */
    private fun openHeloWithPrefill(activity: AppCompatActivity, text: String) {
        val intent = Intent(activity, AssistantChatActivity::class.java).apply {
            putExtra(AssistantChatActivity.EXTRA_PREFILL, text)
        }
        activity.startActivity(intent)
    }

    private fun hasMicPermission(activity: AppCompatActivity): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun startPulse(activity: AppCompatActivity) {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(1f, 1.08f).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = OvershootInterpolator()
            addUpdateListener { fab?.scaleX = it.animatedValue as Float; fab?.scaleY = it.animatedValue as Float }
            start()
        }
    }

    private fun dpToPx(context: android.content.Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
