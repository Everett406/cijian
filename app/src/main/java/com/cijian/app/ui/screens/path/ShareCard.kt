package com.cijian.app.ui.screens.path

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File

/**
 * 词义之路分享卡：1080×1350 宣纸底卡片，墨字朱印，系统分享面板输出 PNG。
 */
object ShareCard {

    private val PAPER = 0xFFF6F2E7.toInt()
    private val INK = 0xFF2A2722.toInt()
    private val INK_SOFT = 0xFF72695B.toInt()
    private val CINNABAR = 0xFFB23A2E.toInt()
    private val INDIGO = 0xFF35565A.toInt()

    fun share(context: Context, from: String, to: String, words: List<String>) {
        val bitmap = render(from, to, words)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "cijian_path_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享词义之路"))
    }

    private fun render(from: String, to: String, words: List<String>): Bitmap {
        val W = 1080; val H = 1350
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(PAPER)

        // 细墨边框
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; style = Paint.Style.STROKE; strokeWidth = 3f }
        c.drawRect(36f, 36f, W - 36f, H - 36f, frame)

        val serif = Typeface.SERIF
        fun paint(color: Int, size: Float, bold: Boolean = false): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; textSize = size
                typeface = if (bold) Typeface.create(serif, Typeface.BOLD) else serif
            }

        // 印章 + 标题
        val seal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CINNABAR }
        c.drawRoundRect(72f, 84f, 152f, 164f, 12f, 12f, seal)
        val sealChar = paint(Color.WHITE, 56f, bold = true)
        val tw = sealChar.measureText("词")
        c.drawText("词", 112f - tw / 2, 148f, sealChar)

        val title = paint(INK, 52f, bold = true)
        c.drawText("词间 · 词义之路", 188f, 140f, title)

        // 起终点
        val big = paint(INK, 92f, bold = true)
        val bigRed = paint(CINNABAR, 92f, bold = true)
        c.drawText(from, 76f, 320f, bigRed)
        val arrow = paint(INK_SOFT, 64f)
        c.drawText("走 到", 76f + bigRed.measureText(from) + 60f, 312f, arrow)
        c.drawText(to, 76f, 440f, big)

        // 分隔细线
        val line = Paint().apply { color = 0x33B9B0A0.toInt(); strokeWidth = 2f }
        c.drawLine(72f, 500f, W - 72f, 500f, line)

        // 站点（最多 12 站）
        val stopPaint = paint(INK, 46f)
        val idxPaint = paint(CINNABAR, 34f, bold = true)
        var y = 580f
        for (i in 1 until words.size - 1) {
            if (i > 12) break
            c.drawCircle(92f, y - 16f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CINNABAR })
            c.drawText(i.toString().padStart(2, '0'), 124f, y - 4f, idxPaint)
            c.drawText(words[i], 190f, y, stopPaint)
            y += 62f
        }
        if (words.size > 13) {
            val more = paint(INK_SOFT, 36f)
            c.drawText("… 另有 ${words.size - 13} 站未列出", 190f, y, more)
        }

        // 底注
        val foot = paint(INK_SOFT, 30f)
        c.drawText("腾讯 AI Lab 中文词向量 · 143,613 词 × 200 维", 76f, H - 84f, foot)
        c.drawText("词间 CiJian", W - 76f - foot.measureText("词间 CiJian"), H - 84f, foot)
        return bmp
    }
}
