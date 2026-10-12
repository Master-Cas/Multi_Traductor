package cl.bonvallarte.multitraductor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import cl.bonvallarte.multitraductor.audio.AudioCaptureService
import cl.bonvallarte.multitraductor.audio.CaptureState
import cl.bonvallarte.multitraductor.audio.ModelManager
import java.util.Locale

/** WebView + translation strip + internal-audio feasibility meter. NOT a voice translator yet. */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var address: EditText
    private lateinit var subtitle: TextView
    private lateinit var status: TextView
    private lateinit var aiStatus: TextView
    private lateinit var listen: Button
    private lateinit var toolbar: LinearLayout
    private lateinit var webHolder: FrameLayout
    private var customVideo: View? = null
    private var videoCallback: WebChromeClient.CustomViewCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateStatus = object : Runnable {
        override fun run() {
            if (::status.isInitialized) {
                val s = CaptureState
                status.text = if (s.active) {
                    if (s.firstNonSilentTime > 0)
                        String.format(Locale.US,"Audio detectado · %.0f dBFS · %.1fs",s.levelDbfs,s.framesReceived / 16000.0)
                    else String.format(Locale.US,"Capturando sin señal · %.1fs",s.framesReceived / 16000.0)
                } else s.status
                listen.text = if (s.active) "■ Detener" else "♫ Escuchar"
                aiStatus.text = s.aiStatus
                if (s.translatedText.isNotBlank()) subtitle.text = s.translatedText
            }
            handler.postDelayed(this,450)
        }
    }

    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        (getSystemService(AUDIO_SERVICE) as AudioManager)
            .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
        createLayout()
        configureWeb()
        if (state == null || web.restoreState(state) == null) testPage()
        handler.post(updateStatus)
    }
    private fun dp(n:Int) = (n * resources.displayMetrics.density).toInt()
    private fun button(label:String, callback:()->Unit):Button = Button(this).apply {
        text=label; textSize=12f; isAllCaps=false
        setOnClickListener {callback()}
    }

    private fun createLayout() {
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16,13,23))
        }
        toolbar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        toolbar.addView(button("‹"){if(web.canGoBack())web.goBack()},
            LinearLayout.LayoutParams(dp(43),dp(44)))
        address=EditText(this).apply {
            hint="https://…";setSingleLine(true)
            textSize=13f;setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setOnEditorActionListener {_,_,_-> navigate();true}
        }
        toolbar.addView(address,LinearLayout.LayoutParams(0,dp(44),1f))
        toolbar.addView(button("Ir"){navigate()},LinearLayout.LayoutParams(dp(43),dp(44)))
        toolbar.addView(button("Test"){testPage()},LinearLayout.LayoutParams(dp(59),dp(44)))
        toolbar.addView(button("Modelo IA"){
            if(ModelManager.installed(this)) {
                CaptureState.aiStatus="Modelo instalado; pulsa Escuchar"
            } else {
                ModelManager.install(applicationContext){message->
                    CaptureState.aiStatus=message
                    Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
                }
            }
        },LinearLayout.LayoutParams(dp(102),dp(44)))
        root.addView(toolbar)
        webHolder=FrameLayout(this)
        web=WebView(this)
        webHolder.addView(web,FrameLayout.LayoutParams(-1,-1))
        root.addView(webHolder,LinearLayout.LayoutParams(-1,0,1f))
        val bottom=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(35,25,47))
            setPadding(dp(8),dp(5),dp(8),dp(8))
        }
        subtitle=TextView(this).apply {
            text="Traducción pendiente de IA · prueba de captura"
            textSize=16f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)
        }
        bottom.addView(subtitle,LinearLayout.LayoutParams(-1,dp(40)))
        val controls=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        listen=button("♫ Escuchar") {
            if(CaptureState.active) stopAudio() else beginAudio()
        }
        controls.addView(listen,LinearLayout.LayoutParams(dp(120),dp(46)))
        status=TextView(this).apply {
            text=CaptureState.status;textSize=11f
            setTextColor(Color.rgb(225,196,240))
            setPadding(dp(8),0,0,0)
        }
        controls.addView(status,LinearLayout.LayoutParams(0,-2,1f))
        bottom.addView(controls)
        aiStatus=TextView(this).apply {
            textSize=11f
            setTextColor(Color.rgb(210,175,231))
            text=CaptureState.aiStatus
            gravity=Gravity.CENTER
        }
        bottom.addView(aiStatus)
        root.addView(bottom)
        setContentView(root)
        orientation(resources.configuration.orientation)
    }

    private fun configureWeb() {
        web.settings.apply {
            javaScriptEnabled=true
            domStorageEnabled=true
            mediaPlaybackRequiresUserGesture=true
            allowFileAccess=false
            allowContentAccess=false
            javaScriptCanOpenWindowsAutomatically=false
            setSupportMultipleWindows(false)
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled=true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false)
        web.webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView?, request:WebResourceRequest?):Boolean {
                val scheme=request?.url?.scheme
                return scheme!="https" && scheme!="http"
            }
            override fun onPageFinished(view:WebView?, url:String?) {
                address.setText(url?:"")
            }
        }
        web.webChromeClient=object:WebChromeClient(){
            override fun onShowCustomView(view:View?, cb:CustomViewCallback?){
                if(view==null || cb==null || customVideo!=null){cb?.onCustomViewHidden();return}
                customVideo=view;videoCallback=cb
                web.visibility=View.GONE
                webHolder.addView(view,FrameLayout.LayoutParams(-1,-1))
                toolbar.visibility=View.GONE
                // Keep bottom translation strip visible in fullscreen playback.
            }
            override fun onHideCustomView(){hideVideo()}
        }
    }

    private fun hideVideo(){
        val v=customVideo?:return
        webHolder.removeView(v)
        customVideo=null;web.visibility=View.VISIBLE;toolbar.visibility=View.VISIBLE
        videoCallback?.onCustomViewHidden();videoCallback=null
    }
    private fun navigate(){
        val raw=address.text.toString().trim()
        if(raw.isEmpty())return
        val url=if(raw.contains("://"))raw else "https://"+raw
        if(android.net.Uri.parse(url).scheme in listOf("https","http"))web.loadUrl(url)
    }
    private fun testPage(){
        val wav=android.util.Base64.encodeToString(assets.open("voice-test.wav").use { it.readBytes() }, android.util.Base64.NO_WRAP)
        val html=assets.open("audio-test.html").bufferedReader().use {it.readText()}.replace("__WAV_BASE64__",wav)
        web.loadDataWithBaseURL("https://appassets.androidplatform.net/",
            html,"text/html","UTF-8",null)
        address.setText("Prueba audio HTML5")
    }

    private fun beginAudio(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),501);return
        }
        val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(),502)
    }
    override fun onRequestPermissionsResult(code:Int, permissions:Array<out String>, results:IntArray){
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==501){
            if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED)beginAudio()
            else CaptureState.status="Permiso de audio denegado"
        }
    }
    @Deprecated("Android compatibility result API")
    override fun onActivityResult(code:Int, result:Int, intent:Intent?){
        super.onActivityResult(code,result,intent)
        if(code!=502)return
        if(result!=RESULT_OK || intent==null){
            CaptureState.status="Captura no autorizada";return
        }
        startForegroundService(Intent(this,AudioCaptureService::class.java).apply{
            action=AudioCaptureService.START
            putExtra(AudioCaptureService.RESULT,result)
            putExtra(AudioCaptureService.CONSENT,intent)
        })
    }
    private fun stopAudio(){
        stopService(Intent(this,AudioCaptureService::class.java))
        CaptureState.status="Escucha detenida"
    }
    private fun orientation(o:Int){
        val landscape=o==Configuration.ORIENTATION_LANDSCAPE
        subtitle.textSize=if(landscape)14f else 16f
        subtitle.layoutParams.height=dp(if(landscape)30 else 40)
    }
    override fun onConfigurationChanged(config:Configuration){
        super.onConfigurationChanged(config);orientation(config.orientation)
    }
    override fun onSaveInstanceState(out:Bundle){
        web.saveState(out);super.onSaveInstanceState(out)
    }
    @Deprecated("Android WebView navigation")
    override fun onBackPressed(){
        if(customVideo!=null)hideVideo()
        else if(web.canGoBack())web.goBack()
        else super.onBackPressed()
    }
    override fun onDestroy(){
        handler.removeCallbacks(updateStatus)
        web.destroy()
        super.onDestroy()
    }
}
