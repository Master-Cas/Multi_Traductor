package cl.bonvallarte.multitraductor.audio

/** All inference runs on the Android CPU. JNI never sends audio over network. */
object WhisperNative {
    init { System.loadLibrary("multi_whisper") }
    external fun load(path: String): Long
    external fun transcribe(context: Long, pcm: FloatArray): String
    external fun unload(context: Long)
}
