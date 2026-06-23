package com.aquiresolve.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Gerenciador do tema (claro / escuro) do app.
 *
 * - Por padrão segue o tema do sistema (SYSTEM).
 * - O usuário pode forçar Claro ou Escuro pelo Perfil → Aparência.
 * - A escolha é persistida em SharedPreferences e reaplicada no startup
 *   (ver [AppApplication.onCreate]).
 *
 * Usa o mecanismo padrão do Android ([AppCompatDelegate.setDefaultNightMode]),
 * que recria as Activities automaticamente — sem gambiarra de recreate manual.
 */
object ThemeManager {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_MODE = "theme_mode"

    /** Modos de tema disponíveis para o usuário. */
    enum class Mode(val storageValue: String) {
        SYSTEM("system"),
        LIGHT("light"),
        DARK("dark");

        companion object {
            fun fromStorage(value: String?): Mode =
                values().firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Lê o modo salvo (default = SYSTEM). */
    fun current(context: Context): Mode =
        Mode.fromStorage(prefs(context).getString(KEY_MODE, Mode.SYSTEM.storageValue))

    /**
     * Aplica o modo informado e persiste a escolha.
     * Chamar a partir da UI (Perfil → Aparência). A Activity é recriada pelo
     * próprio AppCompat quando o night mode muda.
     */
    fun apply(context: Context, mode: Mode) {
        prefs(context).edit().putString(KEY_MODE, mode.storageValue).apply()
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode))
    }

    /** Aplica o modo salvo. Chamar cedo no startup ([AppApplication.onCreate]). */
    fun applyStored(context: Context) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(current(context)))
    }

    private fun toNightMode(mode: Mode): Int = when (mode) {
        Mode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        Mode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        Mode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }

    /** Rótulo legível para exibir como subtítulo no Perfil. */
    fun label(mode: Mode): String = when (mode) {
        Mode.SYSTEM -> "Sistema padrão"
        Mode.LIGHT -> "Claro"
        Mode.DARK -> "Escuro"
    }
}
