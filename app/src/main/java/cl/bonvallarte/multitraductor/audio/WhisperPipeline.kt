package cl.bonvallarte.multitraductor.audio

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Continuous off-line audio → Whisper → ML Kit.
 * Capture never stops for inference. Up to three 5-second windows are buffered;
 * if inference cannot catch up, discard oldest pending window and report loss.
 * No PCM is stored outside volatile memory or uploaded.
 */
object WhisperPipeline {
    private val executor = Executors.newSingleThreadExecutor()
    private val draining = AtomicBoolean(false)
    private val generation = AtomicLong(0)
    private val buffer = AudioChunkBuffer()
    private val deliveryLock = Any()
    @Volatile private var started = false
    @Volatile private var nativeContext = 0L
    private var translator: OnDeviceTranslator? = null
    private var deliveredSequence = 0L
    private var lastSourceSignature = ""
    private var lastPublishedSignature = ""

    fun start(appContext: Context) {
        val session = generation.incrementAndGet()
        started = ModelManager.installed(appContext)
        buffer.clear()
        synchronized(deliveryLock) {
            deliveredSequence = 0L
            lastSourceSignature = ""
            lastPublishedSignature = ""
        }
        if (!started) {
            CaptureState.aiStatus = "Instala el modelo IA (32 MB) para reconocer voz"
            return
        }
        CaptureState.aiStatus = "Preparando Whisper local; acumulando audio…"
        executor.execute {
            try {
                if (nativeContext == 0L)
                    nativeContext = WhisperNative.load(ModelManager.modelFile(appContext).absolutePath)
                if (nativeContext == 0L) error("Whisper no pudo cargar el modelo")
                if (translator == null) translator = OnDeviceTranslator(appContext)
                if (started && generation.get() == session) {
                    CaptureState.aiStatus = "Whisper listo; captura y traducción continuas"
                    scheduleDrain(session)
                }
            } catch (e: Throwable) {
                if (generation.get() == session) {
                    started = false
                    CaptureState.aiStatus = "Error del motor IA: " + e.javaClass.simpleName
                }
                Log.e("MT_ASR", "Whisper load failed", e)
            }
        }
    }

    fun offer(pcm: ShortArray, count: Int, dbfs: Double) {
        // Critical: do not check "busy" here. Every captured sample is
        // windowed, even while Whisper or ML Kit processes earlier chunks.
        if (!started) return
        val added = buffer.append(pcm, count, dbfs)
        if (added > 0) {
            val drops = buffer.droppedCount()
            if (drops > 0) {
                CaptureState.aiStatus = "Procesando; " + drops + " bloques omitidos por sobrecarga"
                Log.w("MT_ASR", "ASR queue overload: dropped=" + drops + " pending=" + buffer.pendingCount())
            }
            scheduleDrain(generation.get())
        }
    }

    private fun scheduleDrain(session: Long) {
        if (!started || nativeContext == 0L || session != generation.get()) return
        if (!draining.compareAndSet(false, true)) return
        executor.execute {
            try {
                while (started && session == generation.get()) {
                    val chunk = buffer.poll() ?: break
                    processChunk(session, chunk)
                }
            } finally {
                draining.set(false)
                // Wake up if a producer enqueued between poll() and finally.
                if (started && session == generation.get() && buffer.pendingCount() > 0)
                    scheduleDrain(session)
            }
        }
    }

    private fun processChunk(session: Long, chunk: AudioChunkBuffer.Chunk) {
        try {
            CaptureState.aiStatus = "Reconociendo bloque " + chunk.sequence + "; escuchando en paralelo…"
            val raw = WhisperNative.transcribe(nativeContext, chunk.samples)
            if (!started || session != generation.get()) return
            val text = SubtitleTextFilter.clean(raw)
            if (text.isBlank()) return
            val signature = SubtitleTextFilter.signature(text)
            if (signature.isBlank()) return
            synchronized(deliveryLock) {
                // Avoid showing the same sentence over and over from adjacent
                // overlapping/silent segments; do not suppress new sentences.
                if (signature.length >= 8 && signature == lastSourceSignature) {
                    Log.i("MT_ASR", "Repeated ASR window filtered seq=" + chunk.sequence)
                    return
                }
                lastSourceSignature = signature
            }
            CaptureState.originalText = text
            val client = translator
            if (client == null) {
                CaptureState.aiStatus = "Traductor no preparado"
                return
            }
            // Async translation: speech recognition does NOT wait for ML Kit.
            client.translate(text) { translation, info ->
                publishTranslation(session, chunk.sequence, translation, info)
            }
        } catch (e: Throwable) {
            if (started && session == generation.get())
                CaptureState.aiStatus = "Error de reconocimiento: " + e.javaClass.simpleName
            Log.e("MT_ASR", "ASR failure", e)
        }
    }

    private fun publishTranslation(session: Long, sequence: Long, translation: String, info: String) {
        if (!started || generation.get() != session) return
        val cleaned = SubtitleTextFilter.clean(translation)
        val signature = SubtitleTextFilter.signature(cleaned)
        if (cleaned.isBlank() || signature.isBlank()) return
        synchronized(deliveryLock) {
            if (sequence <= deliveredSequence || !started || session != generation.get()) return
            if (signature.length >= 8 && signature == lastPublishedSignature) return
            deliveredSequence = sequence
            lastPublishedSignature = signature
            CaptureState.translatedText = cleaned
            CaptureState.aiStatus = info + " · cola " + buffer.pendingCount() +
                " · perdidos " + buffer.droppedCount()
        }
        Log.i("MT_ASR", "translated seq=" + sequence + " chars=" + cleaned.length +
            " lang=" + info + " pending=" + buffer.pendingCount() +
            " dropped=" + buffer.droppedCount())
    }

    fun stop() {
        generation.incrementAndGet()
        started = false
        buffer.clear()
        CaptureState.aiStatus = "Reconocimiento detenido"
    }
}
