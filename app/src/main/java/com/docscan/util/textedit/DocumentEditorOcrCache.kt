package com.docscan.util.textedit

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DocumentEditorOcrCache {
    private const val CACHE_VERSION = 4

    data class CachedPageAnalysis(
        val rotationDegrees: Int,
        val ocrDocument: OcrDocument,
        val imageElements: List<EditorImageElement>
    )

    fun load(
        context: Context,
        documentId: Long,
        pageId: Long,
        sourcePath: String
    ): CachedPageAnalysis? {
        val source = File(sourcePath)
        if (!source.exists()) return null
        val cacheFile = cacheFile(context, documentId, pageId)
        if (!cacheFile.exists()) return null

        return runCatching {
            val root = JSONObject(cacheFile.readText())
            if (root.optInt("version", -1) != CACHE_VERSION) return null
            if (root.optString("sourcePath") != source.absolutePath) return null
            if (root.optLong("sourceLength", -1L) != source.length()) return null
            if (root.optLong("sourceModified", -1L) != source.lastModified()) return null

            val ocrJson = root.getJSONObject("ocr")
            val ocr = parseOcr(ocrJson)
            val images = parseImageElements(root.optJSONArray("images") ?: JSONArray())
            CachedPageAnalysis(
                rotationDegrees = root.optInt("rotationDegrees", 0),
                ocrDocument = ocr,
                imageElements = images
            )
        }.getOrNull()
    }

    fun save(
        context: Context,
        documentId: Long,
        pageId: Long,
        sourcePath: String,
        rotationDegrees: Int,
        ocrDocument: OcrDocument,
        imageElements: List<EditorImageElement>
    ) {
        val source = File(sourcePath)
        if (!source.exists()) return
        runCatching {
            val target = cacheFile(context, documentId, pageId)
            target.parentFile?.mkdirs()
            val root = JSONObject().apply {
                put("version", CACHE_VERSION)
                put("sourcePath", source.absolutePath)
                put("sourceLength", source.length())
                put("sourceModified", source.lastModified())
                put("rotationDegrees", rotationDegrees)
                put("ocr", serializeOcr(ocrDocument))
                put("images", serializeImageElements(imageElements))
            }
            target.writeText(root.toString())
        }
    }

    fun invalidate(context: Context, documentId: Long, pageId: Long) {
        runCatching { cacheFile(context, documentId, pageId).delete() }
    }

    private fun cacheFile(context: Context, documentId: Long, pageId: Long): File {
        val dir = File(context.filesDir, "document_editor_cache/document_$documentId")
        return File(dir, "page_${pageId}_ocr.json")
    }

    private fun serializeOcr(doc: OcrDocument): JSONObject {
        return JSONObject().apply {
            put("fullText", doc.fullText)
            put("imageWidth", doc.imageWidth)
            put("imageHeight", doc.imageHeight)
            put("timestamp", doc.timestamp)
            val blocks = JSONArray()
            doc.blocks.forEach { block ->
                blocks.put(JSONObject().apply {
                    put("id", block.id)
                    put("text", block.text)
                    put("pixelRect", rectToJson(block.pixelRect))
                    put("normalizedRect", rectFToJson(block.normalizedRect))
                    put("rotationAngle", block.rotationAngle)
                    put("estimatedTextSizeSp", block.estimatedTextSizeSp)
                    put("estimatedTextColor", block.estimatedTextColor.toArgb())
                    put("estimatedBackgroundColor", block.estimatedBackgroundColor.toArgb())
                    put("fontStyle", styleToJson(block.estimatedFontStyle))
                    val lines = JSONArray()
                    block.lines.forEach { line ->
                        lines.put(JSONObject().apply {
                            put("id", line.id)
                            put("text", line.text)
                            put("pixelRect", rectToJson(line.pixelRect))
                            put("normalizedRect", rectFToJson(line.normalizedRect))
                            line.confidence?.let { put("confidence", it) }
                            put("rotationAngle", line.rotationAngle)
                            put("estimatedTextSizeSp", line.estimatedTextSizeSp)
                            put("estimatedTextColor", line.estimatedTextColor.toArgb())
                            put("estimatedBackgroundColor", line.estimatedBackgroundColor.toArgb())
                            put("fontStyle", styleToJson(line.estimatedFontStyle))
                            val elements = JSONArray()
                            line.elements.forEach { element ->
                                elements.put(JSONObject().apply {
                                    put("id", element.id)
                                    put("text", element.text)
                                    put("pixelRect", rectToJson(element.pixelRect))
                                    put("normalizedRect", rectFToJson(element.normalizedRect))
                                    element.confidence?.let { put("confidence", it) }
                                })
                            }
                            put("elements", elements)
                        })
                    }
                    put("lines", lines)
                })
            }
            put("blocks", blocks)
        }
    }

    private fun parseOcr(root: JSONObject): OcrDocument {
        val blocksArray = root.optJSONArray("blocks") ?: JSONArray()
        val blocks = buildList {
            for (i in 0 until blocksArray.length()) {
                val b = blocksArray.getJSONObject(i)
                val linesArray = b.optJSONArray("lines") ?: JSONArray()
                val lines = buildList {
                    for (j in 0 until linesArray.length()) {
                        val l = linesArray.getJSONObject(j)
                        val elementsArray = l.optJSONArray("elements") ?: JSONArray()
                        val elements = buildList {
                            for (k in 0 until elementsArray.length()) {
                                val e = elementsArray.getJSONObject(k)
                                add(
                                    OcrTextElement(
                                        id = e.optString("id"),
                                        text = e.optString("text"),
                                        pixelRect = jsonToRect(e.getJSONObject("pixelRect")),
                                        normalizedRect = jsonToRectF(e.getJSONObject("normalizedRect")),
                                        confidence = if (e.has("confidence")) e.optDouble("confidence").toFloat() else null
                                    )
                                )
                            }
                        }
                        add(
                            OcrTextLine(
                                id = l.optString("id"),
                                text = l.optString("text"),
                                pixelRect = jsonToRect(l.getJSONObject("pixelRect")),
                                normalizedRect = jsonToRectF(l.getJSONObject("normalizedRect")),
                                elements = elements,
                                confidence = if (l.has("confidence")) l.optDouble("confidence").toFloat() else null,
                                rotationAngle = l.optDouble("rotationAngle", 0.0).toFloat(),
                                estimatedTextSizeSp = l.optDouble("estimatedTextSizeSp", 16.0).toFloat(),
                                estimatedTextColor = Color(l.optInt("estimatedTextColor", android.graphics.Color.BLACK)),
                                estimatedBackgroundColor = Color(l.optInt("estimatedBackgroundColor", android.graphics.Color.WHITE)),
                                estimatedFontStyle = jsonToStyle(l.optJSONObject("fontStyle"))
                            )
                        )
                    }
                }
                add(
                    OcrTextBlock(
                        id = b.optString("id"),
                        text = b.optString("text"),
                        pixelRect = jsonToRect(b.getJSONObject("pixelRect")),
                        normalizedRect = jsonToRectF(b.getJSONObject("normalizedRect")),
                        lines = lines,
                        rotationAngle = b.optDouble("rotationAngle", 0.0).toFloat(),
                        estimatedTextSizeSp = b.optDouble("estimatedTextSizeSp", 16.0).toFloat(),
                        estimatedTextColor = Color(b.optInt("estimatedTextColor", android.graphics.Color.BLACK)),
                        estimatedBackgroundColor = Color(b.optInt("estimatedBackgroundColor", android.graphics.Color.WHITE)),
                        estimatedFontStyle = jsonToStyle(b.optJSONObject("fontStyle"))
                    )
                )
            }
        }

        return OcrDocument(
            fullText = root.optString("fullText"),
            blocks = blocks,
            imageWidth = root.optInt("imageWidth", 1).coerceAtLeast(1),
            imageHeight = root.optInt("imageHeight", 1).coerceAtLeast(1),
            timestamp = root.optLong("timestamp", System.currentTimeMillis())
        )
    }

    private fun serializeImageElements(elements: List<EditorImageElement>): JSONArray {
        return JSONArray().apply {
            elements.forEach { element ->
                put(JSONObject().apply {
                    put("id", element.id)
                    put("pageIndex", element.pageIndex)
                    put("type", element.type.name)
                    put("rect", rectFToJson(element.normalizedRect))
                    put("label", element.label)
                    put("protected", element.protected)
                })
            }
        }
    }

    private fun parseImageElements(array: JSONArray): List<EditorImageElement> = buildList {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            add(
                EditorImageElement(
                    id = item.optString("id"),
                    pageIndex = item.optInt("pageIndex", 0),
                    type = runCatching { EditorElementType.valueOf(item.optString("type")) }.getOrDefault(EditorElementType.IMAGE),
                    normalizedRect = jsonToRectF(item.getJSONObject("rect")),
                    label = item.optString("label", "Image"),
                    protected = item.optBoolean("protected", false)
                )
            )
        }
    }

    private fun rectToJson(rect: Rect) = JSONObject().apply {
        put("left", rect.left); put("top", rect.top); put("right", rect.right); put("bottom", rect.bottom)
    }

    private fun jsonToRect(json: JSONObject) = Rect(
        json.optInt("left"), json.optInt("top"), json.optInt("right"), json.optInt("bottom")
    )

    private fun rectFToJson(rect: RectF) = JSONObject().apply {
        put("left", rect.left.toDouble()); put("top", rect.top.toDouble()); put("right", rect.right.toDouble()); put("bottom", rect.bottom.toDouble())
    }

    private fun jsonToRectF(json: JSONObject) = RectF(
        json.optDouble("left").toFloat(),
        json.optDouble("top").toFloat(),
        json.optDouble("right").toFloat(),
        json.optDouble("bottom").toFloat()
    )

    private fun styleToJson(style: FontStyleEstimate) = JSONObject().apply {
        put("bold", style.isBold)
        put("italic", style.isItalic)
        put("underline", style.isUnderline)
        put("family", style.fontFamilyType)
    }

    private fun jsonToStyle(json: JSONObject?): FontStyleEstimate {
        if (json == null) return FontStyleEstimate()
        return FontStyleEstimate(
            isBold = json.optBoolean("bold", false),
            isItalic = json.optBoolean("italic", false),
            isUnderline = json.optBoolean("underline", false),
            fontFamilyType = json.optString("family", "DEFAULT")
        )
    }
}
