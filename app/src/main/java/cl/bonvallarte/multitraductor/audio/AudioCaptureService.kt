package cl.bonvallarte.multitraductor.audio

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicitly consented playback capture probe.
 * Only app-UID and USAGE_MEDIA audio is measured (dBFS).
 * NO speech recognition, persistence, upload or translation.
 */
class AudioCaptureService:Service() {
    private val running=AtomicBoolean(false)
    private val handler=Handler(Looper.getMainLooper())
    private var projection:MediaProjection?=null
    private var callback:MediaProjection.Callback?=null
    private var recorder:AudioRecord?=null

    override fun onBind(intent:Intent?)=null

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action!=START)return START_NOT_STICKY
        if(running.get())return START_NOT_STICKY
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            CaptureState.status="Permiso de audio denegado";stopSelf();return START_NOT_STICKY
        }
        val result=intent.getIntExtra(RESULT,Activity.RESULT_CANCELED)
        val consent:Intent?=if(Build.VERSION.SDK_INT>=33)
            intent.getParcelableExtra(CONSENT,Intent::class.java)
        else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(CONSENT)
        }
        if(result!=Activity.RESULT_OK || consent==null){
            CaptureState.status="Consentimiento ausente";stopSelf();return START_NOT_STICKY
        }
        try{
            val notificationManager=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(NotificationChannel(CHANNEL,
                "Prueba de captura de audio",NotificationManager.IMPORTANCE_LOW))
            val notification=Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Multi Traductor · audio")
                .setContentText("Midiendo sonido permitido; no se guarda audio")
                .setOngoing(true).build()
            // On Android 14 start foreground service AFTER consent and BEFORE getMediaProjection.
            startForeground(7,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p=manager.getMediaProjection(result,consent)
            projection=p
            callback=object:MediaProjection.Callback(){
                override fun onStop(){
                    CaptureState.status="Android revocó la captura"
                    stopSelf()
                }
            }
            p.registerCallback(callback!!,handler)
            record(p)
        }catch(e:Exception){
            CaptureState.status="Error al iniciar captura: "+e.javaClass.simpleName
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun record(token:MediaProjection){
        val rate=16000
        val fmt=AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minimum=AudioRecord.getMinBufferSize(rate,
            AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        if(minimum<=0)error("PCM16 no disponible")
        val config=AudioPlaybackCaptureConfiguration.Builder(token)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            // Privacy-first: restrict to our app. WebView may be un-capturable:
            // that is precisely what the real-device prototype must establish.
            .addMatchingUid(applicationInfo.uid)
            .build()
        val audio=AudioRecord.Builder().setAudioFormat(fmt)
            .setBufferSizeInBytes((minimum*2).coerceAtLeast(16384))
            .setAudioPlaybackCaptureConfig(config).build()
        if(audio.state!=AudioRecord.STATE_INITIALIZED){
            audio.release();error("AudioRecord no inicializado")
        }
        recorder=audio
        audio.startRecording()
        running.set(true)
        CaptureState.reset()
        CaptureState.active=true
        CaptureState.status="Escuchando medio permitido"
        Thread({
            var lastLogged=0L
            val pcm=ShortArray(2048)
            while(running.get()){
                val n=try{audio.read(pcm,0,pcm.size)}catch(e:Exception){break}
                if(n<=0)break
                val db=AudioLevelMeter.dbfs(pcm,n)
                CaptureState.levelDbfs=db
                if (System.currentTimeMillis() - lastLogged >= 800L) {
                    android.util.Log.i("MT_AUDIO_PROBE", "samples=" + CaptureState.framesReceived +
                        " dbfs=" + "%.1f".format(java.util.Locale.US, db) +
                        " uid=" + applicationInfo.uid)
                    lastLogged=System.currentTimeMillis()
                }
                CaptureState.framesReceived+=n
                if(db > -48.0 && CaptureState.firstNonSilentTime==0L)
                    CaptureState.firstNonSilentTime=System.currentTimeMillis()
                // Discard PCM. No saving, network, ML or transcript yet.
            }
            if(running.get()){
                CaptureState.status="Lectura finalizada"
                handler.post{stopSelf()}
            }
        },"pcm-level-probe").start()
    }

    override fun onDestroy(){
        running.set(false)
        try{recorder?.stop()}catch(e:Exception){}
        try{recorder?.release()}catch(e:Exception){}
        recorder=null
        try{callback?.let{projection?.unregisterCallback(it)}}catch(e:Exception){}
        try{projection?.stop()}catch(e:Exception){}
        projection=null;callback=null
        CaptureState.active=false
        if(!CaptureState.status.startsWith("Error") &&
           !CaptureState.status.contains("revocó"))
            CaptureState.status="Escucha detenida"
        super.onDestroy()
    }
    companion object {
        const val START="cl.bonvallarte.multitraductor.AUDIO_START"
        const val RESULT="projection_result"
        const val CONSENT="projection_consent"
        private const val CHANNEL="capture_probe"
    }
}
