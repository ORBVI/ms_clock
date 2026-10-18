package com.huanshi.timesync

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.min

/** Entire target is drawn in one Canvas pass, with no independently animated elements. */
class SyncTargetView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var light = false
    var focused = false
    var minimal = true
    var redDigits = true
    private val digitBounds = Rect()
    private var elapsed = 0L
    private var sequence = 0L
    private var interval = 0.0
    private var paused = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mono = Typeface.create("monospace", Typeface.BOLD)
    private val sans = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private var timeLabel = "00:00.000"
    private var sequenceLabel = "00000000"
    private var rateLabel = "刷新测量中"
    private var background = 0
    private var foreground = 0
    private var muted = 0
    private var accent = 0

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "同步计时器，点击显示控制栏"
    }

    fun update(ms: Long, frame: Long, frameInterval: Double, isPaused: Boolean) {
        elapsed = ms.coerceAtLeast(0)
        sequence = frame
        interval = frameInterval
        paused = isPaused
        timeLabel = String.format(Locale.US, "%02d:%02d.%03d",
            elapsed / 60_000, elapsed / 1000 % 60, elapsed % 1000)
        sequenceLabel = frame.toString().padStart(8, '0')
        rateLabel = if (interval > 0) String.format(Locale.US, "%.1f Hz  /  %.2f ms", 1000 / interval, interval)
            else "刷新测量中"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (minimal) {
            drawMinimal(canvas)
            return
        }
        background = Color.rgb(if (light) 242 else 12, if (light) 242 else 15, if (light) 235 else 15)
        foreground = if (light) Color.rgb(15, 20, 18) else Color.rgb(246, 247, 237)
        muted = if (light) Color.rgb(80, 91, 83) else Color.rgb(151, 164, 152)
        accent = if (light) Color.rgb(47, 104, 32) else Color.rgb(200, 243, 105)
        canvas.drawColor(background)
        // Fixed design coordinates preserve proportions on phones, tablets and rotation.
        val landscape = width > height
        val designW = if (landscape) 1100f else 640f
        val designH = if (landscape) 580f else 900f
        val scale = min(width / designW, height / designH)
        canvas.save()
        canvas.translate((width - designW * scale) / 2, (height - designH * scale) / 2)
        canvas.scale(scale, scale)
        val left = 32f
        val right = designW - 32f
        val wide = right - left
        text(canvas, "HS / SYNC", left, 46f, 22f, foreground)
        text(canvas, if (paused) "● 已暂停" else "● 正在运行", right, 46f, 17f, accent, align = Paint.Align.RIGHT)
        line(canvas, left, 68f, right, 68f)
        text(canvas, "四相机同步测试靶", left, 104f, 16f, muted)
        text(canvas, "01 — 04 / SAME DISPLAY", right, 104f, if (landscape) 14f else 12f, muted, align = Paint.Align.RIGHT)
        val timeY = if (landscape) 237f else 315f
        text(canvas, "ELAPSED / 累计时间", left, timeY - if (landscape) 102f else 108f, 14f, accent)
        paint.typeface = mono
        paint.textSize = if (landscape) 120f else 69f
        val timeSize = min(paint.textSize, paint.textSize * wide / paint.measureText(timeLabel))
        text(canvas, timeLabel, designW / 2, timeY, timeSize, foreground, true, Paint.Align.CENTER)
        text(canvas, "分 : 秒 . 毫秒", right, timeY + 29f, 14f, muted, align = Paint.Align.RIGHT)
        val scanY = if (landscape) 307f else 409f
        text(canvas, "相位扫描 / 每秒一周", left, scanY - 17f, 14f, muted)
        text(canvas, "%03d ms".format(Locale.US, elapsed % 1000), right, scanY - 17f, 16f, foreground, true, Paint.Align.RIGHT)
        rect(canvas, left, scanY, right, scanY + 49, if (light) 0xffd8ddd1.toInt() else 0xff252d27.toInt())
        for (i in 0..20) {
            val x = left + wide * i / 20
            line(canvas, x, scanY + 53, x, scanY + if (i % 5 == 0) 66 else 60)
        }
        val barWidth = wide / 40
        val x = left + (wide - barWidth) * (elapsed % 1000) / 999f
        rect(canvas, x, scanY, x + barWidth, scanY + 49, foreground)
        for (i in 0..4) text(canvas, "${i * 250}", left + wide * i / 4, scanY + 85, 12f, muted, true,
            if (i == 4) Paint.Align.RIGHT else if (i == 0) Paint.Align.LEFT else Paint.Align.CENTER)
        val codeY = if (landscape) 442f else 604f
        text(canvas, "绘制序号", left, codeY - 16, 14f, muted)
        text(canvas, sequenceLabel, left, codeY + 27, if (landscape) 36f else 32f, foreground, true)
        val codeLeft = if (landscape) 400f else 295f
        text(canvas, "16-BIT GRAY / 低位编码", codeLeft, codeY - 16, 12f, muted)
        val gap = 3f
        val cell = (right - codeLeft) / 16
        val gray = (sequence xor (sequence ushr 1)) and 0xffff
        for (i in 0 until 16) {
            val on = gray and (1L shl (15 - i)) != 0L
            val cellX = codeLeft + cell * i
            rect(canvas, cellX, codeY, cellX + cell - gap, codeY + 34, foreground)
            if (!on) rect(canvas, cellX + 1.5f, codeY + 1.5f, cellX + cell - gap - 1.5f, codeY + 32.5f, background)
        }
        val footer = designH - 54
        line(canvas, left, footer - 18, right, footer - 18)
        text(canvas, if (paused) "PAUSED / 画面冻结" else rateLabel, left, footer + 5, 14f, accent, true)
        text(canvas, "同屏 · 同帧 · 对比读数", right, footer + 5, 14f, muted, align = Paint.Align.RIGHT)
        text(canvas, if (focused) "点击画面恢复控制栏" else "毫秒读数 ≠ 毫秒刷新精度；请对比相同高度的区域", designW / 2, designH - 12, 12f, muted, align = Paint.Align.CENTER)
        canvas.restore()
    }

    /** Stable-width cells keep digits still even when an OEM substitutes the font. */
    private fun drawMinimal(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        paint.typeface = mono
        paint.textSize = 100f
        val digitWidth = ('0'..'9').maxOf { paint.measureText(it.toString()) }
        val separatorWidth = digitWidth * 0.48f
        val referenceWidth = timeLabel.sumOf {
            (if (it.isDigit()) digitWidth else separatorWidth).toDouble()
        }.toFloat()
        paint.getTextBounds("0123456789", 0, 10, digitBounds)
        val fit = min(width * 0.94f / referenceWidth, height * 0.82f / digitBounds.height())
        paint.textSize = 100f * fit
        paint.getTextBounds("0123456789", 0, 10, digitBounds)
        val baseline = height / 2f - (digitBounds.top + digitBounds.bottom) / 2f
        paint.color = if (redDigits) Color.rgb(255, 48, 56) else Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        var x = (width - referenceWidth * fit) / 2f
        for (character in timeLabel) {
            val cellWidth = (if (character.isDigit()) digitWidth else separatorWidth) * fit
            canvas.drawText(character.toString(), x + cellWidth / 2, baseline, paint)
            x += cellWidth
        }
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int,
                     monospaced: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        paint.color = color
        paint.typeface = if (monospaced) mono else sans
        paint.textSize = size
        paint.textAlign = align
        c.drawText(value, x, y, paint)
    }

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        paint.color = color
        c.drawRect(l, t, r, b, paint)
    }

    private fun line(c: Canvas, x: Float, y: Float, endX: Float, endY: Float) {
        paint.color = muted
        paint.strokeWidth = 1f
        c.drawLine(x, y, endX, endY, paint)
    }
}
