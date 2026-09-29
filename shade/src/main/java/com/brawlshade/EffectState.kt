package com.brawlshade

data class EffectState(
    var enabled: Boolean = true,
    var bloom: Float = 0f,
    var bloomThreshold: Float = 0.72f,
    var sharpen: Float = 0f,
    var saturation: Float = 1f,
    var contrast: Float = 1f,
    var brightness: Float = 0f,
    var gamma: Float = 1f,
    var temperature: Float = 0f,
    var tint: Float = 0f,
    var vibrance: Float = 0f,
    var vignette: Float = 0f,
    var chromatic: Float = 0f,
    var rgbSplit: Float = 0f,
    var grain: Float = 0f,
    var scanlines: Float = 0f,
    var crt: Float = 0f,
    var colorize: Float = 0f,
    var hue: Float = 0f,
    var posterize: Float = 0f,
    var edgeGlow: Float = 0f,
    var invert: Float = 0f,
    var blueBoost: Float = 0f
) {
    fun reset() {
        enabled = true; bloom = 0f; bloomThreshold = 0.72f; sharpen = 0f
        saturation = 1f; contrast = 1f; brightness = 0f; gamma = 1f
        temperature = 0f; tint = 0f; vibrance = 0f; vignette = 0f
        chromatic = 0f; rgbSplit = 0f; grain = 0f; scanlines = 0f
        crt = 0f; colorize = 0f; hue = 0f; posterize = 0f
        edgeGlow = 0f; invert = 0f; blueBoost = 0f
    }
}
