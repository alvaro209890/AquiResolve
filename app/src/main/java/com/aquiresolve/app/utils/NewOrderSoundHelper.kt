package com.aquiresolve.app.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.aquiresolve.app.AlertForegroundService

/**
 * Helper para tocar o som de alerta quando um novo pedido chega para prestadores aprovados.
 * O arquivo socb4p1medq-police-siren-sfx-2.mp3 deve estar em:
 * - app/src/main/assets/socb4p1medq-police-siren-sfx-2.mp3
 * - ou app/src/main/res/raw/ (como new_order_alert.mp3)
 */
object NewOrderSoundHelper {
    
    private const val TAG = "NewOrderSoundHelper"
    
    // Nome do arquivo em assets (caminho exato informado pelo usuário)
    private const val ASSET_SOUND_FILE = "socb4p1medq-police-siren-sfx-2.mp3"
    
    // Fallback: resource raw (arquivo new_order_alert.mp3 em res/raw/)
    private const val RAW_SOUND_RES_ID = "new_order_alert"
    private const val MIN_PLAY_INTERVAL_MS = 1500L
    private const val PLAY_REPEAT_COUNT = 3
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var lastPlayTimestamp: Long = 0L
    private var activePlaybackSessionId: Long = 0L
    
    // Controle de loop contínuo — toca até que stopSound() seja chamado
    // Mapeia orderId → sessionId para rastrear qual sessão está ativa por pedido
    private val continuousSessions = mutableMapOf<String, Long>()
    private var continuousPlayer: MediaPlayer? = null
    private var continuousSessionId: Long = 0L
    private var appContext: Context? = null  // guardado para parar o Foreground Service
    
    /**
     * Toca o som de alerta de novo pedido.
     * Repete o alerta algumas vezes para garantir audibilidade.
     */
    fun playNewOrderSound(context: Context) {
        val appContext = context.applicationContext
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { playNewOrderSound(appContext) }
            return
        }

        val now = SystemClock.elapsedRealtime()
        val sessionId = synchronized(this) {
            if (now - lastPlayTimestamp < MIN_PLAY_INTERVAL_MS) {
                Log.d(TAG, "Ignorando toque duplicado em intervalo curto")
                return
            }
            lastPlayTimestamp = now
            activePlaybackSessionId += 1L
            activePlaybackSessionId
        }

        playSoundIteration(appContext, sessionId, PLAY_REPEAT_COUNT)
    }

    private fun playSoundIteration(context: Context, sessionId: Long, remainingPlays: Int) {
        if (remainingPlays <= 0) {
            completeSession(sessionId)
            return
        }

        if (!isSessionActive(sessionId)) {
            releasePlayer()
            return
        }

        try {
            releasePlayer()

            val player = buildPlayer(context)
            if (player == null) {
                Log.w(TAG, "Não foi possível criar MediaPlayer para o alerta")
                completeSession(sessionId)
                return
            }

            mediaPlayer = player
            player.setOnCompletionListener {
                releasePlayer()
                if (isSessionActive(sessionId)) {
                    playSoundIteration(context, sessionId, remainingPlays - 1)
                }
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Erro no MediaPlayer durante alerta: what=$what extra=$extra")
                releasePlayer()
                if (isSessionActive(sessionId)) {
                    playSoundIteration(context, sessionId, remainingPlays - 1)
                }
                true
            }
            player.start()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao tocar iteração do som de novo pedido: ${e.message}", e)
            releasePlayer()
            completeSession(sessionId)
        }
    }
    
    private fun buildPlayer(context: Context): MediaPlayer? {
        val player = MediaPlayer()
        return try {
            player.setupAudioAttributes()

            val loadedFromAssets = try {
                context.assets.openFd(ASSET_SOUND_FILE).use { assetFd ->
                    player.setDataSource(assetFd.fileDescriptor, assetFd.startOffset, assetFd.length)
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "Arquivo não encontrado em assets: $ASSET_SOUND_FILE - ${e.message}")
                false
            }

            if (!loadedFromAssets) {
                val resId = context.resources.getIdentifier(RAW_SOUND_RES_ID, "raw", context.packageName)
                if (resId != 0) {
                    context.resources.openRawResourceFd(resId)?.use { rawFd ->
                        player.setDataSource(rawFd.fileDescriptor, rawFd.startOffset, rawFd.length)
                    } ?: run {
                        Log.w(TAG, "Raw resource encontrado, mas sem AssetFileDescriptor: $RAW_SOUND_RES_ID")
                        player.release()
                        return null
                    }
                } else {
                    Log.w(TAG, "Som não encontrado em assets ou raw ($RAW_SOUND_RES_ID)")
                    player.release()
                    return null
                }
            }

            player.isLooping = false
            player.prepare()
            player
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao preparar MediaPlayer: ${e.message}", e)
            try {
                player.release()
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun MediaPlayer.setupAudioAttributes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            setAudioStreamType(AudioManager.STREAM_ALARM)
        }
        setVolume(1.0f, 1.0f)
    }

    private fun isSessionActive(sessionId: Long): Boolean {
        return synchronized(this) {
            activePlaybackSessionId == sessionId
        }
    }

    private fun completeSession(sessionId: Long) {
        synchronized(this) {
            if (activePlaybackSessionId == sessionId) {
                releasePlayer()
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // Loop contínuo — som repete até stopSound() ser chamado
    // Usado para alertas de novo pedido que devem persistir até
    // que o prestador aceite ou rejeite.
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Inicia reprodução contínua do som de alerta.
     * O som repete em loop até que [stopSound] seja chamado.
     *
     * @param context  Context da aplicação
     * @param orderId  ID do pedido associado (para rastreamento)
     */
    fun startContinuousPlay(context: Context, orderId: String) {
        val appContext = context.applicationContext
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startContinuousPlay(appContext, orderId) }
            return
        }

        synchronized(this) {
            // Se já existe uma sessão para este pedido, não duplica
            if (continuousSessions.containsKey(orderId)) {
                Log.d(TAG, "Loop contínuo já ativo para pedido $orderId")
                return
            }

            // Guarda o application context para uso futuro (parar o Foreground Service)
            this.appContext = appContext

            continuousSessionId += 1L
            val sessionId = continuousSessionId
            continuousSessions[orderId] = sessionId

            // Iniciar Foreground Service para manter o processo vivo em background
            AlertForegroundService.start(appContext)

            Log.d(TAG, "Iniciando loop contínuo para pedido $orderId (session=$sessionId)")
        }

        playContinuousIteration(appContext, orderId)
    }

    /**
     * Para o som contínuo associado a um pedido específico.
     * Se nenhum orderId for passado, para TODOS os sons contínuos.
     */
    fun stopSound(orderId: String? = null) {
        synchronized(this) {
            if (orderId != null) {
                continuousSessions.remove(orderId)
                Log.d(TAG, "Loop contínuo parado para pedido $orderId (${continuousSessions.size} ativos restantes)")
            } else {
                continuousSessions.clear()
                Log.d(TAG, "Todos os loops contínuos parados")
            }
            stopContinuousPlayer()

            // Se não há mais sons ativos, para o Foreground Service
            if (continuousSessions.isEmpty()) {
                val ctx = this.appContext
                if (ctx != null) {
                    mainHandler.post {
                        try {
                            AlertForegroundService.stop(ctx)
                        } catch (e: Exception) {
                            Log.w(TAG, "Erro ao parar AlertForegroundService: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    /**
     * Verifica se há som contínuo tocando para um pedido.
     */
    fun isPlayingForOrder(orderId: String): Boolean {
        return synchronized(this) {
            continuousSessions.containsKey(orderId)
        }
    }

    private fun playContinuousIteration(context: Context, orderId: String) {
        val sessionActive = synchronized(this) {
            continuousSessions.containsKey(orderId)
        }
        if (!sessionActive) {
            stopContinuousPlayer()
            return
        }

        try {
            stopContinuousPlayer()

            val player = buildPlayer(context)
            if (player == null) {
                Log.w(TAG, "Não foi possível criar MediaPlayer para loop contínuo")
                return
            }

            continuousPlayer = player
            player.isLooping = true  // ← loop nativo do MediaPlayer
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Erro no MediaPlayer contínuo: what=$what extra=$extra")
                stopContinuousPlayer()
                // Tenta reiniciar após erro
                mainHandler.postDelayed({
                    playContinuousIteration(context, orderId)
                }, 1000L)
                true
            }
            player.start()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao tocar iteração contínua: ${e.message}", e)
            stopContinuousPlayer()
        }
    }

    private fun stopContinuousPlayer() {
        try {
            continuousPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao liberar player contínuo: ${e.message}")
        }
        continuousPlayer = null
    }
    
    private fun releasePlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao liberar MediaPlayer: ${e.message}")
        }
        mediaPlayer = null
    }
}
