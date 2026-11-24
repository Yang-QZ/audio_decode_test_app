#include <jni.h>
#include <aaudio/AAudio.h>
#include <android/log.h>

#include <atomic>
#include <thread>

#define LOG_TAG "AAudioPlayer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct AAudioHandle {
    AAudioStream* stream = nullptr;
    std::thread writer;
    std::atomic<bool> running{false};
};

/**
 * 在独立线程里把整段 PCM 写入 AAudio 流。
 * data 由调用方 new[] 分配，线程结束后负责释放。
 */
static void writeLoop(AAudioHandle* handle, jbyte* data, jsize size) {
    if (handle == nullptr || handle->stream == nullptr) {
        delete[] data;
        return;
    }

    int32_t channels = AAudioStream_getChannelCount(handle->stream);
    if (channels <= 0) {
        channels = 1;
    }

    aaudio_result_t startResult = AAudioStream_requestStart(handle->stream);
    if (startResult != AAUDIO_OK) {
        LOGE("requestStart failed: %s", AAudio_convertResultToText(startResult));
        delete[] data;
        return;
    }

    const int32_t totalFrames = size / (static_cast<int32_t>(sizeof(int16_t)) * channels);
    int32_t written = 0;
    auto* samples = reinterpret_cast<const int16_t*>(data);

    while (handle->running && written < totalFrames) {
        int32_t chunk = totalFrames - written;
        aaudio_result_t result = AAudioStream_write(
                handle->stream,
                samples + static_cast<int64_t>(written) * channels,
                chunk,
                1000LL * 1000 * 1000);
        if (result < 0) {
            LOGE("write failed: %s", AAudio_convertResultToText(result));
            break;
        }
        written += result;
    }

    AAudioStream_requestStop(handle->stream);
    delete[] data;
}

static void stopWriter(AAudioHandle* handle) {
    if (handle == nullptr) {
        return;
    }
    handle->running = false;
    if (handle->stream != nullptr) {
        AAudioStream_requestStop(handle->stream);
    }
    if (handle->writer.joinable()) {
        handle->writer.join();
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_audiodecodetestapp_audio_AAudioEnginePlayer_nativeCreate(
        JNIEnv* /* env */, jclass /* clazz */, jint sampleRate, jint channels) {
    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) {
        LOGE("createStreamBuilder failed");
        return 0;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, channels);

    AAudioStream* stream = nullptr;
    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK || stream == nullptr) {
        LOGE("openStream failed: %s", AAudio_convertResultToText(result));
        return 0;
    }

    auto* handle = new AAudioHandle();
    handle->stream = stream;
    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audiodecodetestapp_audio_AAudioEnginePlayer_nativeStart(
        JNIEnv* env, jclass /* clazz */, jlong ptr, jbyteArray pcm) {
    auto* handle = reinterpret_cast<AAudioHandle*>(ptr);
    if (handle == nullptr || pcm == nullptr) {
        return;
    }

    jsize size = env->GetArrayLength(pcm);
    if (size <= 0) {
        return;
    }

    // 上一次播放若尚未结束，先安全收尾。
    stopWriter(handle);

    auto* copy = new jbyte[size];
    env->GetByteArrayRegion(pcm, 0, size, copy);

    handle->running = true;
    handle->writer = std::thread(writeLoop, handle, copy, size);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audiodecodetestapp_audio_AAudioEnginePlayer_nativeStop(
        JNIEnv* /* env */, jclass /* clazz */, jlong ptr) {
    stopWriter(reinterpret_cast<AAudioHandle*>(ptr));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audiodecodetestapp_audio_AAudioEnginePlayer_nativeDestroy(
        JNIEnv* /* env */, jclass /* clazz */, jlong ptr) {
    auto* handle = reinterpret_cast<AAudioHandle*>(ptr);
    if (handle == nullptr) {
        return;
    }

    stopWriter(handle);
    if (handle->stream != nullptr) {
        AAudioStream_close(handle->stream);
        handle->stream = nullptr;
    }
    delete handle;
}
