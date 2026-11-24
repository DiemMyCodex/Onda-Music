package com.onda.music.feature.player.share

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StyleSpan
import android.text.style.UpdateAppearance
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.onda.music.core.design.ArtworkUrlResolver
import com.onda.music.core.design.OndaArtworkPalette
import com.onda.music.core.model.LyricsLine
import com.onda.music.core.model.Track
import com.onda.music.feature.player.formatDurationMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

const val ONDA_CARD_WIDTH = 1080
const val ONDA_CARD_HEIGHT = 1920

suspend fun renderOndaShareBitmap(
    context: Context,
    track: Track,
    palette: OndaArtworkPalette,
    template: OndaCardTemplate,
    backdrop: OndaCardBackdrop,
    surfaceTone: OndaCardSurfaceTone,
    cardOpacity: Float,
    selectedLines: List<LyricsLine>,
    wordEmphases: Map<String, OndaWordEmphasis>,
    posterHeaderTop: Boolean
): Bitmap {
    val artworkBitmap = loadArtworkBitmap(context, track.artworkUrl)
    return withContext(Dispatchers.Default) {
        val output = Bitmap.createBitmap(ONDA_CARD_WIDTH, ONDA_CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        drawCardBackdrop(
            canvas = canvas,
            backdrop = backdrop,
            artworkBitmap = artworkBitmap,
            palette = palette
        )
        when (template) {
            OndaCardTemplate.VINYL_TICKET -> drawVinylTicketTemplate(
                canvas = canvas,
                track = track,
                palette = palette,
                backdrop = backdrop,
                artworkBitmap = artworkBitmap
            )
            OndaCardTemplate.EDITORIAL_LYRIC -> drawEditorialLyricTemplate(
                canvas = canvas,
                track = track,
                palette = palette,
                backdrop = backdrop,
                surfaceTone = surfaceTone,
                cardOpacity = cardOpacity,
                selectedLines = selectedLines,
                artworkBitmap = artworkBitmap
            )
            OndaCardTemplate.STAGE_POSTER -> drawStagePosterTemplate(
                canvas = canvas,
                track = track,
                palette = palette,
                backdrop = backdrop,
                selectedLines = selectedLines,
                wordEmphases = wordEmphases,
                posterHeaderTop = posterHeaderTop,
                artworkBitmap = artworkBitmap
            )
        }
        output
    }
}

suspend fun saveCardToGallery(
    context: Context,
    bitmap: Bitmap,
    trackTitle: String
): Result<Uri> = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val safeTitle = sanitizeFileSegment(trackTitle)
        val filename = "onda_soundcard_${safeTitle}_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Onda")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create gallery media entry")
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    error("Unable to encode PNG image")
                }
            } ?: error("Unable to open gallery output stream")
            val readyValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, readyValues, null, null)
            uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }
}

suspend fun shareCardImage(
    context: Context,
    bitmap: Bitmap,
    trackTitle: String
): Result<Unit> {
    val fileResult = withContext(Dispatchers.IO) {
        runCatching {
            val cacheDir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
            val cutoff = System.currentTimeMillis() - 86_400_000L
            cacheDir.listFiles()?.forEach { existing ->
                if (existing.name.startsWith("onda_card_") && existing.lastModified() < cutoff) {
                    existing.delete()
                }
            }
            val outputFile = File(cacheDir, "onda_card_${System.currentTimeMillis()}.png")
            FileOutputStream(outputFile).use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    error("Unable to encode shared PNG")
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
        }
    }
    return fileResult.mapCatching { uri ->
        withContext(Dispatchers.Main) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, trackTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "Onda Soundcard", uri)
            }
            val chooserIntent = Intent.createChooser(sendIntent, "Share Soundcard").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooserIntent)
        }
    }
}

private suspend fun loadArtworkBitmap(
    context: Context,
    artworkUrl: String?
): Bitmap? = withContext(Dispatchers.IO) {
    if (artworkUrl.isNullOrBlank()) return@withContext null
    val loader = SingletonImageLoader.get(context)
    val upgradedUrl = ArtworkUrlResolver.upgradeHighRes(artworkUrl)
    val candidates = listOfNotNull(upgradedUrl, artworkUrl).distinct()
    for (candidate in candidates) {
        val bitmap = runCatching {
            val request = ImageRequest.Builder(context)
                .data(candidate)
                .allowHardware(false)
                .size(1080)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val raw = result.image.toBitmap()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && raw.config == Bitmap.Config.HARDWARE) {
                    raw.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    raw
                }
            } else {
                null
            }
        }.getOrNull()
        if (bitmap != null) {
            return@withContext bitmap
        }
    }
    null
}

private fun sanitizeFileSegment(raw: String): String {
    val cleaned = raw.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
    return if (cleaned.isEmpty()) "track" else cleaned.take(32)
}

private fun drawCardBackdrop(
    canvas: Canvas,
    backdrop: OndaCardBackdrop,
    artworkBitmap: Bitmap?,
    palette: OndaArtworkPalette
) {
    val w = ONDA_CARD_WIDTH.toFloat()
    val h = ONDA_CARD_HEIGHT.toFloat()
    val primaryArgb = composeColorToArgb(palette.primary)
    val tertiaryArgb = composeColorToArgb(palette.tertiary)

    when (backdrop) {
        OndaCardBackdrop.ARTWORK_MIST -> {
            canvas.drawColor(0xFF0A0A0C.toInt())
            if (artworkBitmap != null) {
                val blurred = createSoftMistBitmap(artworkBitmap)
                val destRect = RectF(0f, 0f, w, h)
                val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = 148
                }
                drawCenterCroppedBitmap(canvas, blurred, destRect, bitmapPaint)
                blurred.recycle()
            }
            val mistTop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.18f,
                    h * 0.20f,
                    w * 0.95f,
                    intArrayOf(withArgbAlpha(primaryArgb, 0.34f), 0x000A0A0C),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, mistTop)

            val mistBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.85f,
                    h * 0.80f,
                    w * 0.90f,
                    intArrayOf(withArgbAlpha(tertiaryArgb, 0.36f), 0x000A0A0C),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, mistBottom)

            val vignette = Paint().apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    h,
                    intArrayOf(0x8A070709.toInt(), 0x44070709, 0x99070709.toInt()),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, vignette)
        }
        OndaCardBackdrop.OLED_SOLID -> {
            canvas.drawColor(0xFF050507.toInt())
            val subtleGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.5f,
                    h * 0.12f,
                    w * 0.85f,
                    intArrayOf(0x10FFFFFF, 0x00050507),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, subtleGlow)
        }
        OndaCardBackdrop.ACOUSTIC_GRID -> {
            canvas.drawColor(0xFF0A0B0E.toInt())
            val accentWash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.5f,
                    h * 0.35f,
                    w * 0.9f,
                    intArrayOf(withArgbAlpha(primaryArgb, 0.14f), 0x000A0B0E),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, accentWash)

            val minorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x12FFFFFF
                strokeWidth = 1f
            }
            val majorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x24FFFFFF
                strokeWidth = 1.5f
            }
            val step = 72f
            var x = 0f
            var col = 0
            while (x <= w) {
                canvas.drawLine(x, 0f, x, h, if (col % 4 == 0) majorPaint else minorPaint)
                x += step
                col++
            }
            var y = 0f
            var row = 0
            while (y <= h) {
                canvas.drawLine(0f, y, w, y, if (row % 4 == 0) majorPaint else minorPaint)
                y += step
                row++
            }
            drawAcousticCornerTicks(canvas, w, h)
        }
        OndaCardBackdrop.WARM_PAPER -> {
            canvas.drawColor(0xFFEDE6D8.toInt())
            val paperSheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.5f,
                    h * 0.42f,
                    h * 0.72f,
                    intArrayOf(0xFFF5F0E5.toInt(), 0xFFE3DAC9.toInt()),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, paperSheen)
            val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = 0x1E141414
            }
            canvas.drawRect(36f, 36f, w - 36f, h - 36f, framePaint)
        }
    }
}

private fun drawAcousticCornerTicks(canvas: Canvas, w: Float, h: Float) {
    val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x52FFFFFF
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    val margin = 44f
    val len = 26f
    canvas.drawLine(margin, margin, margin + len, margin, tickPaint)
    canvas.drawLine(margin, margin, margin, margin + len, tickPaint)

    canvas.drawLine(w - margin - len, margin, w - margin, margin, tickPaint)
    canvas.drawLine(w - margin, margin, w - margin, margin + len, tickPaint)

    canvas.drawLine(margin, h - margin, margin + len, h - margin, tickPaint)
    canvas.drawLine(margin, h - margin - len, margin, h - margin, tickPaint)

    canvas.drawLine(w - margin - len, h - margin, w - margin, h - margin, tickPaint)
    canvas.drawLine(w - margin, h - margin - len, w - margin, h - margin, tickPaint)
}

private fun drawVinylTicketTemplate(
    canvas: Canvas,
    track: Track,
    palette: OndaArtworkPalette,
    backdrop: OndaCardBackdrop,
    artworkBitmap: Bitmap?
) {
    val cardRect = RectF(108f, 250f, 972f, 1670f)
    val cardRadius = 42f
    val isPaper = backdrop == OndaCardBackdrop.WARM_PAPER

    if (isPaper) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x1F141414
        }
        val shadowRect = RectF(cardRect.left, cardRect.top + 14f, cardRect.right, cardRect.bottom + 14f)
        canvas.drawRoundRect(shadowRect, cardRadius, cardRadius, shadowPaint)
    }

    val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF131316.toInt()
    }
    canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardBgPaint)

    val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x26FFFFFF
    }
    canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardBorderPaint)

    val innerLeft = cardRect.left + 58f
    val innerRight = cardRect.right - 58f
    val innerWidth = (innerRight - innerLeft).roundToInt()

    val monoEyebrowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 21f
        color = 0xFF8A8A94.toInt()
        letterSpacing = 0.16f
    }
    canvas.drawText("SOUNDCARD", innerLeft, cardRect.top + 68f, monoEyebrowPaint)

    val serialNum = (abs(track.id.hashCode()) % 9000) + 1000
    val serialText = "NO.$serialNum"
    val serialWidth = monoEyebrowPaint.measureText(serialText)
    canvas.drawText(serialText, innerRight - serialWidth, cardRect.top + 68f, monoEyebrowPaint)

    val artTop = cardRect.top + 106f
    val artSize = innerRight - innerLeft
    val artRect = RectF(innerLeft, artTop, innerRight, artTop + artSize)
    drawRoundedArtwork(
        canvas = canvas,
        artworkBitmap = artworkBitmap,
        rect = artRect,
        cornerRadius = 26f,
        markColor = 0x99FFFFFF.toInt()
    )

    val dividerY = artRect.bottom + 46f
    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x29FFFFFF
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }
    canvas.drawLine(innerLeft, dividerY, innerRight, dividerY, dividerPaint)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 44f
        color = 0xFFFFFFFF.toInt()
        letterSpacing = -0.02f
    }
    val titleLayout = buildStaticLayout(
        text = track.title,
        paint = titlePaint,
        widthPx = innerWidth,
        maxLines = 2,
        lineSpacingMult = 1.12f
    )
    val titleY = dividerY + 34f
    canvas.save()
    canvas.translate(innerLeft, titleY)
    titleLayout.draw(canvas)
    canvas.restore()

    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textSize = 29f
        color = 0xFFA2A2AC.toInt()
    }
    val artistLayout = buildStaticLayout(
        text = track.artist,
        paint = artistPaint,
        widthPx = innerWidth,
        maxLines = 1,
        lineSpacingMult = 1.1f
    )
    val artistY = titleY + titleLayout.height + 14f
    canvas.save()
    canvas.translate(innerLeft, artistY)
    artistLayout.draw(canvas)
    canvas.restore()

    val telemetryY = artistY + artistLayout.height + 34f
    val durationText = formatDurationMs(track.durationMs)
    val durationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 25f
        color = 0xFFFFFFFF.toInt()
        fontFeatureSettings = "tnum"
    }
    canvas.drawText(durationText, innerLeft, telemetryY + 26f, durationPaint)

    val pillText = track.source.name
    val pillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 20f
        color = 0xFFD4D4DC.toInt()
        letterSpacing = 0.08f
    }
    val pillTextW = pillPaint.measureText(pillText)
    val pillPadX = 20f
    val pillHeight = 40f
    val pillRect = RectF(
        innerRight - pillTextW - pillPadX * 2f,
        telemetryY - 2f,
        innerRight,
        telemetryY - 2f + pillHeight
    )
    val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1C1C22.toInt()
    }
    val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0x33FFFFFF
    }
    canvas.drawRoundRect(pillRect, 999f, 999f, pillBgPaint)
    canvas.drawRoundRect(pillRect, 999f, 999f, pillStrokePaint)
    canvas.drawText(pillText, pillRect.left + pillPadX, telemetryY + 25f, pillPaint)

    val bars = buildOndaWaveformBars(track.id, 36)
    val waveTop = telemetryY + 58f
    val waveHeight = 58f
    val wavePitch = (innerRight - innerLeft) / bars.size.toFloat()
    val barWidth = (wavePitch * 0.56f).coerceAtLeast(6f)
    val activeAccent = ensureDarkAccent(composeColorToArgb(palette.primary))
    val activeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = activeAccent
    }
    val inactiveBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF383842.toInt()
    }
    val splitIndex = (bars.size * 0.62f).roundToInt()
    bars.forEachIndexed { idx, level ->
        val bh = (waveHeight * level).coerceAtLeast(10f)
        val cx = innerLeft + idx * wavePitch + barWidth * 0.5f
        val barRect = RectF(
            cx - barWidth * 0.5f,
            waveTop + (waveHeight - bh) * 0.5f,
            cx + barWidth * 0.5f,
            waveTop + (waveHeight + bh) * 0.5f
        )
        canvas.drawRoundRect(
            barRect,
            barWidth * 0.5f,
            barWidth * 0.5f,
            if (idx < splitIndex) activeBarPaint else inactiveBarPaint
        )
    }

    val footerRuleY = cardRect.bottom - 102f
    val footerRulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1FFFFFFF
        strokeWidth = 1.5f
    }
    canvas.drawLine(innerLeft, footerRuleY, innerRight, footerRuleY, footerRulePaint)

    val footerCenterY = (footerRuleY + cardRect.bottom) * 0.5f
    drawOndaSplitOMark(
        canvas = canvas,
        left = innerLeft,
        top = footerCenterY - 19f,
        size = 38f,
        tintArgb = 0xFFFFFFFF.toInt()
    )
    val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 23f
        color = 0xFFFFFFFF.toInt()
        letterSpacing = 0.24f
    }
    canvas.drawText("ONDA", innerLeft + 52f, footerCenterY + 8f, brandPaint)

    val archText = "ACOUSTIC ARCH"
    val archPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 18f
        color = 0xFF7A7A86.toInt()
        letterSpacing = 0.12f
    }
    val archW = archPaint.measureText(archText)
    canvas.drawText(archText, innerRight - archW, footerCenterY + 7f, archPaint)
}

private fun drawEditorialLyricTemplate(
    canvas: Canvas,
    track: Track,
    palette: OndaArtworkPalette,
    backdrop: OndaCardBackdrop,
    surfaceTone: OndaCardSurfaceTone,
    cardOpacity: Float,
    selectedLines: List<LyricsLine>,
    artworkBitmap: Bitmap?
) {
    val style = resolveEditorialSurfaceStyle(
        backdrop = backdrop,
        surfaceTone = surfaceTone,
        cardOpacity = cardOpacity,
        palette = palette
    )

    val cardRect = RectF(96f, 270f, 984f, 1650f)
    val cardRadius = 44f
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.fillArgb
    }
    canvas.drawRoundRect(cardRect, cardRadius, cardRadius, fillPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = style.borderArgb
    }
    canvas.drawRoundRect(cardRect, cardRadius, cardRadius, borderPaint)

    val innerLeft = cardRect.left + 68f
    val innerRight = cardRect.right - 68f
    val innerWidth = (innerRight - innerLeft).roundToInt()

    val accentBarColor = if (style.isLightSurface) {
        withArgbAlpha(style.primaryInkArgb, 0.72f)
    } else {
        ensureDarkAccent(composeColorToArgb(palette.primary))
    }
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentBarColor
    }
    canvas.drawRoundRect(
        RectF(innerLeft, cardRect.top + 68f, innerLeft + 54f, cardRect.top + 76f),
        4f,
        4f,
        barPaint
    )

    val eyebrowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 20f
        color = style.secondaryInkArgb
        letterSpacing = 0.16f
    }
    canvas.drawText("LYRIC EXCERPT", innerLeft + 74f, cardRect.top + 79f, eyebrowPaint)

    val footerTop = cardRect.bottom - 188f
    val lyricAreaTop = cardRect.top + 132f
    val lyricAreaHeight = (footerTop - 42f - lyricAreaTop).coerceAtLeast(300f)

    val displayLines = if (selectedLines.isNotEmpty()) {
        selectedLines
    } else {
        listOf(LyricsLine(0L, 0L, track.title))
    }

    val fontCandidates = floatArrayOf(56f, 48f, 42f, 36f, 32f, 28f)
    var chosenSize = 28f
    var chosenBlocks: List<Pair<StaticLayout, StaticLayout?>> = emptyList()
    var totalStackHeight = 0

    for (sizeCandidate in fontCandidates) {
        val mainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = sizeCandidate
            color = style.primaryInkArgb
            letterSpacing = -0.02f
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = (sizeCandidate * 0.56f).coerceAtLeast(20f)
            color = style.secondaryInkArgb
        }
        val blocks = displayLines.map { line ->
            val mainLayout = buildStaticLayout(
                text = line.text.trim(),
                paint = mainPaint,
                widthPx = innerWidth,
                maxLines = 6,
                lineSpacingMult = 1.15f
            )
            val subText = listOfNotNull(
                line.translatedText?.takeIf { it.isNotBlank() },
                line.romanizedText?.takeIf { it.isNotBlank() }
            ).joinToString("\n")
            val subLayout = if (subText.isNotBlank()) {
                buildStaticLayout(
                    text = subText,
                    paint = subPaint,
                    widthPx = innerWidth,
                    maxLines = 4,
                    lineSpacingMult = 1.15f
                )
            } else {
                null
            }
            mainLayout to subLayout
        }
        val lineGap = (sizeCandidate * 0.55f).roundToInt()
        val subGap = (sizeCandidate * 0.22f).roundToInt()
        val heightSum = blocks.sumOf { (mainL, subL) ->
            mainL.height + (if (subL != null) subGap + subL.height else 0)
        } + (blocks.size - 1).coerceAtLeast(0) * lineGap
        chosenSize = sizeCandidate
        chosenBlocks = blocks
        totalStackHeight = heightSum
        if (heightSum <= lyricAreaHeight) {
            break
        }
    }

    var cursorY = lyricAreaTop + ((lyricAreaHeight - totalStackHeight).coerceAtLeast(0f) * 0.45f)
    val lineGap = (chosenSize * 0.55f)
    val subGap = (chosenSize * 0.22f)
    for ((mainL, subL) in chosenBlocks) {
        if (cursorY + mainL.height > footerTop - 16f) break
        canvas.save()
        canvas.translate(innerLeft, cursorY)
        mainL.draw(canvas)
        canvas.restore()
        cursorY += mainL.height
        if (subL != null && cursorY + subGap + subL.height <= footerTop - 12f) {
            cursorY += subGap
            canvas.save()
            canvas.translate(innerLeft, cursorY)
            subL.draw(canvas)
            canvas.restore()
            cursorY += subL.height
        }
        cursorY += lineGap
    }

    val footerRulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.borderArgb
        strokeWidth = 1.5f
    }
    canvas.drawLine(innerLeft, footerTop, innerRight, footerTop, footerRulePaint)

    val thumbSize = 86f
    val thumbTop = footerTop + 42f
    val thumbRect = RectF(innerLeft, thumbTop, innerLeft + thumbSize, thumbTop + thumbSize)
    drawRoundedArtwork(
        canvas = canvas,
        artworkBitmap = artworkBitmap,
        rect = thumbRect,
        cornerRadius = 18f,
        markColor = style.secondaryInkArgb
    )

    val brandReserve = 148f
    val metaLeft = thumbRect.right + 24f
    val metaWidth = (innerRight - brandReserve - metaLeft).roundToInt().coerceAtLeast(120)

    val trackTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 27f
        color = style.primaryInkArgb
    }
    val trackTitleLayout = buildStaticLayout(
        text = track.title,
        paint = trackTitlePaint,
        widthPx = metaWidth,
        maxLines = 1,
        lineSpacingMult = 1.05f
    )
    canvas.save()
    canvas.translate(metaLeft, thumbTop + 10f)
    trackTitleLayout.draw(canvas)
    canvas.restore()

    val trackArtistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textSize = 22f
        color = style.secondaryInkArgb
    }
    val trackArtistLayout = buildStaticLayout(
        text = track.artist,
        paint = trackArtistPaint,
        widthPx = metaWidth,
        maxLines = 1,
        lineSpacingMult = 1.05f
    )
    canvas.save()
    canvas.translate(metaLeft, thumbTop + 46f)
    trackArtistLayout.draw(canvas)
    canvas.restore()

    val stampCenterY = thumbTop + thumbSize * 0.5f
    val ondaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 21f
        color = style.primaryInkArgb
        letterSpacing = 0.22f
    }
    val ondaW = ondaPaint.measureText("ONDA")
    val markSize = 32f
    val markLeft = innerRight - ondaW - markSize - 12f
    drawOndaSplitOMark(
        canvas = canvas,
        left = markLeft,
        top = stampCenterY - markSize * 0.5f,
        size = markSize,
        tintArgb = style.primaryInkArgb
    )
    canvas.drawText("ONDA", innerRight - ondaW, stampCenterY + 7f, ondaPaint)
}

private fun drawStagePosterTemplate(
    canvas: Canvas,
    track: Track,
    palette: OndaArtworkPalette,
    backdrop: OndaCardBackdrop,
    selectedLines: List<LyricsLine>,
    wordEmphases: Map<String, OndaWordEmphasis>,
    posterHeaderTop: Boolean,
    artworkBitmap: Bitmap?
) {
    val w = ONDA_CARD_WIDTH.toFloat()
    val h = ONDA_CARD_HEIGHT.toFloat()
    val isLightBackdrop = backdrop == OndaCardBackdrop.WARM_PAPER

    val primaryInk = if (isLightBackdrop) 0xFF111113.toInt() else 0xFFFFFFFF.toInt()
    val mutedLyricInk = if (isLightBackdrop) 0xCC1C1C20.toInt() else 0xD9FFFFFF.toInt()
    val secondaryInk = if (isLightBackdrop) 0xB01C1C20.toInt() else 0xB3FFFFFF.toInt()
    val borderInk = if (isLightBackdrop) 0x26111113 else 0x2EFFFFFF
    val accentArgb = composeColorToArgb(palette.primary)
    val glowColor = if (isLightBackdrop) {
        withArgbAlpha(accentArgb, 0.55f)
    } else {
        if (computeArgbLuminance(accentArgb) > 0.25f) {
            withArgbAlpha(accentArgb, 0.90f)
        } else {
            0xCCFFFFFF.toInt()
        }
    }

    if (!isLightBackdrop) {
        val posterMist = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.5f,
                h * 0.48f,
                w * 0.85f,
                intArrayOf(withArgbAlpha(accentArgb, 0.16f), 0x00000000),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, posterMist)
    }

    val marginX = 80f
    val contentWidth = (w - marginX * 2f).roundToInt()

    val headerY = if (posterHeaderTop) 108f else h - 220f
    val signatureY = if (posterHeaderTop) h - 122f else 118f

    drawPosterHeaderBar(
        canvas = canvas,
        left = marginX,
        top = headerY,
        width = w - marginX * 2f,
        track = track,
        artworkBitmap = artworkBitmap,
        primaryInk = primaryInk,
        secondaryInk = secondaryInk,
        borderInk = borderInk
    )

    drawPosterCornerSignature(
        canvas = canvas,
        left = marginX,
        right = w - marginX,
        baselineY = signatureY,
        primaryInk = primaryInk,
        secondaryInk = secondaryInk
    )

    val lyricTop = if (posterHeaderTop) headerY + 156f else signatureY + 72f
    val lyricBottom = if (posterHeaderTop) signatureY - 96f else headerY - 76f
    val lyricMaxHeight = (lyricBottom - lyricTop).coerceAtLeast(420f)

    val linesToRender = if (selectedLines.isNotEmpty()) {
        selectedLines
    } else {
        listOf(LyricsLine(0L, 0L, track.title))
    }

    val spannable = SpannableStringBuilder()
    linesToRender.forEachIndexed { lineIdx, line ->
        if (lineIdx > 0) {
            spannable.append("\n")
        }
        val words = OndaLyricSelectionLogic.splitWords(line.text)
        if (words.isEmpty()) {
            val start = spannable.length
            spannable.append(line.text)
            spannable.setSpan(
                ForegroundColorSpan(mutedLyricInk),
                start,
                spannable.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else {
            words.forEachIndexed { wordIdx, word ->
                if (wordIdx > 0) {
                    spannable.append(" ")
                }
                val emphasis = wordEmphases[wordEmphasisKey(lineIdx, wordIdx)] ?: OndaWordEmphasis.NONE
                val renderedWord = if (emphasis == OndaWordEmphasis.CAPS) {
                    word.uppercase(Locale.getDefault())
                } else {
                    word
                }
                val start = spannable.length
                spannable.append(renderedWord)
                val end = spannable.length
                when (emphasis) {
                    OndaWordEmphasis.NONE -> {
                        spannable.setSpan(
                            ForegroundColorSpan(mutedLyricInk),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    OndaWordEmphasis.BOLD -> {
                        spannable.setSpan(
                            StyleSpan(Typeface.BOLD),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        spannable.setSpan(
                            ForegroundColorSpan(primaryInk),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    OndaWordEmphasis.CAPS -> {
                        spannable.setSpan(
                            CapsWordSpan(primaryInk),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    OndaWordEmphasis.LUMINOUS -> {
                        val luminousInk = if (isLightBackdrop) 0xFF0A0A0C.toInt() else 0xFFFFFFFF.toInt()
                        spannable.setSpan(
                            LuminousWordSpan(luminousInk, glowColor),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
        }
    }

    val sizeSteps = floatArrayOf(78f, 68f, 58f, 50f, 44f, 38f, 32f)
    var bestLayout: StaticLayout? = null
    for (candidateSize in sizeSteps) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = candidateSize
            color = primaryInk
            letterSpacing = -0.02f
        }
        val layout = StaticLayout.Builder.obtain(
            spannable,
            0,
            spannable.length,
            textPaint,
            contentWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.16f)
            .setIncludePad(false)
            .build()
        bestLayout = layout
        if (layout.height <= lyricMaxHeight) {
            break
        }
    }

    bestLayout?.let { layout ->
        val yOffset = lyricTop + ((lyricMaxHeight - layout.height).coerceAtLeast(0f) * 0.48f)
        canvas.save()
        canvas.translate(marginX, yOffset)
        layout.draw(canvas)
        canvas.restore()
    }
}

private fun drawPosterHeaderBar(
    canvas: Canvas,
    left: Float,
    top: Float,
    width: Float,
    track: Track,
    artworkBitmap: Bitmap?,
    primaryInk: Int,
    secondaryInk: Int,
    borderInk: Int
) {
    val thumbSize = 92f
    val thumbRect = RectF(left, top, left + thumbSize, top + thumbSize)
    drawRoundedArtwork(
        canvas = canvas,
        artworkBitmap = artworkBitmap,
        rect = thumbRect,
        cornerRadius = 20f,
        markColor = secondaryInk
    )

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = borderInk
    }
    canvas.drawRoundRect(thumbRect, 20f, 20f, borderPaint)

    val textLeft = thumbRect.right + 24f
    val textWidth = (left + width - textLeft).roundToInt().coerceAtLeast(120)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 31f
        color = primaryInk
    }
    val titleLayout = buildStaticLayout(
        text = track.title,
        paint = titlePaint,
        widthPx = textWidth,
        maxLines = 1,
        lineSpacingMult = 1.05f
    )
    canvas.save()
    canvas.translate(textLeft, top + 12f)
    titleLayout.draw(canvas)
    canvas.restore()

    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textSize = 24f
        color = secondaryInk
    }
    val artistLayout = buildStaticLayout(
        text = track.artist,
        paint = artistPaint,
        widthPx = textWidth,
        maxLines = 1,
        lineSpacingMult = 1.05f
    )
    canvas.save()
    canvas.translate(textLeft, top + 52f)
    artistLayout.draw(canvas)
    canvas.restore()
}

private fun drawPosterCornerSignature(
    canvas: Canvas,
    left: Float,
    right: Float,
    baselineY: Float,
    primaryInk: Int,
    secondaryInk: Int
) {
    val markSize = 36f
    drawOndaSplitOMark(
        canvas = canvas,
        left = left,
        top = baselineY - markSize + 8f,
        size = markSize,
        tintArgb = primaryInk
    )
    val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 22f
        color = primaryInk
        letterSpacing = 0.24f
    }
    canvas.drawText("ONDA", left + markSize + 14f, baselineY, brandPaint)

    val tagText = "SOUNDCARD"
    val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 19f
        color = secondaryInk
        letterSpacing = 0.16f
    }
    val tagW = tagPaint.measureText(tagText)
    canvas.drawText(tagText, right - tagW, baselineY, tagPaint)
}

private fun drawRoundedArtwork(
    canvas: Canvas,
    artworkBitmap: Bitmap?,
    rect: RectF,
    cornerRadius: Float,
    markColor: Int
) {
    val clipPath = Path().apply {
        addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
    }
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(0xFF242429.toInt(), 0xFF141418.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
    }
    canvas.save()
    canvas.clipPath(clipPath)
    canvas.drawRect(rect, bgPaint)
    if (artworkBitmap != null) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        drawCenterCroppedBitmap(canvas, artworkBitmap, rect, paint)
    } else {
        val markSize = minOf(rect.width(), rect.height()) * 0.34f
        drawOndaSplitOMark(
            canvas = canvas,
            left = rect.centerX() - markSize * 0.5f,
            top = rect.centerY() - markSize * 0.5f,
            size = markSize,
            tintArgb = markColor
        )
    }
    canvas.restore()

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0x24FFFFFF
    }
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
}

private fun drawCenterCroppedBitmap(
    canvas: Canvas,
    bitmap: Bitmap,
    dstRect: RectF,
    paint: Paint
) {
    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    if (bw <= 0f || bh <= 0f || dstRect.width() <= 0f || dstRect.height() <= 0f) return

    val targetAspect = dstRect.width() / dstRect.height()
    val bitmapAspect = bw / bh
    val srcRect = if (bitmapAspect > targetAspect) {
        val cropW = bh * targetAspect
        val left = ((bw - cropW) * 0.5f).roundToInt()
        Rect(left, 0, (left + cropW).roundToInt().coerceAtMost(bitmap.width), bitmap.height)
    } else {
        val cropH = bw / targetAspect
        val top = ((bh - cropH) * 0.5f).roundToInt()
        Rect(0, top, bitmap.width, (top + cropH).roundToInt().coerceAtMost(bitmap.height))
    }
    canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
}

private fun createSoftMistBitmap(source: Bitmap): Bitmap {
    val w = 48
    val h = 48
    val small = Bitmap.createScaledBitmap(source, w, h, true)
    val working = if (small.config != Bitmap.Config.ARGB_8888) {
        val copy = small.copy(Bitmap.Config.ARGB_8888, true)
        small.recycle()
        copy
    } else {
        small
    }
    val pixels = IntArray(w * h)
    val temp = IntArray(w * h)
    working.getPixels(pixels, 0, w, 0, 0, w, h)
    repeat(3) {
        boxBlurHorizontal(pixels, temp, w, h, 4)
        boxBlurVertical(temp, pixels, w, h, 4)
    }
    working.setPixels(pixels, 0, w, 0, 0, w, h)
    return working
}

private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val div = radius * 2 + 1
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            for (dx in -radius..radius) {
                val sx = (x + dx).coerceIn(0, w - 1)
                val c = src[row + sx]
                r += (c ushr 16) and 0xFF
                g += (c ushr 8) and 0xFF
                b += c and 0xFF
            }
            dst[row + x] = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
        }
    }
}

private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val div = radius * 2 + 1
    for (x in 0 until w) {
        for (y in 0 until h) {
            var r = 0
            var g = 0
            var b = 0
            for (dy in -radius..radius) {
                val sy = (y + dy).coerceIn(0, h - 1)
                val c = src[sy * w + x]
                r += (c ushr 16) and 0xFF
                g += (c ushr 8) and 0xFF
                b += c and 0xFF
            }
            dst[y * w + x] = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
        }
    }
}

private fun drawOndaSplitOMark(
    canvas: Canvas,
    left: Float,
    top: Float,
    size: Float,
    tintArgb: Int
) {
    if (size <= 0f) return
    val scale = size / 24f
    val cx = left + size * 0.5f
    val cy = top + size * 0.5f
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintArgb
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawCircle(cx, cy, 8.5f * scale, strokePaint)

    val wavePath = Path().apply {
        moveTo(left + 4.5f * scale, top + 12.5f * scale)
        cubicTo(
            left + 7.0f * scale,
            top + 7.2f * scale,
            left + 9.8f * scale,
            top + 7.2f * scale,
            left + 12.0f * scale,
            top + 12.0f * scale
        )
        cubicTo(
            left + 14.2f * scale,
            top + 16.8f * scale,
            left + 17.0f * scale,
            top + 16.8f * scale,
            left + 19.5f * scale,
            top + 11.5f * scale
        )
    }
    canvas.drawPath(wavePath, strokePaint)

    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tintArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(left + 15.5f * scale, top + 8.6f * scale, 1.05f * scale, dotPaint)
}

private fun buildStaticLayout(
    text: String,
    paint: TextPaint,
    widthPx: Int,
    maxLines: Int,
    lineSpacingMult: Float
): StaticLayout {
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, widthPx))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, lineSpacingMult)
        .setIncludePad(false)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
}

private fun ensureDarkAccent(argb: Int): Int {
    return if (computeArgbLuminance(argb) >= 0.22f) {
        argb
    } else {
        blendArgbOver(0xFFFFFF, argb, 0.45f)
    }
}

private class LuminousWordSpan(
    private val inkColor: Int,
    private val glowColor: Int
) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) {
        tp.color = inkColor
        tp.isFakeBoldText = true
        tp.setShadowLayer(24f, 0f, 0f, glowColor)
    }
}

private class CapsWordSpan(
    private val inkColor: Int
) : MetricAffectingSpan() {
    override fun updateMeasureState(textPaint: TextPaint) {
        textPaint.isFakeBoldText = true
        textPaint.letterSpacing = 0.08f
    }

    override fun updateDrawState(tp: TextPaint) {
        tp.color = inkColor
        tp.isFakeBoldText = true
        tp.letterSpacing = 0.08f
    }
}
