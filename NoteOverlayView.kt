package com.notevid.app

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Touch surface placed above Media3's TextureView. No notes are burned into the video. */
class NoteOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var notes: List<VideoNote> = emptyList()
        set(value) { field = value; invalidate() }
    var timeMs: Long = 0L
        set(value) { field = value; invalidate() }
    var isEditing: Boolean = false
        set(value) { field = value; if (!value) { selectedId = null; moveMode = false }; invalidate() }
    var videoAspectRatio: Float = 16f / 9f
        set(value) { field = if (value > 0f) value else 16f / 9f; invalidate() }

    var onNewBox: ((RectF) -> Unit)? = null
    var onSelect: ((VideoNote) -> Unit)? = null
    var onPositionChanged: ((VideoNote) -> Unit)? = null

    private var selectedId: String? = null
    private var moveMode = false
    private val d = resources.displayMetrics.density
    private var touchStart = PointF()
    private var initialBox = RectF()
    private var preview: RectF? = null
    private var candidateNote: VideoNote? = null
    private enum class TouchMode { NONE, CREATE, TAP, MOVE, RESIZE }
    private var touchMode = TouchMode.NONE

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 205, 250); style = Paint.Style.STROKE; strokeWidth = 2f * d
        pathEffect = DashPathEffect(floatArrayOf(8f * d, 5f * d), 0f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 205, 250) }

    fun select(id: String?) { selectedId = id; invalidate() }
    fun moveSelected() { moveMode = true; invalidate() }

    private fun imageRect(): RectF {
        val rw = width.toFloat().coerceAtLeast(1f)
        val rh = height.toFloat().coerceAtLeast(1f)
        val contentW: Float
        val contentH: Float
        if (rw / rh > videoAspectRatio) {
            contentH = rh; contentW = rh * videoAspectRatio
        } else {
            contentW = rw; contentH = rw / videoAspectRatio
        }
        return RectF((rw - contentW) / 2f, (rh - contentH) / 2f,
            (rw + contentW) / 2f, (rh + contentH) / 2f)
    }

    private fun toView(note: VideoNote, image: RectF) = RectF(
        image.left + note.x * image.width(), image.top + note.y * image.height(),
        image.left + (note.x + note.w) * image.width(),
        image.top + (note.y + note.h) * image.height()
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = imageRect()
        for (n in notes) {
            if (timeMs !in n.startMs until n.endMs && (!isEditing || selectedId != n.id)) continue
            val box = toView(n, image)
            drawNote(canvas, n, box, image)
            if (isEditing && selectedId == n.id) {
                canvas.drawRect(box, markerPaint)
                val radius = 7f * d
                canvas.drawCircle(box.right, box.bottom, radius, handlePaint)
            }
        }
        preview?.let {
            fillPaint.color = Color.argb(100, 255, 255, 255)
            fillPaint.style = Paint.Style.FILL
            canvas.drawRect(it, fillPaint)
            canvas.drawRect(it, markerPaint)
        }
    }

    private fun drawNote(canvas: Canvas, note: VideoNote, box: RectF, image: RectF) {
        fillPaint.color = note.style.background
        fillPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(box, 4f * d, 4f * d, fillPaint)
        if (note.text.isBlank()) return
        val padding = max(4f * d, 5f * d * image.width() / (360f * d))
        val available = (box.width() - padding * 2f).toInt().coerceAtLeast(1)
        val typeface = when {
            note.style.weight >= 650 -> Typeface.create("sans-serif", Typeface.BOLD)
            note.style.weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val scale = (image.width() / (360f * d)).coerceIn(0.65f, 2.0f)
        val size = note.style.fontSp * resources.displayMetrics.scaledDensity * scale
        fun textPaint(color: Int, stroke: Boolean): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            this.typeface = typeface
            style = if (stroke) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = note.style.outlineDp * d * scale
            strokeJoin = Paint.Join.ROUND
            isSubpixelText = true
        }
        fun layout(paint: TextPaint) = StaticLayout.Builder.obtain(note.text, 0, note.text.length, paint, available)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()
        val stroke = layout(textPaint(note.style.outlineColor, true))
        val fill = layout(textPaint(note.style.textColor, false))
        canvas.save()
        canvas.clipRect(box)
        canvas.translate(box.left + padding, box.top + padding)
        if (note.style.outlineDp > 0f) stroke.draw(canvas)
        fill.draw(canvas)
        canvas.restore()
    }

    private fun visibleNoteAt(x: Float, y: Float, image: RectF): VideoNote? {
        return notes.asReversed().firstOrNull { n ->
            (timeMs in n.startMs until n.endMs || n.id == selectedId) && toView(n, image).contains(x, y)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditing) return false
        val image = imageRect()
        val x = event.x.coerceIn(image.left, image.right)
        val y = event.y.coerceIn(image.top, image.bottom)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!image.contains(event.x, event.y)) return false
                touchStart = PointF(x, y)
                val selected = notes.find { it.id == selectedId }
                val rect = selected?.let { toView(it, image) }
                val hitHandle = rect != null && abs(x - rect.right) < 20f * d && abs(y - rect.bottom) < 20f * d
                candidateNote = if (hitHandle) selected else visibleNoteAt(x, y, image)
                touchMode = when {
                    hitHandle -> TouchMode.RESIZE
                    candidateNote == null -> TouchMode.CREATE
                    moveMode -> TouchMode.MOVE
                    else -> TouchMode.TAP
                }
                if (touchMode == TouchMode.MOVE || touchMode == TouchMode.RESIZE) {
                    initialBox = RectF(toView(candidateNote!!, image))
                }
                preview = if (touchMode == TouchMode.CREATE) RectF(x, y, x, y) else null
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                when (touchMode) {
                    TouchMode.CREATE -> preview = RectF(min(touchStart.x, x), min(touchStart.y, y), max(touchStart.x, x), max(touchStart.y, y))
                    TouchMode.MOVE -> candidateNote?.let { n ->
                        val dx = (x - touchStart.x) / image.width()
                        val dy = (y - touchStart.y) / image.height()
                        n.x = (initialBox.left - image.left) / image.width() + dx
                        n.y = (initialBox.top - image.top) / image.height() + dy
                        n.clampPosition()
                    }
                    TouchMode.RESIZE -> candidateNote?.let { n ->
                        n.w = (initialBox.width() + x - touchStart.x) / image.width()
                        n.h = (initialBox.height() + y - touchStart.y) / image.height()
                        n.clampPosition()
                    }
                    else -> Unit
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_UP -> {
                when (touchMode) {
                    TouchMode.CREATE -> {
                        val rect = preview ?: RectF(x, y, x, y)
                        if (rect.width() < 30f * d || rect.height() < 25f * d) {
                            rect.set(x, y, min(image.right, x + 180f * d), min(image.bottom, y + 74f * d))
                        }
                        val normalized = RectF(
                            (rect.left - image.left) / image.width(),
                            (rect.top - image.top) / image.height(),
                            (rect.right - image.left) / image.width(),
                            (rect.bottom - image.top) / image.height()
                        )
                        onNewBox?.invoke(normalized)
                    }
                    TouchMode.TAP -> candidateNote?.let { select(it.id); onSelect?.invoke(it) }
                    TouchMode.MOVE, TouchMode.RESIZE -> candidateNote?.let { onPositionChanged?.invoke(it) }
                    else -> Unit
                }
                preview = null; touchMode = TouchMode.NONE; moveMode = false
                invalidate(); return true
            }
            MotionEvent.ACTION_CANCEL -> {
                preview = null; touchMode = TouchMode.NONE; moveMode = false
                invalidate(); return true
            }
        }
        return true
    }
}
