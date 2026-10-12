#include <jni.h>
#include <android/log.h>
#include <whisper.h>
#include <algorithm>
#include <cstdint>
#include <string>

#define WH_TAG "MT_WHISPER"
#define WHLOG(...) __android_log_print(ANDROID_LOG_INFO, WH_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_cl_bonvallarte_multitraductor_audio_WhisperNative_load(
        JNIEnv * env, jobject, jstring path) {
    if (!path) return 0;
    const char * text = env->GetStringUTFChars(path, nullptr);
    if (!text) return 0;
    auto params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context * ctx = whisper_init_from_file_with_params(text, params);
    env->ReleaseStringUTFChars(path, text);
    WHLOG("whisper init %s", ctx ? "ok" : "failed");
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_cl_bonvallarte_multitraductor_audio_WhisperNative_transcribe(
        JNIEnv * env, jobject, jlong context, jfloatArray pcm) {
    whisper_context * ctx = reinterpret_cast<whisper_context*>(context);
    if (!ctx || !pcm) return env->NewStringUTF("");
    const jsize count = env->GetArrayLength(pcm);
    if (count < 16000 || count > 16000 * 30) return env->NewStringUTF("");
    jfloat * audio = env->GetFloatArrayElements(pcm, nullptr);
    if (!audio) return env->NewStringUTF("");
    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = 4;
    params.audio_ctx = 512;
    params.max_tokens = 64;
    params.no_timestamps = true;
    params.single_segment = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = true;
    params.language = "auto";
    params.suppress_blank = true;
    params.suppress_nst = true;
    params.greedy.best_of = 1;
    params.temperature_inc = 0.0f;
    WHLOG("ASR chunk starting: %d samples, 4 threads, ctx=512", int(count));
    const int rc = whisper_full(ctx, params, audio, count);
    WHLOG("ASR whisper_full returned: %d", rc);
    env->ReleaseFloatArrayElements(pcm, audio, JNI_ABORT);
    if (rc != 0) {
        WHLOG("whisper_full failed: %d", rc);
        return env->NewStringUTF("");
    }
    std::string output;
    const int n = whisper_full_n_segments(ctx);
    for (int i=0;i<n;i++) {
        const char * part = whisper_full_get_segment_text(ctx,i);
        if(part){ if(!output.empty()) output += " "; output += part; }
    }
    WHLOG("transcription decoded %d segments, %zu bytes",n,output.size());
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_cl_bonvallarte_multitraductor_audio_WhisperNative_unload(
        JNIEnv *, jobject, jlong context) {
    if (context) whisper_free(reinterpret_cast<whisper_context*>(context));
}
