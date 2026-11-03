package com.docscan.ui.remover.engine

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Precision Edge Refinement Engine.
 * Implements:
 * - Sub-pixel edge anti-aliasing & feathering
 * - Hair strand and pet fur detail preservation
 * - Halo removal (eliminates white halos and dark fringes)
 * - Color spill removal (neutralizes background color bleed on foreground edges)
 */
object EdgeRefiner {

    /**
     * Refines alpha mask boundary using edge-aware bilateral smoothing
     * and hair/fur strand preservation.
     */
    fun refineEdges(
        originalPixels: IntArray,
        maskBytes: ByteArray,
        width: Int,
        height: Int,
        isHairMode: Boolean = true,
        featherRadius: Int = 2
    ): ByteArray {
        val total = width * height
        val refinedMask = ByteArray(total)

        // 1. Identify boundary pixels (transition band: alpha between 10 and 245, or adjacent to 0/255)
        val isBoundary = BooleanArray(total)
        for (y in 1 until height - 1) {
            val rowOffset = y * width
            for (x in 1 until width - 1) {
                val idx = rowOffset + x
                val a = maskBytes[idx].toInt() and 0xFF
                if (a in 5..250) {
                    isBoundary[idx] = true
                } else {
                    // Check 4 neighbors
                    val aL = maskBytes[idx - 1].toInt() and 0xFF
                    val aR = maskBytes[idx + 1].toInt() and 0xFF
                    val aT = maskBytes[idx - width].toInt() and 0xFF
                    val aB = maskBytes[idx + width].toInt() and 0xFF
                    if ((a == 0 && (aL > 100 || aR > 100 || aT > 100 || aB > 100)) ||
                        (a == 255 && (aL < 150 || aR < 150 || aT < 150 || aB < 150))) {
                        isBoundary[idx] = true
                    }
                }
            }
        }

        // 2. Edge-aware refinement on boundary band
        val rad = if (isHairMode) featherRadius + 1 else featherRadius
        val sigmaColor = if (isHairMode) 35.0 else 50.0

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x
                if (!isBoundary[idx]) {
                    refinedMask[idx] = maskBytes[idx]
                    continue
                }

                val centerPixel = originalPixels[idx]
                val cr = Color.red(centerPixel)
                val cg = Color.green(centerPixel)
                val cb = Color.blue(centerPixel)

                var weightedAlphaSum = 0.0
                var weightSum = 0.0

                for (dy in -rad..rad) {
                    val ny = y + dy
                    if (ny !in 0 until height) continue
                    val nRow = ny * width
                    for (dx in -rad..rad) {
                        val nx = x + dx
                        if (nx !in 0 until width) continue
                        val nIdx = nRow + nx

                        val distSpatial = sqrt((dx * dx + dy * dy).toDouble())
                        if (distSpatial > rad) continue

                        val nPixel = originalPixels[nIdx]
                        val nr = Color.red(nPixel)
                        val ng = Color.green(nPixel)
                        val nb = Color.blue(nPixel)

                        // Color distance (Delta E)
                        val colorDist = sqrt(
                            ((cr - nr) * (cr - nr) +
                             (cg - ng) * (cg - ng) +
                             (cb - nb) * (cb - nb)).toDouble()
                        )

                        // Bilateral weight: spatial * range
                        val spatialWeight = 1.0 / (1.0 + distSpatial)
                        val rangeWeight = Math.exp(-(colorDist * colorDist) / (2 * sigmaColor * sigmaColor))
                        val weight = spatialWeight * rangeWeight

                        val nAlpha = maskBytes[nIdx].toInt() and 0xFF
                        weightedAlphaSum += nAlpha * weight
                        weightSum += weight
                    }
                }

                val finalAlpha = if (weightSum > 0.0001) {
                    (weightedAlphaSum / weightSum).toInt().coerceIn(0, 255)
                } else {
                    maskBytes[idx].toInt() and 0xFF
                }

                refinedMask[idx] = finalAlpha.toByte()
            }
        }

        return refinedMask
    }

    /**
     * De-halo filter and Color Spill Neutralization.
     * Prevents white fringes or dark halos around subjects by comparing boundary pixel
     * colors to definite foreground core and definite background samples.
     */
    fun removeHaloAndColorSpill(
        originalPixels: IntArray,
        refinedMask: ByteArray,
        width: Int,
        height: Int
    ): IntArray {
        val outputPixels = originalPixels.clone()
        val total = width * height

        for (y in 1 until height - 1) {
            val rowOffset = y * width
            for (x in 1 until width - 1) {
                val idx = rowOffset + x
                val alpha = refinedMask[idx].toInt() and 0xFF

                // Only touch boundary transition pixels
                if (alpha in 10..245) {
                    // Find nearest pure foreground pixel within small radius
                    var bestFgDist = 999.0
                    var bestFgColor = originalPixels[idx]

                    for (dy in -3..3) {
                        val ny = y + dy
                        if (ny !in 0 until height) continue
                        val nRow = ny * width
                        for (dx in -3..3) {
                            val nx = x + dx
                            if (nx !in 0 until width) continue
                            val nIdx = nRow + nx
                            val nAlpha = refinedMask[nIdx].toInt() and 0xFF

                            if (nAlpha >= 250) {
                                val d = sqrt((dx * dx + dy * dy).toDouble())
                                if (d < bestFgDist) {
                                    bestFgDist = d
                                    bestFgColor = originalPixels[nIdx]
                                }
                            }
                        }
                    }

                    if (bestFgDist < 999.0) {
                        // Blend edge RGB towards the nearest true foreground color
                        // to kill background color spill and white/dark halo
                        val curPixel = originalPixels[idx]
                        val curR = Color.red(curPixel)
                        val curG = Color.green(curPixel)
                        val curB = Color.blue(curPixel)

                        val fgR = Color.red(bestFgColor)
                        val fgG = Color.green(bestFgColor)
                        val fgB = Color.blue(bestFgColor)

                        // Edge blend factor based on alpha confidence
                        val blendFactor = (alpha / 255.0f).coerceIn(0.2f, 0.95f)
                        val cleanR = (curR * blendFactor + fgR * (1f - blendFactor)).toInt().coerceIn(0, 255)
                        val cleanG = (curG * blendFactor + fgG * (1f - blendFactor)).toInt().coerceIn(0, 255)
                        val cleanB = (curB * blendFactor + fgB * (1f - blendFactor)).toInt().coerceIn(0, 255)

                        outputPixels[idx] = Color.argb(alpha, cleanR, cleanG, cleanB)
                    } else {
                        val c = originalPixels[idx]
                        outputPixels[idx] = Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))
                    }
                } else if (alpha == 0) {
                    outputPixels[idx] = 0 // fully transparent
                } else {
                    val c = originalPixels[idx]
                    outputPixels[idx] = Color.argb(255, Color.red(c), Color.green(c), Color.blue(c))
                }
            }
        }

        return outputPixels
    }
}
