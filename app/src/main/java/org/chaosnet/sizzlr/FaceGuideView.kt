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

    // All coordinates are in a 0–100 × 0–100 space, scaled to view pixels by sx/sy.
    //
    // Human proportions used:
    //   Top of cranium  y=8
    //   Temple / brow   y=28  x=27–73  (widest cranium point)
    //   Cheekbones      y=46  x=24–76  (widest face point)
    //   Jaw corners     y=67  x=36–64
    //   Chin centre     y=83  (rounded arc, NOT a single point)
    //   Eye level       y=38  (~40 % of face height below top)
    //   Nose base       y=58  (~65 % of face height)
    //   Mouth           y=70  (~80 % of face height)

    private fun buildFacePath(w: Int, h: Int): Path = Path().apply {
        // Start: left temple
        moveTo(sx(27f, w), sy(28f, h))
        // ── Dome of skull to right temple ──────────────────────────────────
        cubicTo(
            sx(26f, w), sy(8f, h),
            sx(74f, w), sy(8f, h),
            sx(73f, w), sy(28f, h)
        )
        // ── Right side: temple → cheekbone → jaw corner ────────────────────
        cubicTo(
            sx(77f, w), sy(40f, h),   // cheekbone flare
            sx(74f, w), sy(58f, h),   // cheek taper
            sx(64f, w), sy(67f, h)    // jaw corner
        )
        // ── Right jaw corner → right chin ──────────────────────────────────
        cubicTo(
            sx(60f, w), sy(76f, h),
            sx(56f, w), sy(83f, h),
            sx(50f, w), sy(84f, h)    // chin centre — rounded, not a point
        )
        // ── Left chin → left jaw corner ────────────────────────────────────
        cubicTo(
            sx(44f, w), sy(83f, h),
            sx(40f, w), sy(76f, h),
            sx(36f, w), sy(67f, h)    // jaw corner
        )
        // ── Left jaw corner → cheekbone → temple ───────────────────────────
        cubicTo(
            sx(26f, w), sy(58f, h),   // cheek taper
            sx(23f, w), sy(40f, h),   // cheekbone flare
            sx(27f, w), sy(28f, h)    // back to start
        )
        close()
    }

    private fun buildLeftEarPath(w: Int, h: Int): Path = Path().apply {
        // Ear sits at cheekbone level (y 36–52), just outside the face edge (x≈27)
        moveTo(sx(27f, w), sy(36f, h))
        cubicTo(
            sx(21f, w), sy(36f, h),
            sx(21f, w), sy(52f, h),
            sx(27f, w), sy(52f, h)
        )
    }

    private fun buildRightEarPath(w: Int, h: Int): Path = Path().apply {
        moveTo(sx(73f, w), sy(36f, h))
        cubicTo(
            sx(79f, w), sy(36f, h),
            sx(79f, w), sy(52f, h),
            sx(73f, w), sy(52f, h)
        )
    }

    private fun buildLeftEyePath(w: Int, h: Int): Path = Path().apply {
        // Left eye centred at (40, 38), width ≈ 11 units
        moveTo(sx(34f, w), sy(38f, h))
        quadTo(sx(40f, w), sy(33f, h), sx(46f, w), sy(38f, h))
        quadTo(sx(40f, w), sy(43f, h), sx(34f, w), sy(38f, h))
    }

    private fun buildRightEyePath(w: Int, h: Int): Path = Path().apply {
        // Right eye centred at (60, 38), width ≈ 11 units
        moveTo(sx(54f, w), sy(38f, h))
        quadTo(sx(60f, w), sy(33f, h), sx(66f, w), sy(38f, h))
        quadTo(sx(60f, w), sy(43f, h), sx(54f, w), sy(38f, h))
    }

    private fun buildNosePath(w: Int, h: Int): Path = Path().apply {
        // Bridge drops from between the eyes; nostrils flare slightly at base
        moveTo(sx(50f, w), sy(41f, h))
        lineTo(sx(50f, w), sy(57f, h))
        lineTo(sx(46f, w), sy(61f, h))   // left nostril
        moveTo(sx(54f, w), sy(61f, h))   // right nostril
        lineTo(sx(50f, w), sy(57f, h))
    }

    private fun buildMouthPath(w: Int, h: Int): Path = Path().apply {
        // Lips — wider than before to match cheekbone width
        moveTo(sx(40f, w), sy(70f, h))
        quadTo(sx(50f, w), sy(75f, h), sx(60f, w), sy(70f, h))
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
