package com.brawlshade

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class ReShadeView(
    context: Context,
    private val onInputSurfaceReady: (Surface) -> Unit
) : GLSurfaceView(context) {

    private val rendererImpl = ShaderRenderer(onInputSurfaceReady)

    init {
        setEGLContextClientVersion(2)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setRenderer(rendererImpl)
        renderMode = RENDERMODE_CONTINUOUSLY
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    fun updateState(state: EffectState) {
        rendererImpl.state = state.copy()
    }

    fun releaseInput() {
        queueEvent { rendererImpl.releaseInput() }
    }

    private class ShaderRenderer(
        private val onInputSurfaceReady: (Surface) -> Unit
    ) : Renderer {

        @Volatile
        var state = EffectState()

        private var program = 0
        private var oesTex = 0
        private var inputTexture: SurfaceTexture? = null
        private var inputSurface: Surface? = null
        private val texTransform = FloatArray(16)

        private val verts: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(floatArrayOf(
                    -1f, -1f, 0f, 0f,
                     1f, -1f, 1f, 0f,
                    -1f,  1f, 0f, 1f,
                     1f,  1f, 1f, 1f
                ))
                position(0)
            }

        override fun onSurfaceCreated(
            gl: javax.microedition.khronos.opengles.GL10?,
            config: javax.microedition.khronos.egl.EGLConfig?
        ) {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            oesTex = genExternalTexture()
            inputTexture = SurfaceTexture(oesTex)
            inputSurface = Surface(inputTexture)
            onInputSurfaceReady(inputSurface!!)
        }

        override fun onSurfaceChanged(
            gl: javax.microedition.khronos.opengles.GL10?,
            width: Int,
            height: Int
        ) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
            val tex = inputTexture
            if (tex == null) {
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                return
            }

            try {
                tex.updateTexImage()
                tex.getTransformMatrix(texTransform)
            } catch (_: Throwable) {
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                return
            }

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            val pos = GLES20.glGetAttribLocation(program, "aPos")
            val uv = GLES20.glGetAttribLocation(program, "aUv")
            val mat = GLES20.glGetUniformLocation(program, "uTexMatrix")
            val timeLoc = GLES20.glGetUniformLocation(program, "uTime")
            val resLoc = GLES20.glGetUniformLocation(program, "uResolution")

            GLES20.glEnableVertexAttribArray(pos)
            GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, verts)
            verts.position(2)
            GLES20.glEnableVertexAttribArray(uv)
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, verts)

            GLES20.glUniformMatrix4fv(mat, 1, false, texTransform, 0)
            GLES20.glUniform1f(timeLoc, (System.nanoTime() % 10000000000L) / 1000000000f)
            GLES20.glUniform2f(resLoc, width.toFloat().coerceAtLeast(1f), height.toFloat().coerceAtLeast(1f))

            bindFloats(program, state)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(pos)
            GLES20.glDisableVertexAttribArray(uv)
        }

        private fun bindFloats(p: Int, s: EffectState) {
            fun f(name: String, value: Float) =
                GLES20.glUniform1f(GLES20.glGetUniformLocation(p, name), value)
            f("uEnabled", if (s.enabled) 1f else 0f)
            f("uBloom", s.bloom); f("uBloomThreshold", s.bloomThreshold); f("uSharpen", s.sharpen)
            f("uSaturation", s.saturation); f("uContrast", s.contrast); f("uBrightness", s.brightness)
            f("uGamma", s.gamma); f("uTemperature", s.temperature); f("uTint", s.tint)
            f("uVibrance", s.vibrance); f("uVignette", s.vignette); f("uChromatic", s.chromatic)
            f("uRgbSplit", s.rgbSplit); f("uGrain", s.grain); f("uScanlines", s.scanlines)
            f("uCrt", s.crt); f("uColorize", s.colorize); f("uHue", s.hue)
            f("uPosterize", s.posterize); f("uEdgeGlow", s.edgeGlow); f("uInvert", s.invert)
            f("uBlueBoost", s.blueBoost)
        }

        fun releaseInput() {
            try { inputSurface?.release() } catch (_: Throwable) {}
            inputSurface = null
            try { inputTexture?.release() } catch (_: Throwable) {}
            inputTexture = null
            if (oesTex != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(oesTex), 0)
                oesTex = 0
            }
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }

        private fun genExternalTexture(): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return ids[0]
        }

        private fun compile(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw IllegalStateException(log)
            }
            return shader
        }

        private fun linkProgram(vs: String, fs: String): Int {
            val v = compile(GLES20.GL_VERTEX_SHADER, vs)
            val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, v)
            GLES20.glAttachShader(p, f)
            GLES20.glLinkProgram(p)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(p)
                GLES20.glDeleteProgram(p)
                GLES20.glDeleteShader(v)
                GLES20.glDeleteShader(f)
                throw IllegalStateException(log)
            }
            GLES20.glDeleteShader(v)
            GLES20.glDeleteShader(f)
            return p
        }

        companion object {
            private const val VERTEX_SHADER = """
                attribute vec2 aPos;
                attribute vec2 aUv;
                varying vec2 vUv;
                uniform mat4 uTexMatrix;
                void main() {
                    gl_Position = vec4(aPos, 0.0, 1.0);
                    vUv = (uTexMatrix * vec4(aUv, 0.0, 1.0)).xy;
                }
            """

            private const val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision highp float;

                varying vec2 vUv;
                uniform samplerExternalOES uTex;
                uniform vec2 uResolution;
                uniform float uTime;

                uniform float uEnabled;
                uniform float uBloom;
                uniform float uBloomThreshold;
                uniform float uSharpen;
                uniform float uSaturation;
                uniform float uContrast;
                uniform float uBrightness;
                uniform float uGamma;
                uniform float uTemperature;
                uniform float uTint;
                uniform float uVibrance;
                uniform float uVignette;
                uniform float uChromatic;
                uniform float uRgbSplit;
                uniform float uGrain;
                uniform float uScanlines;
                uniform float uCrt;
                uniform float uColorize;
                uniform float uHue;
                uniform float uPosterize;
                uniform float uEdgeGlow;
                uniform float uInvert;
                uniform float uBlueBoost;

                vec3 sampleRgb(vec2 uv) {
                    return texture2D(uTex, clamp(uv, 0.0, 1.0)).rgb;
                }

                float lum(vec3 c) {
                    return dot(c, vec3(0.2126, 0.7152, 0.0722));
                }

                float hash(vec2 p) {
                    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
                }

                vec3 hueRotate(vec3 c, float a) {
                    float ca = cos(a);
                    float sa = sin(a);
                    vec3 axis = vec3(0.299, 0.587, 0.114);
                    return clamp(c * ca + cross(axis, c) * sa + axis * dot(axis, c) * (1.0 - ca), 0.0, 1.0);
                }

                void main() {
                    vec2 uv = vUv;
                    vec2 px = 1.0 / max(uResolution, vec2(1.0));

                    if (uCrt > 0.001) {
                        vec2 q = uv * 2.0 - 1.0;
                        float r = dot(q, q);
                        q *= 1.0 + r * 0.018 * uCrt;
                        uv = q * 0.5 + 0.5;
                    }

                    vec3 c = sampleRgb(uv);

                    if (uEnabled < 0.5) {
                        gl_FragColor = vec4(c, 1.0);
                        return;
                    }

                    if (uChromatic > 0.001) {
                        vec2 d = (uv - 0.5) * uChromatic * 0.012;
                        c.r = sampleRgb(uv + d).r;
                        c.b = sampleRgb(uv - d).b;
                    }

                    if (uRgbSplit > 0.001) {
                        float d = uRgbSplit * 0.010;
                        c.r = sampleRgb(uv + vec2(d, 0.0)).r;
                        c.b = sampleRgb(uv - vec2(d, 0.0)).b;
                    }

                    if (uSharpen > 0.001) {
                        vec3 n = sampleRgb(uv + vec2(0.0, px.y));
                        vec3 s = sampleRgb(uv - vec2(0.0, px.y));
                        vec3 e = sampleRgb(uv + vec2(px.x, 0.0));
                        vec3 w = sampleRgb(uv - vec2(px.x, 0.0));
                        vec3 sharp = c * 5.0 - n - s - e - w;
                        c = mix(c, sharp, uSharpen);
                    }

                    if (uBloom > 0.001) {
                        vec3 b = vec3(0.0);
                        float weights[4];
                        weights[0] = 0.10; weights[1] = 0.07; weights[2] = 0.045; weights[3] = 0.025;

                        for (int i = 1; i <= 4; i++) {
                            float d = float(i) * 3.0;
                            vec3 a = sampleRgb(uv + vec2(px.x * d, 0.0));
                            vec3 d1 = sampleRgb(uv - vec2(px.x * d, 0.0));
                            vec3 e = sampleRgb(uv + vec2(0.0, px.y * d));
                            vec3 f = sampleRgb(uv - vec2(0.0, px.y * d));
                            float w = weights[i - 1];
                            b += max(lum(a) - uBloomThreshold, 0.0) * a * w;
                            b += max(lum(d1) - uBloomThreshold, 0.0) * d1 * w;
                            b += max(lum(e) - uBloomThreshold, 0.0) * e * w;
                            b += max(lum(f) - uBloomThreshold, 0.0) * f * w;
                        }
                        c += b * uBloom * 7.0;
                    }

                    if (uEdgeGlow > 0.001) {
                        vec3 n = sampleRgb(uv + vec2(0.0, px.y));
                        vec3 e = sampleRgb(uv + vec2(px.x, 0.0));
                        vec3 edge = abs(c - n) + abs(c - e);
                        c += edge * uEdgeGlow * 1.5;
                    }

                    c += uBrightness;
                    c = (c - 0.5) * uContrast + 0.5;

                    float l = lum(c);
                    c = mix(vec3(l), c, uSaturation);
                    c = mix(c, c + (c - vec3(l)) * 1.7, uVibrance);

                    c.r += uTemperature * 0.08;
                    c.b -= uTemperature * 0.08;
                    c.g += uTint * 0.035;
                    c.b += uBlueBoost * 0.08;

                    c = hueRotate(c, uHue);
                    c = pow(max(c, 0.0), vec3(1.0 / max(uGamma, 0.1)));

                    if (uColorize > 0.001) {
                        vec3 tintColor = vec3(0.66, 0.35, 1.0);
                        c = mix(c, tintColor * (0.35 + l), uColorize);
                    }

                    if (uPosterize > 0.001) {
                        float levels = mix(64.0, 4.0, uPosterize);
                        c = floor(c * levels + 0.5) / levels;
                    }

                    if (uInvert > 0.001) {
                        c = mix(c, 1.0 - c, uInvert);
                    }

                    if (uVignette > 0.001) {
                        vec2 p = uv - 0.5;
                        float vig = smoothstep(0.15, 0.85, dot(p, p) * 1.65);
                        c *= 1.0 - vig * uVignette * 0.75;
                    }

                    if (uGrain > 0.001) {
                        float n = hash(uv * uResolution + uTime * 60.0) - 0.5;
                        c += n * uGrain * 0.09;
                    }

                    if (uScanlines > 0.001) {
                        float line = 0.5 + 0.5 * sin(uv.y * uResolution.y * 3.14159);
                        c *= 1.0 - line * uScanlines * 0.10;
                    }

                    if (uCrt > 0.001) {
                        float scan = 0.5 + 0.5 * sin(uv.y * uResolution.y * 1.25);
                        c *= 1.0 - scan * uCrt * 0.08;
                    }

                    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
                }
            """
        }
    }
}
