#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_mucephei_app_ai_WhisperJni_nativeInit(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (!path) {
        return 0;
    }
    ALOGD("Initializing whisper context from %s", path);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);
    if (!ctx) {
        ALOGE("Failed to initialize whisper context");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_mucephei_app_ai_WhisperJni_nativeFree(JNIEnv *env, jobject thiz, jlong context_ptr) {
    struct whisper_context *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (ctx) {
        whisper_free(ctx);
        ALOGD("Whisper context freed");
    }
}

JNIEXPORT jstring JNICALL
Java_com_mucephei_app_ai_WhisperJni_nativeFullTranscribe(JNIEnv *env, jobject thiz, jlong context_ptr, jfloatArray samples, jint num_threads) {
    struct whisper_context *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    if (!ctx) {
        return env->NewStringUTF("");
    }

    jsize len = env->GetArrayLength(samples);
    jfloat *pcm_samples = env->GetFloatArrayElements(samples, nullptr);
    if (!pcm_samples) {
        return env->NewStringUTF("");
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = num_threads > 0 ? num_threads : 4;
    params.offset_ms = 0;

    ALOGD("Starting whisper transcription on %d samples with %d threads", len, params.n_threads);

    if (whisper_full(ctx, params, pcm_samples, len) != 0) {
        ALOGE("Failed to run whisper_full transcription");
        env->ReleaseFloatArrayElements(samples, pcm_samples, JNI_ABORT);
        return env->NewStringUTF("");
    }

    env->ReleaseFloatArrayElements(samples, pcm_samples, JNI_ABORT);

    std::string result_text = "";
    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text) {
            result_text += text;
        }
    }

    ALOGD("Transcription completed: %s", result_text.c_str());
    return env->NewStringUTF(result_text.c_str());
}

}
