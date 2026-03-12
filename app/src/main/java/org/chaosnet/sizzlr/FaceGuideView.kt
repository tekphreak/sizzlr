package org.chaosnet.sizzlr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class FaceGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val vignetteP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(178, 0, 0, 0) // ~70% black
        style = Paint.Style.FILL
    }

    private val outlineP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(178, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
        strokeCap = Paint.Cap.ROUND
    }

    private val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 200, 200, 200)
        textSize = 28f
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.15f
    }

    private val labelBgP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(153, 0, 0, 0)
        style = Paint.Style.FILL
    }

    // Scale a point from the 0-100 SVG coordinate space to view coordinates
    private fun sx(x: Float, w: Int) = x / 100f * w
    private fun sy(y: Float, h: Int) = y / 100f * h

    private fun buildFacePath(w: Int, h: Int): Path = Path().apply {
        // M32,30 C32,10 68,10 68,30 V50 C68,75 50,90 50,90 C50,90 32,75 32,50 Z
        moveTo(sx(32f, w), sy(30f, h))
        cubicTo(
            sx(32f, w), sy(10f, h),
            sx(68f, w), sy(10f, h),
            sx(68f, w), sy(30f, h)
        )
        lineTo(sx(68f, w), sy(50f, h))
        cubicTo(
            sx(68f, w), sy(75f, h),
            sx(50f, w), sy(90f, h),
            sx(50f, w), sy(90f, h)
        )
        cubicTo(
            sx(50f, w), sy(90f, h),
            sx(32f, w), sy(75f, h),
            sx(32f, w), sy(50f, h)
        )
        close()
    }

    private fun buildLeftEarPath(w: Int, h: Int): Path = Path().apply {
        // M32,40 C28,40 28,55 32,55
        moveTo(sx(32f, w), sy(40f, h))
        cubicTo(
            sx(28f, w), sy(40f, h),
            sx(28f, w), sy(55f, h),
            sx(32f, w), sy(55f, h)
        )
    }

    private fun buildRightEarPath(w: Int, h: Int): Path = Path().apply {
        // M68,40 C72,40 72,55 68,55
        moveTo(sx(68f, w), sy(40f, h))
        cubicTo(
            sx(72f, w), sy(40f, h),
            sx(72f, w), sy(55f, h),
            sx(68f, w), sy(55f, h)
        )
    }

    private fun buildLeftEyePath(w: Int, h: Int): Path = Path().apply {
        // M38,45 Q42,42 46,45 Q42,48 38,45
        moveTo(sx(38f, w), sy(45f, h))
        quadTo(sx(42f, w), sy(42f, h), sx(46f, w), sy(45f, h))
        quadTo(sx(42f, w), sy(48f, h), sx(38f, w), sy(45f, h))
    }

    private fun buildRightEyePath(w: Int, h: Int): Path = Path().apply {
        // M54,45 Q58,42 62,45 Q58,48 54,45
        moveTo(sx(54f, w), sy(45f, h))
        quadTo(sx(58f, w), sy(42f, h), sx(62f, w), sy(45f, h))
        quadTo(sx(58f, w), sy(48f, h), sx(54f, w), sy(45f, h))
    }

    private fun buildNosePath(w: Int, h: Int): Path = Path().apply {
        // M50,45 V58 L47,61  +  M53,61 L50,58
        moveTo(sx(50f, w), sy(45f, h))
        lineTo(sx(50f, w), sy(58f, h))
        lineTo(sx(47f, w), sy(61f, h))
        moveTo(sx(53f, w), sy(61f, h))
        lineTo(sx(50f, w), sy(58f, h))
    }

    private fun buildMouthPath(w: Int, h: Int): Path = Path().apply {
        // M42,72 Q50,76 58,72
        moveTo(sx(42f, w), sy(72f, h))
        quadTo(sx(50f, w), sy(76f, h), sx(58f, w), sy(72f, h))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w == 0 || h == 0) return

        val facePath = buildFacePath(w, h)

        // Dark vignette everywhere except inside the face
        canvas.save()
        canvas.clipOutPath(facePath)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignetteP)
        canvas.restore()

        // Face outline (dotted)
        canvas.drawPath(facePath, outlineP)

        // Ears
        canvas.drawPath(buildLeftEarPath(w, h), outlineP)
        canvas.drawPath(buildRightEarPath(w, h), outlineP)

        // Eyes
        canvas.drawPath(buildLeftEyePath(w, h), outlineP)
        canvas.drawPath(buildRightEyePath(w, h), outlineP)

        // Nose
        canvas.drawPath(buildNosePath(w, h), outlineP)

        // Mouth
        canvas.drawPath(buildMouthPath(w, h), outlineP)

        // "Align Face Here" label near bottom
        val labelText = "ALIGN FACE HERE"
        val labelY = h * 0.92f
        val textWidth = labelP.measureText(labelText)
        val padding = 16f
        canvas.drawRoundRect(
            w / 2f - textWidth / 2f - padding,
            labelY - labelP.textSize - 4f,
            w / 2f + textWidth / 2f + padding,
            labelY + 8f,
            12f, 12f,
            labelBgP
        )
        canvas.drawText(labelText, w / 2f, labelY, labelP)
    }
}
