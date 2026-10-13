package cl.bonvallarte.multitraductor.audio

import android.content.Context
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.ConcurrentHashMap

/** ML Kit language ID and translation run locally. Translation models need initial Wi-Fi download. */
class OnDeviceTranslator(private val context:Context) {
    private val detector=LanguageIdentification.getClient()
    private val translators=ConcurrentHashMap<String,Translator>()

    fun translate(text:String, done:(String,String)->Unit) {
        if(text.isBlank()){done("","Sin habla");return}
        detector.identifyLanguage(text).addOnSuccessListener{rawLanguage->
            val code=rawLanguage.substringBefore('-')
            val source=TranslateLanguage.fromLanguageTag(code)
            if(code=="es"){done(text,"es → es");return@addOnSuccessListener}
            if(source==null || code=="und"){
                done(text,"Idioma no detectado; texto original")
                return@addOnSuccessListener
            }
            val client=translators.getOrPut(code){
                Translation.getClient(TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(TranslateLanguage.SPANISH)
                    .build())
            }
            // Only Wi-Fi model installation: no unapproved mobile data.
            client.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build())
                .addOnSuccessListener{
                    client.translate(text)
                        .addOnSuccessListener{result->done(result,code+" → es")}
                        .addOnFailureListener{done(text,"Error de traducción: "+it.javaClass.simpleName)}
                }
                .addOnFailureListener{
                    done(text,"Modelo de idioma pendiente (Wi-Fi): "+code)
                }
        }.addOnFailureListener {done(text,"No se pudo detectar el idioma")}
    }
}
