package com.example.loginapp.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Aguarda o Firebase Auth restaurar a sessão do usuário após reinício do processo.
 * Retorna imediatamente se já houver um usuário autenticado.
 */
suspend fun FirebaseAuth.awaitCurrentUser(): FirebaseUser? {
    currentUser?.let { return it }

    return withTimeoutOrNull(5000L) {
        suspendCancellableCoroutine { cont ->
            val listener = FirebaseAuth.AuthStateListener { auth ->
                val user = auth.currentUser
                if (user != null && cont.isActive) {
                    cont.resume(user)
                }
            }
            addAuthStateListener(listener)
            cont.invokeOnCancellation {
                removeAuthStateListener(listener)
            }
        }
    }
}
