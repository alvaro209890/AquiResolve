package com.aquiresolve.app

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Marca uma Activity que trata os próprios window insets (tem
 * setOnApplyWindowInsetsListener próprio, ex.: ClientHomeActivity).
 * O tratador global de edge-to-edge pula essas telas para não duplicar padding.
 */
interface InsetsSelfManaged

/**
 * Permite à Activity escolher a cor da faixa desenhada atrás da barra de status
 * no Android 15+ (onde window.statusBarColor é ignorado pelo sistema).
 * Sem isso a faixa usa a cor solicitada em window.statusBarColor (se opaca)
 * ou o laranja da marca (primary_color).
 */
interface StatusBarStripColor {
    val statusBarStripColorRes: Int
}

/**
 * Tratamento GLOBAL de window insets para o edge-to-edge FORÇADO do Android 15+
 * (targetSdk 35): sem isso, as barras de status e de navegação do sistema cobrem
 * o topo e o rodapé de toda tela que não trata insets — e só 3 das ~57 activities
 * tratavam.
 *
 * Registrado uma única vez no [AppApplication]; vale automaticamente para TODA
 * Activity, inclusive as criadas no futuro:
 *  - afasta o conteúdo (android.R.id.content) das barras do sistema (e do recorte
 *    de tela/notch);
 *  - quando a janela usa adjustResize, afasta também do teclado (IME) — o
 *    adjustResize clássico não redimensiona janela edge-to-edge;
 *  - desenha faixas coloridas atrás das barras (o Android 15 ignora
 *    statusBarColor/navigationBarColor), preservando o visual das versões antigas;
 *  - CONSOME os insets, impedindo que componentes Material (BottomNavigationView
 *    etc.) apliquem o mesmo inset de novo — a causa histórica da barra inferior
 *    "bugada" em Android antigo.
 *
 * Em Android < 15 não faz nada: lá o próprio sistema posiciona o conteúdo entre
 * as barras (o tema não usa mais windowTranslucentStatus nem os hacks
 * SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN removidos junto desta correção).
 */
object EdgeToEdgeInsets {

    private const val ENFORCED_SDK = 35 // Android 15 (VANILLA_ICE_CREAM)

    /** true quando a janela desta Activity é desenhada sob as barras do sistema. */
    fun isEdgeToEdge(activity: Activity): Boolean =
        Build.VERSION.SDK_INT >= ENFORCED_SDK || activity is InsetsSelfManaged

    fun install(app: Application) {
        if (Build.VERSION.SDK_INT < ENFORCED_SDK) return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is InsetsSelfManaged) return
                apply(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun apply(activity: Activity) {
        val decor = activity.window.decorView as? FrameLayout ?: return
        val content = activity.findViewById<View>(android.R.id.content) ?: return

        // Faixas que simulam o fundo das barras do sistema (altura definida pelos insets).
        val topStrip = View(activity).apply { setBackgroundColor(topStripColor(activity)) }
        val bottomStrip = View(activity).apply {
            setBackgroundColor(ContextCompat.getColor(activity, R.color.surface_color))
        }
        decor.addView(
            topStrip,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP)
        )
        decor.addView(
            bottomStrip,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM)
        )

        val handleIme = wantsAdjustResize(activity)

        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val imeBottom =
                if (handleIme) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0

            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = maxOf(bars.bottom, imeBottom)
            )
            topStrip.layoutParams =
                (topStrip.layoutParams as FrameLayout.LayoutParams).apply { height = bars.top }
            bottomStrip.layoutParams =
                (bottomStrip.layoutParams as FrameLayout.LayoutParams).apply { height = bars.bottom }

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun topStripColor(activity: Activity): Int {
        if (activity is StatusBarStripColor) {
            return ContextCompat.getColor(activity, activity.statusBarStripColorRes)
        }
        // Se a tela pediu uma cor opaca em window.statusBarColor e o sistema ainda a
        // reporta, respeita; senão cai no laranja da marca (mesmo default do tema).
        @Suppress("DEPRECATION")
        val requested = activity.window.statusBarColor
        if (Color.alpha(requested) == 0xFF) return requested
        return ContextCompat.getColor(activity, R.color.primary_color)
    }

    private fun wantsAdjustResize(activity: Activity): Boolean {
        val mode = activity.window.attributes.softInputMode
        return (mode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST) ==
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }
}
