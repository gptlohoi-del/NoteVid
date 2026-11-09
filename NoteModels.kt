package com.notevid.app

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** All coordinates are normalized to the decoded video image (0..1), never to the phone screen. */
data class NoteStyle(
    var background: Int = Color.WHITE,
    var textColor: Int = Color.BLACK,
    var outlineColor: Int = Color.rgb(255, 196, 0),
    var outlineDp: Float = 2.2f,
    var fontSp: Float = 20f,
    var weight: Int = 500
) {
    fun copyStyle() = copy()

    fun toJson() = JSONObject().apply {
        put("background", background)
        put("textColor", textColor)
        put("outlineColor", outlineColor)
        put("outlineDp", outlineDp.toDouble())
        put("fontSp", fontSp.toDouble())
        put("weight", weight)
    }

    companion object {
        fun fromJson(o: JSONObject?): NoteStyle {
            if (o == null) return NoteStyle()
            return NoteStyle(
                o.optInt("background", Color.WHITE),
                o.optInt("textColor", Color.BLACK),
                o.optInt("outlineColor", Color.rgb(255, 196, 0)),
                o.optDouble("outlineDp", 2.2).toFloat().coerceIn(0f, 12f),
                o.optDouble("fontSp", 20.0).toFloat().coerceIn(10f, 52f),
                o.optInt("weight", 500).coerceIn(400, 700)
            )
        }
    }
}

data class VideoNote(
    val id: String = UUID.randomUUID().toString(),
    var startMs: Long,
    var endMs: Long,
    var text: String,
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float,
    var style: NoteStyle = NoteStyle()
) {
    fun clampPosition() {
        w = w.coerceIn(0.05f, 1f)
        h = h.coerceIn(0.05f, 1f)
        x = x.coerceIn(0f, 1f - w)
        y = y.coerceIn(0f, 1f - h)
        startMs = max(0L, startMs)
        endMs = max(startMs + 100L, endMs)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("startMs", startMs)
        put("endMs", endMs)
        put("text", text)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("w", w.toDouble())
        put("h", h.toDouble())
        put("style", style.toJson())
    }

    companion object {
        fun fromJson(o: JSONObject): VideoNote {
            val note = VideoNote(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                startMs = o.optLong("startMs", 0L),
                endMs = o.optLong("endMs", 5000L),
                text = o.optString("text", ""),
                x = o.optDouble("x", 0.1).toFloat(),
                y = o.optDouble("y", 0.1).toFloat(),
                w = o.optDouble("w", 0.5).toFloat(),
                h = o.optDouble("h", 0.2).toFloat(),
                style = NoteStyle.fromJson(o.optJSONObject("style"))
            )
            note.clampPosition()
            return note
        }
    }
}

object NoteJson {
    const val SCHEMA = 1

    fun encode(videoName: String, notes: List<VideoNote>): String = JSONObject().apply {
        put("app", "NoteVid")
        put("schema", SCHEMA)
        put("videoName", videoName)
        put("positionMode", "normalized-video-frame")
        put("notes", JSONArray().apply {
            notes.sortedWith(compareBy<VideoNote> { it.startMs }.thenBy { it.id })
                .forEach { put(it.toJson()) }
        })
    }.toString(2)

    fun decode(json: String): MutableList<VideoNote> {
        val root = JSONObject(json)
        require(root.optInt("schema", -1) == SCHEMA) {
            "Định dạng ghi chú chưa được hỗ trợ (schema ${root.optInt("schema", -1)})."
        }
        val array = root.optJSONArray("notes") ?: JSONArray()
        val notes = mutableListOf<VideoNote>()
        for (i in 0 until array.length()) notes.add(VideoNote.fromJson(array.getJSONObject(i)))
        return notes
    }
}

object Timecode {
    fun format(ms: Long): String {
        val safe = max(0L, ms)
        val cs = (safe % 1000L) / 10L
        val sec = (safe / 1000L) % 60L
        val min = (safe / 60_000L) % 60L
        val hour = safe / 3_600_000L
        return "%02d:%02d:%02d.%02d".format(java.util.Locale.US, hour, min, sec, cs)
    }

    /** Accepts HH:MM:SS.cs / MM:SS.cs / SS.cs, with up to three fractional digits. */
    fun parse(value: String): Long? {
        val parts = value.trim().split(':')
        if (parts.size !in 1..3) return null
        val before = parts.dropLast(1)
        if (before.any { it.toLongOrNull() == null }) return null
        val secParts = parts.last().split('.')
        if (secParts.size !in 1..2) return null
        val seconds = secParts[0].toLongOrNull() ?: return null
        val fraction = if (secParts.size == 2) secParts[1] else ""
        if (fraction.length > 3 || fraction.any { !it.isDigit() }) return null
        val fracMs = fraction.padEnd(3, '0').toLongOrNull() ?: 0L
        val m = if (before.isNotEmpty()) before.last().toLong() else 0L
        val h = if (before.size == 2) before[0].toLong() else 0L
        if (seconds !in 0..59 && parts.size > 1) return null
        if (m !in 0..59 || h < 0L) return null
        if (seconds < 0) return null
        return try { Math.addExact(Math.addExact(Math.multiplyExact(h, 3_600_000L), Math.multiplyExact(m, 60_000L)), Math.addExact(Math.multiplyExact(seconds, 1000L), fracMs)) }
        catch (_: ArithmeticException) { null }
    }
}

object VersionCheck {
    fun parse(input: String): List<Int>? {
        val m = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(input.trim()) ?: return null
        return m.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    }
    fun isNewer(remote: String, local: String): Boolean {
        val r = parse(remote) ?: return false
        val l = parse(local) ?: return false
        for (i in 0..2) if (r[i] != l[i]) return r[i] > l[i]
        return false
    }
}
