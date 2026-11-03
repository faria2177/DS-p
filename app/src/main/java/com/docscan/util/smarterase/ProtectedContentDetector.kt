package com.docscan.util.smarterase

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.docscan.util.TextRecognizerHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ProtectedRegion(
    val bounds: Rect,
    val text: String,
    val isPrintedText: Boolean = true
)

data class DocumentLine(
    val isHorizontal: Boolean,
    val coordinate: Int, // y for horizontal, x for vertical
    val start: Int,
    val end: Int,
    val thickness: Int,
    val color: Int
)

/**
 * Protected Content Detector: Detects printed text bounding boxes and structural borders
 * to prevent accidental destruction of vital document text.
 */
object ProtectedContentDetector {

    /**
     * Extracts protected printed text regions from the bitmap using OCR.
     */
    suspend fun detectProtectedTextRegions(bitmap: Bitmap): List<ProtectedRegion> {
        val blocks = TextRecognizerHelper.extractTextWithBlocks(bitmap)
        return blocks.map { block ->
            ProtectedRegion(
                bounds = block.rect,
                text = block.text,
                isPrintedText = true
            )
        }
    }

    /**
     * Checks if a given mask intersects with any protected text region.
     */
    fun checkMaskOverlap(maskBounds: Rect, protectedRegions: List<ProtectedRegion>): List<ProtectedRegion> {
        return protectedRegions.filter { region ->
            Rect.intersects(maskBounds, region.bounds)
        }
    }
}

/**
 * Document Structure Detector: Identifies form lines, table lines, ruled lines, and grids
 * crossing through the erased region, enabling Level 3 line reconstruction.
 */
object DocumentStructureDetector {

    /**
     * Finds structural lines (horizontal and vertical) passing through a target bounding region.
     */
    fun detectCrossingLines(bitmap: Bitmap, searchArea: Rect): List<DocumentLine> {
        val w = bitmap.width
        val h = bitmap.height
        val lines = mutableListOf<DocumentLine>()

        val x0 = max(0, searchArea.left - 20)
        val x1 = min(w - 1, searchArea.right + 20)
        val y0 = max(0, searchArea.top - 20)
        val y1 = min(h - 1, searchArea.bottom + 20)

        // 1. Search for Horizontal lines crossing through this region
        for (y in y0..y1 step 2) {
            var leftDark = false
            var rightDark = false

            // Check if line exists to the left of the mask
            if (searchArea.left > 10) {
                var darkCount = 0
                for (x in max(0, searchArea.left - 25) until searchArea.left) {
                    val pixel = bitmap.getPixel(x, y)
                    val lum = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    if (lum < 160) darkCount++
                }
                leftDark = darkCount >= 10
            }

            // Check if line exists to the right of the mask
            if (searchArea.right < w - 10) {
                var darkCount = 0
                for (x in searchArea.right until min(w, searchArea.right + 25)) {
                    val pixel = bitmap.getPixel(x, y)
                    val lum = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    if (lum < 160) darkCount++
                }
                rightDark = darkCount >= 10
            }

            // If line exists on both sides of the mask, it is a crossing line!
            if (leftDark && rightDark) {
                // Sample line color
                val samplePixel = bitmap.getPixel(max(0, searchArea.left - 5), y)
                lines.add(
                    DocumentLine(
                        isHorizontal = true,
                        coordinate = y,
                        start = searchArea.left,
                        end = searchArea.right,
                        thickness = 2,
                        color = samplePixel
                    )
                )
            }
        }

        // 2. Search for Vertical lines crossing through this region
        for (x in x0..x1 step 2) {
            var topDark = false
            var bottomDark = false

            if (searchArea.top > 10) {
                var darkCount = 0
                for (y in max(0, searchArea.top - 25) until searchArea.top) {
                    val pixel = bitmap.getPixel(x, y)
                    val lum = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    if (lum < 160) darkCount++
                }
                topDark = darkCount >= 10
            }

            if (searchArea.bottom < h - 10) {
                var darkCount = 0
                for (y in searchArea.bottom until min(h, searchArea.bottom + 25)) {
                    val pixel = bitmap.getPixel(x, y)
                    val lum = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    if (lum < 160) darkCount++
                }
                bottomDark = darkCount >= 10
            }

            if (topDark && bottomDark) {
                val samplePixel = bitmap.getPixel(x, max(0, searchArea.top - 5))
                lines.add(
                    DocumentLine(
                        isHorizontal = false,
                        coordinate = x,
                        start = searchArea.top,
                        end = searchArea.bottom,
                        thickness = 2,
                        color = samplePixel
                    )
                )
            }
        }

        return lines
    }
}
