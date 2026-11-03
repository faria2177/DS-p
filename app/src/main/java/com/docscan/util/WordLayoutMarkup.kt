package com.docscan.util

/**
 * Shared high-fidelity layout instructions for the To Word pipeline.
 *
 * The AI providers return a deliberately small markup language that keeps the
 * visual relationships which plain OCR/Markdown normally loses: section bands,
 * key/value tables, two-column notes, alert banners and centered document titles.
 * DocxExporter understands these markers and converts them to native editable
 * OpenXML tables/paragraphs rather than flattening the page into an image.
 */
object WordLayoutMarkup {

    fun buildPrompt(rawText: String? = null): String {
        val sourceHint = rawText?.takeIf { it.isNotBlank() }?.let {
            "\nOCR REFERENCE (use only to improve recognition; the image is authoritative):\n${it.take(12000)}"
        }.orEmpty()

        return """
            You are a professional document-layout reconstruction engine for Microsoft Word.
            Analyze the supplied document image and reproduce its CONTENT AND LAYOUT as an editable Word document.

            ABSOLUTE RULES
            1. Extract every visible word exactly. Never summarize, translate, normalize, invent or correct values.
            2. Preserve Bengali + English mixed text, punctuation, masked numbers, dates, amounts, IDs and symbols exactly.
            3. Preserve reading order, section order, table rows/columns, merged-looking headings, two-column regions,
               bullet lists, colored notice bands and footer/sign-off blocks.
            4. The output must be EDITABLE. Do not return a screenshot description.
            5. Ignore decorative logos/QR content as text unless readable; the app preserves the graphical header separately.
            6. If a cell is unreadable write [UNREADABLE]. Do not guess.

            OUTPUT FORMAT - RETURN ONLY THIS LIGHTWEIGHT MARKUP (no code fences, no explanation):

            :::title-center
            Main centered title line
            Optional centered subtitle line
            :::endtitle

            Normal paragraph text stays as normal lines.

            For a colored section heading immediately above a key/value table:
            :::section
            Section heading exactly as printed
            :::endsection

            :::keyvalue-table
            | left cell | right cell |
            | next left cell | next right cell |
            :::endtable

            For a normal data table with a real header row:
            :::table
            | Header 1 | Header 2 | Header 3 |
            | --- | --- | --- |
            | value | value | value |
            :::endtable

            For side-by-side content such as English notes on the left and Bengali notes on the right:
            :::columns2
            :::left
            Heading or text
            • bullet
            • bullet
            :::right
            Heading or text
            • bullet
            • bullet
            :::endcolumns2

            For a full-width highlighted notice/banner:
            :::banner-green
            exact banner text
            :::endbanner

            For an orange/cream warning banner:
            :::banner-orange
            exact banner text
            :::endbanner

            For the final two-column sign-off/footer:
            :::footer2
            :::left
            left text
            :::right
            right text
            :::endfooter2

            FORMATTING WITHIN TEXT
            - **bold text** for visibly bold content.
            - *italic text* for visibly italic content.
            - • for bullets.
            - Do not create headings/tables that do not exist visually.
            - If the document is a ticket/form with green section bars and 2-column field tables, use :::section + :::keyvalue-table.
            - If the document is Bangladesh Railway e-ticket style, preserve Journey Information and Passenger Information
              as separate green sections, preserve all rows, keep the English/Bengali note columns side-by-side, and preserve
              both bottom alert bands and the final bilingual sign-off.
            $sourceHint
        """.trimIndent()
    }

    fun cleanAiOutput(text: String): String {
        return text
            .replace("```markdown", "", ignoreCase = true)
            .replace("```md", "", ignoreCase = true)
            .replace("```text", "", ignoreCase = true)
            .replace("```", "")
            .trim()
    }

    fun isBangladeshRailwayTicket(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.lowercase()
        return t.contains("bangladesh railway") &&
            (t.contains("journey information") || t.contains("train name & number")) &&
            (t.contains("passenger information") || t.contains("pnr number"))
    }

    fun shouldPreserveHeaderGraphic(text: String?): Boolean {
        if (isBangladeshRailwayTicket(text)) return true
        if (text.isNullOrBlank()) return false
        val t = text.lowercase()
        // Other official forms/tickets commonly have logos, seals or QR codes in a compact header.
        return (t.contains("powered by") || t.contains("qr") || t.contains("e-ticket")) &&
            (t.contains("information") || t.contains("details"))
    }

    /**
     * Local fallback for Railway e-tickets when no vision provider is available.
     * It does not fabricate values; it only groups already-recognized OCR lines.
     */
    fun buildRailwayFallback(rawText: String): String {
        if (!isBangladeshRailwayTicket(rawText)) return rawText.trim()

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val out = StringBuilder()
        out.append(":::title-center\nBANGLADESH RAILWAY\nবাংলাদেশ রেলওয়ে\n:::endtitle\n")

        val journeyStart = lines.indexOfFirst { it.contains("Journey Information", ignoreCase = true) }
        val passengerStart = lines.indexOfFirst { it.contains("Passenger Information", ignoreCase = true) }
        val noteStart = lines.indexOfFirst { it.contains("Please Note", ignoreCase = true) }

        // Intro text before Journey Information, excluding the duplicated title.
        if (journeyStart > 0) {
            lines.subList(0, journeyStart)
                .filterNot { it.contains("BANGLADESH RAILWAY", ignoreCase = true) || it.contains("বাংলাদেশ রেলওয়ে") }
                .forEach { out.append(it).append('\n') }
        }

        fun appendKeyValueSection(title: String, from: Int, to: Int) {
            if (from < 0 || to <= from) return
            out.append(":::section\n").append(title).append("\n:::endsection\n")
            out.append(":::keyvalue-table\n")
            val knownLabels = listOf(
                "Issue Date & Time", "Journey Date & Time", "Train Name & Number", "From Station", "To Station",
                "Class Name", "Coach Name / Seat(s)", "No. of Seats", "No. of Adult Passenger(s)",
                "No. of Senior Citizen Passenger(s)", "No. of Child Passenger(s)", "Fare", "VAT",
                "Service Charge", "Total Fare", "Passenger Name", "Identification Type", "Identification Number",
                "Mobile Number", "PNR Number"
            )

            lines.subList(from + 1, to).forEach { line ->
                // Prefer separators produced by geometry/AI.
                val cells = when {
                    line.contains("|") -> line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    line.contains("\t") -> line.split("\t").map { it.trim() }.filter { it.isNotEmpty() }
                    else -> emptyList()
                }
                if (cells.size >= 2) {
                    out.append("| ").append(cells.first()).append(" | ")
                        .append(cells.drop(1).joinToString(" ")).append(" |\n")
                } else {
                    // Railway e-ticket OCR commonly returns "Field (বাংলা) Value" on one line.
                    val label = knownLabels.firstOrNull { line.startsWith(it, ignoreCase = true) }
                    if (label != null) {
                        val closeParen = line.indexOf(')', startIndex = label.length)
                        var splitAt = if (closeParen >= label.length) closeParen + 1 else label.length
                        // Preserve bold markers that visually belong to the field label (e.g. Total Fare**).
                        while (splitAt < line.length && line[splitAt] == '*') splitAt++
                        val left = line.substring(0, splitAt).trim()
                        val right = line.substring(splitAt).trim()
                        out.append("| ").append(left).append(" | ").append(right).append(" |\n")
                    } else {
                        // Keep unsplittable OCR lines rather than inventing a value.
                        out.append("| ").append(line).append(" |  |\n")
                    }
                }
            }
            out.append(":::endtable\n")
        }

        val journeyEnd = when {
            passengerStart > journeyStart -> passengerStart
            noteStart > journeyStart -> noteStart
            else -> lines.size
        }
        appendKeyValueSection("Journey Information (যাত্রার তথ্য)", journeyStart, journeyEnd)

        if (passengerStart >= 0) {
            val passengerEnd = if (noteStart > passengerStart) noteStart else lines.size
            appendKeyValueSection("Passenger Information (যাত্রীর তথ্য)", passengerStart, passengerEnd)
        }

        if (noteStart >= 0) {
            out.append(":::columns2\n:::left\n")
            lines.subList(noteStart, lines.size).forEach { out.append(it).append('\n') }
            out.append(":::right\n\n:::endcolumns2\n")
        }
        return out.toString().trim()
    }
}
