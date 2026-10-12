package cl.bonvallarte.multitraductor.audio

/** Volatile diagnostic state, no PCM storage. */
object CaptureState {
    @Volatile var active = false
    @Volatile var status = "Escucha detenida"
    @Volatile var levelDbfs = -90.0
    @Volatile var framesReceived = 0L
    @Volatile var firstNonSilentTime = 0L
    fun reset() {
        active = false; status = "Escucha detenida"
        levelDbfs = -90.0; framesReceived = 0L; firstNonSilentTime = 0L
    }
}
