package com.docscan.util

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Central ML Kit Document Scanner configuration.
 *
 * Google exposes only a small set of customization points for the built-in scanner UI:
 *  - gallery import on/off
 *  - page limit
 *  - output format (JPEG / PDF)
 *  - scanner mode
 *
 * The SDK-owned capture button cannot be styled from the public API. The current public Builder
 * also does not expose a supported setter that forces MANUAL capture mode, so we do not use
 * reflection/hidden APIs that could break across Google Play services updates.
 *
 * Keep every scanner entry point using these presets so Scan and NID behave consistently.
 */
object MlKitDocumentScannerHelper {

    private const val DEFAULT_DOCUMENT_PAGE_LIMIT = 100
    private const val NID_PAGE_LIMIT = 2

    data class ScanResult(
        val imageUris: List<Uri>,
        val pdfUri: Uri?,
        val pageCount: Int
    )

    /**
     * Modern full-feature document scan preset.
     *
     * - Gallery import enabled for convenience.
     * - Up to 100 pages for normal multi-page documents.
     * - JPEG is returned for the app's own crop/filter/editor flow.
     * - PDF is also requested for normal document workflows.
     * - FULL mode enables Google's filters, automatic enhancement, and image cleaning.
     * - Crop, rotation, page reorder and perspective correction remain available.
     */
    fun createDocumentScannerOptions(
        pageLimit: Int = DEFAULT_DOCUMENT_PAGE_LIMIT,
        allowGallery: Boolean = true
    ): GmsDocumentScannerOptions {
        return buildOptions(
            pageLimit = pageLimit,
            allowGallery = allowGallery,
            includePdf = true
        )
    }

    /**
     * NID front/back preset.
     *
     * - Exactly two scan slots maximum: Front + Back.
     * - Gallery import enabled so Front/Back can be captured by camera or selected from Gallery.
     * - JPEG only because the app builds its own two-sided NID preview/export after scanning;
     *   skipping ML Kit PDF generation reduces unnecessary processing.
     * - FULL mode enables Google's optional image filters and cleaning tools.
     * - Crop, rotate and page management remain available.
     */
    fun createNidScannerOptions(): GmsDocumentScannerOptions {
        return buildOptions(
            pageLimit = NID_PAGE_LIMIT,
            allowGallery = true,
            includePdf = false
        )
    }

    /**
     * Single-side NID preset used by the Scan ID landing page.
     * Each tap (Front or Back) opens ML Kit for exactly one side, while Gallery import remains
     * available inside the ML Kit UI. The returned JPEG is already auto-cropped/perspective corrected.
     */
    fun createNidSideScannerOptions(): GmsDocumentScannerOptions {
        return buildOptions(
            pageLimit = 1,
            allowGallery = true,
            includePdf = false
        )
    }

    /**
     * Backwards-compatible generic factory for other callers in the project.
     */
    fun createScannerOptions(
        pageLimit: Int = DEFAULT_DOCUMENT_PAGE_LIMIT,
        allowGallery: Boolean = true,
        includePdf: Boolean = true
    ): GmsDocumentScannerOptions {
        return buildOptions(
            pageLimit = pageLimit,
            allowGallery = allowGallery,
            includePdf = includePdf
        )
    }

    private fun buildOptions(
        pageLimit: Int,
        allowGallery: Boolean,
        includePdf: Boolean
    ): GmsDocumentScannerOptions {
        val safePageLimit = pageLimit.coerceAtLeast(1)
        val builder = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(allowGallery)
            .setPageLimit(safePageLimit)
            .setScannerMode(SCANNER_MODE_FULL)

        if (includePdf) {
            builder.setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
        } else {
            builder.setResultFormats(RESULT_FORMAT_JPEG)
        }

        return builder.build()
    }

    fun getScanner(
        options: GmsDocumentScannerOptions = createDocumentScannerOptions()
    ): GmsDocumentScanner {
        return GmsDocumentScanning.getClient(options)
    }

    fun startScanning(
        activity: Activity,
        options: GmsDocumentScannerOptions = createDocumentScannerOptions(),
        onIntentSenderReady: (IntentSender) -> Unit,
        onError: (Exception) -> Unit
    ) {
        getScanner(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                onIntentSenderReady(intentSender)
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    fun extractResult(data: Intent?): ScanResult? {
        if (data == null) return null

        val result = GmsDocumentScanningResult.fromActivityResultIntent(data) ?: return null
        val imageUris = result.pages?.mapNotNull { it.imageUri }.orEmpty()
        val pdfUri = result.pdf?.uri
        val pageCount = result.pdf?.pageCount ?: imageUris.size

        return ScanResult(
            imageUris = imageUris,
            pdfUri = pdfUri,
            pageCount = pageCount
        )
    }
}
