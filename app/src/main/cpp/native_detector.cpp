#include <jni.h>
#include <algorithm>
#include <cmath>
#include <cstdint>

namespace {

constexpr int kMaxDetections = 64;
constexpr int kFloatsPerDetection = 6;
constexpr int kSampleStep = 2;
constexpr int kMaxGridW = 320;
constexpr int kMaxGridH = 144;
constexpr int kMaxCells = kMaxGridW * kMaxGridH;

thread_local uint8_t gMask[kMaxCells];
thread_local uint8_t gVisited[kMaxCells];
thread_local int gQueue[kMaxCells];

enum DetectionType {
    kPlayer = 1,
    kEnemy = 2,
    kProjectile = 3,
    kArea = 4
};

inline bool isRed(uint8_t r, uint8_t g, uint8_t b) {
    return r > 145 &&
           static_cast<float>(r) > static_cast<float>(g) * 1.35f &&
           static_cast<float>(r) > static_cast<float>(b) * 1.35f &&
           (static_cast<int>(r) - static_cast<int>(g)) > 32;
}

inline bool isFriendly(uint8_t r, uint8_t g, uint8_t b) {
    const int chroma = std::max({r, g, b}) - std::min({r, g, b});
    return chroma > 30 &&
           g > 100 &&
           b > 90 &&
           r < 155 &&
           static_cast<int>(g) + static_cast<int>(b) > static_cast<int>(r) * 2;
}

inline bool isOrange(uint8_t r, uint8_t g, uint8_t b) {
    return r > 165 &&
           g > 92 &&
           g < 215 &&
           b < 125 &&
           static_cast<int>(r) - static_cast<int>(g) > 40;
}

inline void appendDetection(
    float* out,
    int& count,
    int type,
    float confidence,
    float left,
    float top,
    float right,
    float bottom
) {
    if (count >= kMaxDetections) return;

    const int base = count * kFloatsPerDetection;
    out[base + 0] = static_cast<float>(type);
    out[base + 1] = std::clamp(confidence, 0.05f, 0.99f);
    out[base + 2] = left;
    out[base + 3] = top;
    out[base + 4] = right;
    out[base + 5] = bottom;
    ++count;
}

template <typename Matcher>
void scanColor(
    const uint8_t* rgba,
    int width,
    int height,
    int rowStride,
    Matcher matcher,
    int mode,
    float* out,
    int& count
) {
    const int gridW = std::min(kMaxGridW, (width + kSampleStep - 1) / kSampleStep);
    const int gridH = std::min(kMaxGridH, (height + kSampleStep - 1) / kSampleStep);
    const int cells = gridW * gridH;

    std::fill(gVisited, gVisited + cells, 0);
    std::fill(gMask, gMask + cells, 0);

    const int playTop = static_cast<int>(height * 0.06f);
    const int playBottom = static_cast<int>(height * 0.84f);
    const int playLeft = static_cast<int>(width * 0.02f);
    const int playRight = static_cast<int>(width * 0.98f);

    for (int gy = 0; gy < gridH; ++gy) {
        const int y = std::min(height - 1, gy * kSampleStep + kSampleStep / 2);
        if (y < playTop || y >= playBottom) continue;

        const uint8_t* row = rgba + static_cast<size_t>(y) * static_cast<size_t>(rowStride);
        for (int gx = 0; gx < gridW; ++gx) {
            const int x = std::min(width - 1, gx * kSampleStep + kSampleStep / 2);
            if (x < playLeft || x >= playRight) continue;

            const uint8_t* px = row + static_cast<size_t>(x) * 4u;
            if (matcher(px[0], px[1], px[2])) {
                gMask[gy * gridW + gx] = 1;
            }
        }
    }

    float bestPlayerScore = -1.0f;
    int bestL = 0;
    int bestT = 0;
    int bestR = 0;
    int bestB = 0;

    for (int gy = 0; gy < gridH; ++gy) {
        for (int gx = 0; gx < gridW; ++gx) {
            const int root = gy * gridW + gx;
            if (!gMask[root] || gVisited[root]) continue;

            int head = 0;
            int tail = 0;
            gQueue[tail++] = root;
            gVisited[root] = 1;

            int minX = gx;
            int maxX = gx;
            int minY = gy;
            int maxY = gy;
            int area = 0;

            while (head < tail) {
                const int idx = gQueue[head++];
                const int cx = idx % gridW;
                const int cy = idx / gridW;

                minX = std::min(minX, cx);
                maxX = std::max(maxX, cx);
                minY = std::min(minY, cy);
                maxY = std::max(maxY, cy);
                ++area;

                const int neighbors[4] = {
                    idx - 1,
                    idx + 1,
                    idx - gridW,
                    idx + gridW
                };

                for (int n = 0; n < 4; ++n) {
                    const int ni = neighbors[n];
                    if (ni < 0 || ni >= cells) continue;
                    if (n == 0 && cx == 0) continue;
                    if (n == 1 && cx == gridW - 1) continue;
                    if (n == 2 && cy == 0) continue;
                    if (n == 3 && cy == gridH - 1) continue;

                    if (gMask[ni] && !gVisited[ni]) {
                        gVisited[ni] = 1;
                        gQueue[tail++] = ni;
                    }
                }
            }

            if (area < 2) continue;

            const int left = minX * kSampleStep;
            const int top = minY * kSampleStep;
            const int right = std::min(width, (maxX + 1) * kSampleStep);
            const int bottom = std::min(height, (maxY + 1) * kSampleStep);
            const int bw = std::max(1, right - left);
            const int bh = std::max(1, bottom - top);
            const float aspect = static_cast<float>(bw) / static_cast<float>(bh);

            if (mode == 1) {
                if (area > 900 || bw > width / 2 || bh > height / 2) continue;

                const float cx = (left + right) * 0.5f / width;
                const float cy = (top + bottom) * 0.5f / height;
                const float centerDistance = std::sqrt(
                    (cx - 0.5f) * (cx - 0.5f) +
                    (cy - 0.5f) * (cy - 0.5f)
                );
                const float compactArea = static_cast<float>(std::min(area, 400));
                const float score = compactArea / (1.0f + centerDistance * 4.0f);

                if (score > bestPlayerScore) {
                    bestPlayerScore = score;
                    bestL = left;
                    bestT = top;
                    bestR = right;
                    bestB = bottom;
                }
                continue;
            }

            if (mode == 0) {
                if (aspect >= 3.0f && bh <= 10) {
                    const float padX = std::max(5.0f, bw * 0.15f);
                    const float estimateHeight = std::max(26.0f, bw * 0.55f);
                    appendDetection(
                        out,
                        count,
                        kEnemy,
                        0.58f + std::min(0.35f, area / 120.0f),
                        std::max(0.0f, left - padX),
                        static_cast<float>(top),
                        std::min(static_cast<float>(width), right + padX),
                        std::min(static_cast<float>(height), top + estimateHeight)
                    );
                } else if (area >= 20 || std::max(bw, bh) >= 11) {
                    appendDetection(
                        out,
                        count,
                        kEnemy,
                        0.52f + std::min(0.32f, area / 180.0f),
                        static_cast<float>(left),
                        static_cast<float>(top),
                        static_cast<float>(right),
                        static_cast<float>(bottom)
                    );
                } else {
                    appendDetection(
                        out,
                        count,
                        kProjectile,
                        0.42f + std::min(0.38f, area / 70.0f),
                        static_cast<float>(left),
                        static_cast<float>(top),
                        static_cast<float>(right),
                        static_cast<float>(bottom)
                    );
                }
            } else if (mode == 2) {
                if (area >= 20 && aspect >= 0.35f && aspect <= 2.8f) {
                    appendDetection(
                        out,
                        count,
                        kArea,
                        0.45f + std::min(0.35f, area / 260.0f),
                        static_cast<float>(left),
                        static_cast<float>(top),
                        static_cast<float>(right),
                        static_cast<float>(bottom)
                    );
                }
            }
        }
    }

    if (mode == 1 && bestPlayerScore > 0.0f) {
        appendDetection(
            out,
            count,
            kPlayer,
            0.55f + std::min(0.35f, bestPlayerScore / 180.0f),
            static_cast<float>(bestL),
            static_cast<float>(bestT),
            static_cast<float>(bestR),
            static_cast<float>(bestB)
        );
    }
}

}  // namespace

extern "C"
JNIEXPORT jint JNICALL
Java_com_brawlbrain_rt_m1_NativeDetector_analyze(
    JNIEnv* env,
    jclass,
    jobject rgbaBuffer,
    jint width,
    jint height,
    jint rowStride,
    jobject outputBuffer
) {
    if (rgbaBuffer == nullptr || outputBuffer == nullptr) return 0;
    if (width <= 0 || height <= 0 || rowStride < width * 4) return 0;
    if (width > kMaxGridW * kSampleStep || height > kMaxGridH * kSampleStep) return 0;

    auto* rgba = static_cast<uint8_t*>(env->GetDirectBufferAddress(rgbaBuffer));
    auto* out = static_cast<float*>(env->GetDirectBufferAddress(outputBuffer));
    const jlong outCapacity = env->GetDirectBufferCapacity(outputBuffer);

    if (rgba == nullptr || out == nullptr) return 0;
    if (outCapacity < static_cast<jlong>(kMaxDetections * kFloatsPerDetection * sizeof(float))) {
        return 0;
    }

    int count = 0;

    scanColor(
        rgba,
        width,
        height,
        rowStride,
        [](uint8_t r, uint8_t g, uint8_t b) {
            return isRed(r, g, b);
        },
        0,
        out,
        count
    );

    if (count < kMaxDetections) {
        scanColor(
            rgba,
            width,
            height,
            rowStride,
            [](uint8_t r, uint8_t g, uint8_t b) {
                return isFriendly(r, g, b);
            },
            1,
            out,
            count
        );
    }

    if (count < kMaxDetections) {
        scanColor(
            rgba,
            width,
            height,
            rowStride,
            [](uint8_t r, uint8_t g, uint8_t b) {
                return isOrange(r, g, b);
            },
            2,
            out,
            count
        );
    }

    return count;
}
