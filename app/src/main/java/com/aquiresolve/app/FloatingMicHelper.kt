package com.aquiresolve.app

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
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
 * chat da Helô). Posicionado no canto inferior-esquerdo, com animação de pulse.
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
    private var pulseAnimator: ValueAnimator? = null
    private var micPermissionLauncher: ActivityResultLauncher<String>? = null
    private var attached = false

    fun attach(activity: AppCompatActivity) {
        if (attached) return

        // Não mostra o FAB no próprio chat da Helô
        if (activity is AssistantChatActivity) return

        micPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                openHelo(activity)
            } else {
                Toast.makeText(activity, "Permita o microfone para falar com o Helô.", Toast.LENGTH_SHORT).show()
            }
        }

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

            setOnClickListener {
                if (hasMicPermission(activity)) {
                    openHelo(activity)
                } else {
                    micPermissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

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
        decorView.addView(fab, params)

        // Ajustar margem inferior com base nos insets do sistema (barra de navegação)
        ViewCompat.setOnApplyWindowInsetsListener(decorView) { _, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            fab?.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = navBarHeight + dpToPx(activity, 80)
            }
            insets
        }

        // Animação de pulse sutil
        startPulse(activity)

        attached = true
    }

    fun detach() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        fab?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        fab = null
        micPermissionLauncher = null
        attached = false
    }

    private fun openHelo(activity: AppCompatActivity) {
        val intent = Intent(activity, AssistantChatActivity::class.java).apply {
            // Flag para abrir com voz ativa
            putExtra("start_with_voice", true)
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
