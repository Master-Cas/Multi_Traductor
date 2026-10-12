package cl.bonvallarte.multitraductor.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit one-time installation of the SHA-256-pinned multilingual Tiny q5_1 model. */
object ModelManager {
    const val MODEL_SIZE = 32152673L
    const val SHA256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7"
    const val DOWNLOAD = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"
    private val downloading=AtomicBoolean(false)
    private val main=Handler(Looper.getMainLooper())

    fun modelFile(context:Context):File =
        File(context.getExternalFilesDir(null) ?: context.filesDir,"ggml-tiny-q5_1.bin")

    fun installed(context:Context):Boolean = modelFile(context).length()==MODEL_SIZE

    fun install(context:Context, notify:(String)->Unit) {
        if (!downloading.compareAndSet(false,true)) return
        Thread({
            val file=modelFile(context)
            val part=File(file.absolutePath+".part")
            try {
                if(installed(context) && digest(file)==SHA256) {
                    main.post{notify("Modelo Whisper ya instalado")}
                    return@Thread
                }
                main.post{notify("Descargando modelo multilingüe de 32 MB…")}
                val connection=URL(DOWNLOAD).openConnection() as HttpURLConnection
                connection.connectTimeout=15000
                connection.readTimeout=30000
                connection.instanceFollowRedirects=true
                connection.connect()
                if(connection.responseCode !in 200..299) error("HTTP "+connection.responseCode)
                val bytes=connection.inputStream.use{input->
                    part.outputStream().use{output->
                        val data=ByteArray(64*1024)
                        var total=0L
                        var lastPercent=-1
                        while(true){
                            val n=input.read(data)
                            if(n<0)break
                            output.write(data,0,n)
                            total+=n
                            val percent=(total*100L/MODEL_SIZE).toInt().coerceAtMost(100)
                            if(percent>=lastPercent+10){
                                lastPercent=percent
                                main.post{notify("Descargando modelo: "+percent+" %")}
                            }
                            if(total>MODEL_SIZE)error("El modelo supera el tamaño esperado")
                        }
                        total
                    }
                }
                connection.disconnect()
                if(bytes!=MODEL_SIZE || digest(part)!=SHA256)error("Verificación SHA-256 fallida")
                if(file.exists())file.delete()
                if(!part.renameTo(file))error("No se pudo instalar el modelo")
                main.post{notify("Modelo Whisper listo (multilingüe, local)")}
            }catch(e:Exception){
                part.delete()
                main.post{notify("Error de modelo: "+(e.message?:"desconocido"))}
            }finally{downloading.set(false)}
        },"whisper-model-download").start()
    }

    private fun digest(file:File):String {
        val md=MessageDigest.getInstance("SHA-256")
        file.inputStream().use{input->
            val bytes=ByteArray(65536)
            while(true){
                val n=input.read(bytes); if(n<0)break
                md.update(bytes,0,n)
            }
        }
        return md.digest().joinToString(""){"%02x".format(it)}
    }
}
