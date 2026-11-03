package com.docscan.util

import java.util.regex.Pattern

/**
 * Intelligent Document & Letter Structure Analyzer.
 * Identifies formal letters, notices, memos, circulars, and official bank/corporate correspondence.
 * Preserves two-column letterheads, Reference & Date alignments, Recipient blocks, Subject lines,
 * highlighted bullet points, body paragraphs, and dual-column Sign-off / Seal stamp footers.
 */
object OfficialDocumentAnalyzer {

    data class LetterStructure(
        val letterheadLeft: List<String> = emptyList(),
        val letterheadRight: List<String> = emptyList(),
        val refText: String? = null,
        val dateText: String? = null,
        val recipientLines: List<String> = emptyList(),
        val subject: String? = null,
        val salutation: String? = null,
        val highlightedBullets: List<String> = emptyList(),
        val bodyParagraphs: List<String> = emptyList(),
        val signoffLines: List<String> = emptyList(),
        val stampLines: List<String> = emptyList()
    )

    private val REF_PREFIX_REGEX = Pattern.compile("(?i)^(ref|reference|memo|স্মারক নং|পত্র নং|নং)[\\s.:/\\-]+(.*)")
    private val DATE_PREFIX_REGEX = Pattern.compile("(?i)^(date|dated|তারিখ)[\\s.:/\\-]+(.*)")
    private val SUBJECT_PREFIX_REGEX = Pattern.compile("(?i)^(subject|sub|বিষয়)[\\s.:/\\-]+(.*)")
    private val TO_PREFIX_REGEX = Pattern.compile("(?i)^(to|to,|বরাবর|বরাবর,)\\s*$")

    private val ORG_KEYWORDS = listOf(
        "BANK", "PLC", "LIMITED", "LTD", "CORPORATION", "CORP", "MINISTRY", "COLLEGE", "UNIVERSITY",
        "HOSPITAL", "DEPARTMENT", "DIVISION", "GOVERNMENT", "কার্যালয়", "ব্যাংক", "কলেজ", "মন্ত্রণালয়"
    )

    private val ADDRESS_KEYWORDS = listOf(
        "BHABAN", "FLOOR", "ROAD", "STREET", "AVENUE", "C/A", "DHAKA", "CHITTAGONG", "JASHORE", "JESSORE",
        "SYLHET", "BANGLADESH", "WWW.", "@", ".COM", ".BD", "TEL:", "PHONE:", "EMAIL:"
    )

    private val SIGNOFF_KEYWORDS = listOf(
        "kind regards", "regards", "yours sincerely", "sincerely", "yours faithfully", "with regards",
        "faithfully yours", "ধন্যবাদান্তে", "নিবেদক", "বিনীত", "আবেদক", "স্বাক্ষর"
    )

    private val STAMP_KEYWORDS = listOf(
        "অধ্যক্ষের কার্যালয়", "কার্যালয়", "মেডিকেল কলেজ", "পত্র নং", "তাং", "স্বাক্ষরিত",
        "received", "accepted", "verified", "seal", "stamp"
    )

    /**
     * Determines if a piece of text represents an official correspondence/letter/notice.
     */
    fun isOfficialLetterOrNotice(rawText: String): Boolean {
        val upper = rawText.uppercase()
        var signals = 0
        if (upper.contains("REF:") || upper.contains("REFERENCE:") || upper.contains("স্মারক")) signals += 2
        if (upper.contains("DATE:") || upper.contains("DATED:") || upper.contains("তারিখ")) signals += 2
        if (upper.contains("SUBJECT:") || upper.contains("SUB:") || upper.contains("বিষয়")) signals += 2
        if (upper.contains("KIND REGARDS") || upper.contains("YOURS SINCERELY") || upper.contains("ধন্যবাদান্তে")) signals += 2
        if (ORG_KEYWORDS.any { upper.contains(it) }) signals += 1
        if (ADDRESS_KEYWORDS.any { upper.contains(it) }) signals += 1
        if (rawText.lines().any { TO_PREFIX_REGEX.matcher(it.trim()).matches() }) signals += 2
        return signals >= 4
    }

    /**
     * Parses and enriches raw text into structured layout markers for Word generation and UI preview.
     */
    fun structureTextForWord(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return ""

        // If markers are already explicitly present, keep them clean
        if (trimmed.contains(":::letterhead") || trimmed.contains(":::signoff") || trimmed.contains("|| Date:")) {
            return trimmed
        }

        if (!isOfficialLetterOrNotice(trimmed)) {
            return trimmed
        }

        val structure = parseOfficialLetter(trimmed)
        val sb = StringBuilder()

        // 1. Letterhead
        if (structure.letterheadLeft.isNotEmpty() || structure.letterheadRight.isNotEmpty()) {
            sb.append(":::letterhead-left\n")
            structure.letterheadLeft.forEach { sb.append(it).append("\n") }
            sb.append(":::\n")
            sb.append(":::letterhead-right\n")
            structure.letterheadRight.forEach { sb.append(it).append("\n") }
            sb.append(":::\n\n")
        }

        // 2. Ref & Date
        if (structure.refText != null || structure.dateText != null) {
            val ref = structure.refText ?: ""
            val date = structure.dateText ?: ""
            if (ref.isNotBlank() && date.isNotBlank()) {
                sb.append("$ref || $date\n\n")
            } else if (ref.isNotBlank()) {
                sb.append("$ref\n\n")
            } else if (date.isNotBlank()) {
                sb.append("$date\n\n")
            }
        }

        // 3. Recipient
        if (structure.recipientLines.isNotEmpty()) {
            sb.append(":::recipient\n")
            structure.recipientLines.forEach { sb.append(it).append("\n") }
            sb.append(":::\n\n")
        }

        // 4. Subject
        if (structure.subject != null) {
            sb.append("**").append(structure.subject).append("**\n\n")
        }

        // 5. Salutation
        if (structure.salutation != null) {
            sb.append(structure.salutation).append("\n\n")
        }

        // 6. Highlighted Bullets
        structure.highlightedBullets.forEach { bullet ->
            sb.append("• ==").append(bullet).append("==\n\n")
        }

        // 7. Body Paragraphs
        structure.bodyParagraphs.forEach { para ->
            sb.append(para).append("\n\n")
        }

        // 8. Sign-off & Stamp
        if (structure.signoffLines.isNotEmpty() || structure.stampLines.isNotEmpty()) {
            if (structure.signoffLines.isNotEmpty()) {
                sb.append(":::signoff-left\n")
                structure.signoffLines.forEach { sb.append(it).append("\n") }
                sb.append(":::\n")
            }
            if (structure.stampLines.isNotEmpty()) {
                sb.append(":::signoff-right\n")
                structure.stampLines.forEach { sb.append(it).append("\n") }
                sb.append(":::\n")
            }
        }

        return sb.toString().trim()
    }

    /**
     * Parses the lines of an official letter into categorized components.
     */
    fun parseOfficialLetter(rawText: String): LetterStructure {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return LetterStructure()

        val letterheadLeft = mutableListOf<String>()
        val letterheadRight = mutableListOf<String>()
        var refText: String? = null
        var dateText: String? = null
        val recipientLines = mutableListOf<String>()
        var subjectText: String? = null
        var salutationText: String? = null
        val highlightedBullets = mutableListOf<String>()
        val bodyParagraphs = mutableListOf<String>()
        val signoffLines = mutableListOf<String>()
        val stampLines = mutableListOf<String>()

        var idx = 0

        // Phase 1: Letterhead (Top lines until Ref, Date, or To)
        while (idx < lines.size) {
            val line = lines[idx]
            val upper = line.uppercase()

            if (REF_PREFIX_REGEX.matcher(line).matches() ||
                DATE_PREFIX_REGEX.matcher(line).matches() ||
                TO_PREFIX_REGEX.matcher(line).matches() ||
                SUBJECT_PREFIX_REGEX.matcher(line).matches() ||
                line.contains("|| Date:")
            ) {
                break
            }

            // Distribute into Left (Org name) vs Right (Division, Address, Contact)
            if (ADDRESS_KEYWORDS.any { upper.contains(it) } || upper.contains("DIVISION") || upper.contains("DEPARTMENT")) {
                letterheadRight.add(line)
            } else {
                letterheadLeft.add(line)
            }
            idx++
        }

        // Phase 2: Ref and Date
        while (idx < lines.size) {
            val line = lines[idx]
            if (line.contains("|| Date:")) {
                val parts = line.split("||")
                refText = parts.getOrNull(0)?.trim()
                dateText = parts.getOrNull(1)?.trim()
                idx++
                break
            } else if (REF_PREFIX_REGEX.matcher(line).matches()) {
                refText = line
                idx++
            } else if (DATE_PREFIX_REGEX.matcher(line).matches()) {
                dateText = line
                idx++
            } else {
                break
            }
        }

        // Phase 3: Recipient ("To ...")
        if (idx < lines.size && TO_PREFIX_REGEX.matcher(lines[idx]).matches()) {
            recipientLines.add(lines[idx])
            idx++
            while (idx < lines.size) {
                val line = lines[idx]
                if (SUBJECT_PREFIX_REGEX.matcher(line).matches() ||
                    line.startsWith("Dear", ignoreCase = true) ||
                    line.startsWith("Sir", ignoreCase = true) ||
                    line.startsWith("We would like", ignoreCase = true) ||
                    line.startsWith("With reference", ignoreCase = true)
                ) {
                    break
                }
                recipientLines.add(line)
                idx++
            }
        }

        // Phase 4: Subject
        if (idx < lines.size && SUBJECT_PREFIX_REGEX.matcher(lines[idx]).matches()) {
            subjectText = lines[idx]
            idx++
        }

        // Phase 5: Salutation
        if (idx < lines.size) {
            val line = lines[idx]
            if (line.startsWith("Dear", ignoreCase = true) ||
                line.startsWith("Respected", ignoreCase = true) ||
                line.startsWith("Sir,", ignoreCase = true) ||
                line.equals("Sir", ignoreCase = true) ||
                line.startsWith("মহোদয়", ignoreCase = true)
            ) {
                salutationText = line
                idx++
            }
        }

        // Phase 6: Body Paragraphs & Bullets until Closing
        val currentParagraphLines = mutableListOf<String>()

        fun flushParagraph() {
            if (currentParagraphLines.isNotEmpty()) {
                val joined = currentParagraphLines.joinToString(" ")
                bodyParagraphs.add(joined)
                currentParagraphLines.clear()
            }
        }

        var inSignoffSection = false

        while (idx < lines.size) {
            val line = lines[idx]
            val lower = line.lowercase()

            // Check if this line marks the beginning of the sign-off / footer section
            if (SIGNOFF_KEYWORDS.any { lower.startsWith(it) || lower == it } ||
                line.startsWith("(") && line.endsWith(")") && idx >= lines.size - 8
            ) {
                flushParagraph()
                inSignoffSection = true
                break
            }

            // Bullet points
            if (line.startsWith("•") || line.startsWith("-") || line.startsWith("*") || line.startsWith("▪")) {
                flushParagraph()
                val cleanBullet = line.removePrefix("•").removePrefix("-").removePrefix("*").removePrefix("▪")
                    .removePrefix("==").removeSuffix("==").trim()
                highlightedBullets.add(cleanBullet)
                idx++
                continue
            }

            // Check for potential isolated seal / stamp at the bottom
            if (STAMP_KEYWORDS.any { line.contains(it) } && idx >= lines.size - 6) {
                flushParagraph()
                inSignoffSection = true
                break
            }

            // Accumulate body text
            currentParagraphLines.add(line)
            idx++
        }
        flushParagraph()

        // Phase 7: Sign-off & Stamp parsing
        while (idx < lines.size) {
            val line = lines[idx]
            val lower = line.lowercase()

            if (STAMP_KEYWORDS.any { line.contains(it) } ||
                line.contains("যশোর") || line.contains("কলেজ") ||
                line.matches(Regex(".*\\d{1,2}/\\d{1,2}/\\d{2,4}.*")) && stampLines.isNotEmpty()
            ) {
                stampLines.add(line)
            } else {
                signoffLines.add(line)
            }
            idx++
        }

        return LetterStructure(
            letterheadLeft = letterheadLeft,
            letterheadRight = letterheadRight,
            refText = refText,
            dateText = dateText,
            recipientLines = recipientLines,
            subject = subjectText,
            salutation = salutationText,
            highlightedBullets = highlightedBullets,
            bodyParagraphs = bodyParagraphs,
            signoffLines = signoffLines,
            stampLines = stampLines
        )
    }
}
