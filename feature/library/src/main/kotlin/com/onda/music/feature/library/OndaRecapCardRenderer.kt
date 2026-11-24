package com.onda.music.feature.library

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.onda.music.core.database.ArtistPlayStatRow
import com.onda.music.core.database.TrackPlayStatRow
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OndaRecapCardRenderer {

    suspend fun renderAndShare(
        context: Context,
        rangeLabel: String,
        totalPlayCount: Int,
        totalDurationMs: Long,
        topTracks: List<TrackPlayStatRow>,
        topArtists: List<ArtistPlayStatRow>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = renderBitmap(
                rangeLabel = rangeLabel,
                totalPlayCount = totalPlayCount,
                totalDurationMs = totalDurationMs,
                topTracks = topTracks,
                topArtists = topArtists
            )
            val dir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
            val outFile = File(dir, "onda-recap-${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Onda Music OLED Listening Recap")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Onda Recap").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    fun renderBitmap(
        rangeLabel: String,
        totalPlayCount: Int,
        totalDurationMs: Long,
        topTracks: List<TrackPlayStatRow>,
        topArtists: List<ArtistPlayStatRow>
    ): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.BLACK)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#242424")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            style = Paint.Style.FILL
        }
        val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val barTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222222")
            style = Paint.Style.FILL
        }

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E8E93")
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.10f
        }
        val heroMetricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 94f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val rowTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val rowSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9A9AA0")
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6E6E73")
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        canvas.drawRoundRect(RectF(36f, 36f, width - 36f, height - 36f), 44f, 44f, borderPaint)

        canvas.drawText("ONDA", 84f, 128f, brandPaint)
        canvas.drawText("OFFLINE OLED RECAP · ${rangeLabel.uppercase(Locale.US)}", 84f, 174f, labelPaint)

        val hoursText = formatRecapDuration(totalDurationMs)
        canvas.drawText(hoursText, 84f, 304f, heroMetricPaint)
        canvas.drawText("$totalPlayCount PLAYS · LOCAL-FIRST · ZERO TELEMETRY", 84f, 356f, labelPaint)

        var y = 412f
        val tracksCardRect = RectF(72f, y, width - 72f, y + 430f)
        canvas.drawRoundRect(tracksCardRect, 32f, 32f, cardFillPaint)
        canvas.drawRoundRect(tracksCardRect, 32f, 32f, borderPaint)
        canvas.drawText("TOP TRACKS", 108f, y + 54f, labelPaint)

        val shownTracks = topTracks.take(5)
        if (shownTracks.isEmpty()) {
            canvas.drawText("No listening history yet", 108f, y + 128f, rowSubPaint)
        } else {
            shownTracks.forEachIndexed { idx, item ->
                val rowY = y + 114f + idx * 66f
                canvas.drawText(String.format(Locale.US, "%02d", idx + 1), 108f, rowY, rankPaint)
                val titleText = ellipsize(item.title, 28)
                val metaText = "${ellipsize(item.artist, 22)} · ${formatRecapDuration(item.totalDurationMs)}"
                canvas.drawText(titleText, 172f, rowY - 4f, rowTitlePaint)
                canvas.drawText(metaText, 172f, rowY + 26f, rowSubPaint)
            }
        }

        y = 874f
        val artistsCardRect = RectF(72f, y, width - 72f, y + 340f)
        canvas.drawRoundRect(artistsCardRect, 32f, 32f, cardFillPaint)
        canvas.drawRoundRect(artistsCardRect, 32f, 32f, borderPaint)
        canvas.drawText("TOP ARTISTS", 108f, y + 54f, labelPaint)

        val shownArtists = topArtists.take(4)
        val maxArtistDuration = shownArtists.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L
        if (shownArtists.isEmpty()) {
            canvas.drawText("Play tracks to build your local artist breakdown", 108f, y + 128f, rowSubPaint)
        } else {
            shownArtists.forEachIndexed { idx, item ->
                val rowY = y + 112f + idx * 58f
                canvas.drawText(ellipsize(item.artist, 26), 108f, rowY, rowTitlePaint)
                val durStr = formatRecapDuration(item.totalDurationMs)
                val durWidth = labelPaint.measureText(durStr)
                canvas.drawText(durStr, width - 108f - durWidth, rowY, labelPaint)
                val barTop = rowY + 12f
                val barLeft = 108f
                val barRight = width - 108f
                canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barTop + 8f), 4f, 4f, barTrackPaint)
                val frac = (item.totalDurationMs.toFloat() / maxArtistDuration.toFloat()).coerceIn(0.08f, 1f)
                canvas.drawRoundRect(
                    RectF(barLeft, barTop, barLeft + (barRight - barLeft) * frac, barTop + 8f),
                    4f,
                    4f,
                    barFillPaint
                )
            }
        }

        canvas.drawText("ONDA MUSIC v1.2.9 · PURE #000000 OLED", 84f, height - 74f, labelPaint)
        return bitmap
    }

    private fun formatRecapDuration(ms: Long): String {
        val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%dh %02dm", hours, minutes)
        } else {
            "${minutes}m"
        }
    }

    private fun ellipsize(text: String, maxChars: Int): String {
        val clean = text.trim()
        return if (clean.length <= maxChars) clean else clean.take(maxChars - 1) + "..."
    }
}
