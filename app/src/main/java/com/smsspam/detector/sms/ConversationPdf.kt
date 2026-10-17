package com.smsspam.detector.sms

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a conversation to a paginated PDF. Used by the conversation's
 * "Export as PDF" action; the caller supplies a destination [Uri] from the
 * system file picker.
 */
object ConversationPdf {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val LINE_HEIGHT = 14f

    /** Writes [messages] to [uri]. Returns the number of messages written. */
    fun export(context: Context, uri: Uri, title: String, messages: List<SmsMessage>): Int {
        val document = PdfDocument()
        val titlePaint = Paint().apply { isAntiAlias = true; textSize = 16f; isFakeBoldText = true }
        val metaPaint = Paint().apply { isAntiAlias = true; textSize = 9f; color = 0xFF666666.toInt() }
        val bodyPaint = Paint().apply { isAntiAlias = true; textSize = 11f }
        val formatter = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())

        var pageNumber = 1
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
        var canvas = page.canvas
        var y = MARGIN + 10f

        canvas.drawText(title.take(80), MARGIN, y, titlePaint)
        y += 28f

        messages.forEach { message ->
            val header = "${if (message.isIncoming) "Received" else "Sent"} · ${formatter.format(Date(message.timestamp))}"
            val lines = wrap(message.body.ifBlank { "(no text)" }, bodyPaint, PAGE_WIDTH - 2 * MARGIN)
            val needed = LINE_HEIGHT + lines.size * LINE_HEIGHT + 10f
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                canvas = page.canvas
                y = MARGIN + 10f
            }
            canvas.drawText(header, MARGIN, y, metaPaint)
            y += LINE_HEIGHT
            lines.forEach { line ->
                canvas.drawText(line, MARGIN, y, bodyPaint)
                y += LINE_HEIGHT
            }
            y += 10f
        }
        document.finishPage(page)

        context.contentResolver.openOutputStream(uri, "wt")?.use { document.writeTo(it) }
            ?: error("Could not open the selected file")
        document.close()
        return messages.size
    }

    /** Greedy word wrap using [paint]'s metrics for [maxWidth]. */
    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var remaining = paragraph
            if (remaining.isEmpty()) {
                result += ""
                return@forEach
            }
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, maxWidth, null)
                if (count <= 0) break
                var end = count
                // Prefer to break on a space just before the limit.
                if (end < remaining.length) {
                    val space = remaining.lastIndexOf(' ', end)
                    if (space > 0) end = space
                }
                result += remaining.substring(0, end).trimEnd()
                remaining = remaining.substring(end).trimStart()
            }
        }
        return result.ifEmpty { listOf("") }
    }
}
