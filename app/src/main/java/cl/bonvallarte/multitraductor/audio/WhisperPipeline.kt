package cl.bonvallarte.multitraductor.audio

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sequential offline audio to text to Spanish, 5 second chunks.
 * Bounded buffer; clips are dropped rather than accumulating unbounded work on midrange phones.
 * No audio is stored or sent to any server.
 */
object WhisperPipeline {
    private const val SAMPLE_RATE=16000
    private const val CHUNK_SIZE=SAMPLE_RATE*5
    private val executor=Executors.newSingleThreadExecutor()
    private val busy=AtomicBoolean(false)
    private val mutex=Any()
    private val accumulated=FloatArray(CHUNK_SIZE)
    private var filled=0
    private var voicedSamples=0
    @Volatile private var started=false
    @Volatile private var nativeContext=0L
    private var translator:OnDeviceTranslator?=null

    fun start(appContext:Context) {
        synchronized(mutex){
            filled=0;voicedSamples=0;started=true
        }
        if(!ModelManager.installed(appContext)){
            CaptureState.aiStatus="Instala el modelo IA (32 MB) para reconocer voz"
            started=false;return
        }
        CaptureState.aiStatus="Preparando Whisper local…"
        executor.execute {
            try{
                if(nativeContext==0L)
                    nativeContext=WhisperNative.load(ModelManager.modelFile(appContext).absolutePath)
                if(nativeContext==0L)error("Whisper no pudo cargar el modelo")
                if(translator==null)translator=OnDeviceTranslator(appContext)
                CaptureState.aiStatus="Whisper preparado; escuchando voz…"
            }catch(e:Throwable){
                started=false
                CaptureState.aiStatus="Error del motor IA: "+e.javaClass.simpleName
                Log.e("MT_ASR","Whisper load failed",e)
            }
        }
    }

    fun offer(pcm:ShortArray,count:Int,dbfs:Double){
        if(!started || nativeContext==0L || busy.get())return
        var chunk:FloatArray?=null
        var voiced=false
        synchronized(mutex){
            val n=count.coerceIn(0,pcm.size)
            val remaining=CHUNK_SIZE-filled
            val take=n.coerceAtMost(remaining)
            for(i in 0 until take)accumulated[filled+i]=pcm[i]/32768.0f
            filled+=take
            if(dbfs>-43.0)voicedSamples+=take
            if(filled>=CHUNK_SIZE){
                chunk=accumulated.copyOf()
                voiced=voicedSamples > SAMPLE_RATE / 2
                filled=0;voicedSamples=0
            }
        }
        val audio=chunk?:return
        if(!voiced){
            CaptureState.aiStatus="Sin voz suficiente en últimos 5 s"
            return
        }
        if(!busy.compareAndSet(false,true))return
        CaptureState.aiStatus="Reconociendo voz en el equipo…"
        executor.execute{
            try{
                val result=WhisperNative.transcribe(nativeContext,audio).trim()
                if(result.isEmpty()){
                    CaptureState.aiStatus="No se reconocieron palabras"
                    busy.set(false);return@execute
                }
                CaptureState.originalText=result
                CaptureState.aiStatus="Traduciendo localmente…"
                translator?.translate(result){translation,info->
                    CaptureState.translatedText=translation
                    CaptureState.aiStatus=info
                    Log.i("MT_ASR","segment translated; chars="+translation.length+" lang="+info)
                    busy.set(false)
                } ?:run{CaptureState.aiStatus="Traductor no preparado";busy.set(false)}
            }catch(e:Throwable){
                CaptureState.aiStatus="Error de reconocimiento: "+e.javaClass.simpleName
                Log.e("MT_ASR","ASR failure",e)
                busy.set(false)
            }
        }
    }
    fun stop(){
        synchronized(mutex){started=false;filled=0;voicedSamples=0}
        CaptureState.aiStatus="Reconocimiento detenido"
    }
}
