package com.aquiresolve.app

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Utilitário para aplicar window insets corretamente em qualquer Activity,
 * evitando que a barra de navegação do sistema (gestos ou 3 botões)
 * sobreponha o conteúdo do app.
 *
 * Uso: chame `InsetsHelper.apply(activity, rootView, bottomView?)` no onCreate.
 */
object InsetsHelper {

    /**
     * Aplica edge-to-edge + padding de navegação.
     *
     * @param activity a Activity atual
     * @param rootView a view raiz do layout (deve ter um id)
     * @param bottomView (opcional) view que fica no final e precisa de padding extra
     * @param extraBottomDp (opcional) padding adicional em dp além do inset do sistema
     */
    fun apply(
        activity: Activity,
        rootView: View,
        bottomView: View? = null,
        extraBottomDp: Int = 0
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Padding superior para status bar
            rootView.updatePadding(top = systemBars.top)

            insets
        }

        // Se houver uma view no fundo (BottomNavigationView, input bar, etc.),
        // adiciona padding da barra de navegação
        if (bottomView != null) {
            val density = activity.resources.displayMetrics.density
            ViewCompat.setOnApplyWindowInsetsListener(bottomView) { v, insets ->
                val navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                v.updatePadding(bottom = navBar + (extraBottomDp * density).toInt())
                insets
            }
        }
    }
}
