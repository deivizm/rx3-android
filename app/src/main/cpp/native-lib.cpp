// =============================================================================
//  RX3 Fase 1 — "player falso" nativo
//
//  Valida la tubería que luego usarán los shims del rbp real:
//    - Render de un framebuffer 1280x800 a un ANativeWindow (lo que hará fbshim)
//    - Tono de audio por Oboe -> AAudio (lo que hará audioshim)
//
//  Nada de esto usa el firmware de Pioneer. Es un banco de pruebas.
// =============================================================================
#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>

#include <oboe/Oboe.h>

#include <cmath>
#include <cstdint>
#include <mutex>
#include <atomic>

#define LOG_TAG "rx3native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Resolución nativa del XDJ-RX3. El rbp renderiza a esto; el SurfaceView escala.
static constexpr int32_t kFbWidth = 1280;
static constexpr int32_t kFbHeight = 800;

// ------------------------------- Pantalla -----------------------------------
static std::mutex gWindowMutex;
static ANativeWindow *gWindow = nullptr;

static inline uint32_t packRGBX(uint8_t r, uint8_t g, uint8_t b) {
    // ANativeWindow en RGBX_8888: byte0=R, byte1=G, byte2=B, byte3=X (little-endian)
    return (0xFFu << 24) | (static_cast<uint32_t>(b) << 16) |
           (static_cast<uint32_t>(g) << 8) | static_cast<uint32_t>(r);
}

// Dibuja un patrón de prueba: barras de color estilo SMPTE + una caja que se
// mueve, para ver a ojo que el refresco llega entero y sin tearing.
static void drawTestPattern(uint32_t *pixels, int32_t stride, int64_t frame) {
    static const uint32_t bars[8] = {
            packRGBX(255, 255, 255), packRGBX(255, 255, 0),
            packRGBX(0, 255, 255),   packRGBX(0, 255, 0),
            packRGBX(255, 0, 255),   packRGBX(255, 0, 0),
            packRGBX(0, 0, 255),     packRGBX(20, 20, 20),
    };
    const int barW = kFbWidth / 8;

    for (int y = 0; y < kFbHeight; ++y) {
        uint32_t *row = pixels + static_cast<int64_t>(y) * stride;
        for (int x = 0; x < kFbWidth; ++x) {
            row[x] = bars[(x / barW) & 7];
        }
    }

    // Caja de 120x120 que recorre la pantalla en horizontal.
    const int boxSize = 120;
    int bx = static_cast<int>((frame * 6) % (kFbWidth - boxSize));
    int by = (kFbHeight - boxSize) / 2;
    for (int y = by; y < by + boxSize; ++y) {
        uint32_t *row = pixels + static_cast<int64_t>(y) * stride;
        for (int x = bx; x < bx + boxSize; ++x) {
            row[x] = packRGBX(255, 128, 0);
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_rx3android_player_NativeBridge_setSurface(JNIEnv *env, jobject, jobject surface) {
    std::lock_guard<std::mutex> lock(gWindowMutex);
    if (gWindow) {
        ANativeWindow_release(gWindow);
        gWindow = nullptr;
    }
    if (surface) {
        gWindow = ANativeWindow_fromSurface(env, surface);
        if (gWindow) {
            ANativeWindow_setBuffersGeometry(gWindow, kFbWidth, kFbHeight,
                                             WINDOW_FORMAT_RGBX_8888);
            LOGI("Surface fijado, buffer %dx%d RGBX_8888", kFbWidth, kFbHeight);
        } else {
            LOGE("ANativeWindow_fromSurface devolvio null");
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_rx3android_player_NativeBridge_clearSurface(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gWindowMutex);
    if (gWindow) {
        ANativeWindow_release(gWindow);
        gWindow = nullptr;
        LOGI("Surface liberado");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_rx3android_player_NativeBridge_renderFrame(JNIEnv *, jobject, jlong frame) {
    std::lock_guard<std::mutex> lock(gWindowMutex);
    if (!gWindow) return;

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(gWindow, &buffer, nullptr) != 0) {
        return;
    }
    if (buffer.format == WINDOW_FORMAT_RGBX_8888 ||
        buffer.format == WINDOW_FORMAT_RGBA_8888) {
        drawTestPattern(static_cast<uint32_t *>(buffer.bits), buffer.stride,
                        static_cast<int64_t>(frame));
    }
    ANativeWindow_unlockAndPost(gWindow);
}

// -------------------------------- Audio -------------------------------------
// Tono senoidal continuo por Oboe. Esto es lo que audioshim sustituira por el
// PCM real del rbp.
class ToneEngine : public oboe::AudioStreamDataCallback {
public:
    oboe::Result start() {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Exclusive)
                ->setFormat(oboe::AudioFormat::Float)
                ->setChannelCount(oboe::ChannelCount::Stereo)
                ->setDataCallback(this);
        oboe::Result r = builder.openStream(mStream);
        if (r != oboe::Result::OK) {
            LOGE("Oboe openStream fallo: %s", oboe::convertToText(r));
            return r;
        }
        mSampleRate = mStream->getSampleRate();
        mPhaseIncrement = 2.0 * M_PI * mFrequency / static_cast<double>(mSampleRate);
        r = mStream->requestStart();
        LOGI("Oboe stream iniciado: %d Hz, %d canales, sr=%d",
             (int) mFrequency, mStream->getChannelCount(), mSampleRate);
        return r;
    }

    void stop() {
        if (mStream) {
            mStream->requestStop();
            mStream->close();
            mStream.reset();
            LOGI("Oboe stream parado");
        }
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData,
                                          int32_t numFrames) override {
        auto *out = static_cast<float *>(audioData);
        int channels = stream->getChannelCount();
        for (int i = 0; i < numFrames; ++i) {
            auto sample = static_cast<float>(std::sin(mPhase) * 0.2);
            mPhase += mPhaseIncrement;
            if (mPhase > 2.0 * M_PI) mPhase -= 2.0 * M_PI;
            for (int c = 0; c < channels; ++c) {
                out[i * channels + c] = sample;
            }
        }
        return oboe::DataCallbackResult::Continue;
    }

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    double mFrequency = 440.0;
    double mPhase = 0.0;
    double mPhaseIncrement = 0.0;
    int32_t mSampleRate = 48000;
};

static ToneEngine gTone;

extern "C" JNIEXPORT void JNICALL
Java_com_rx3android_player_NativeBridge_startTone(JNIEnv *, jobject) {
    gTone.start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_rx3android_player_NativeBridge_stopTone(JNIEnv *, jobject) {
    gTone.stop();
}
