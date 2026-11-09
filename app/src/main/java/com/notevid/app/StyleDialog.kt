package com.notevid.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlin.math.roundToInt

/** Same editor is used for app defaults and for per-note overrides. */
object StyleDialog {
    private val colorOptions = listOf(
        "Trắng" to Color.WHITE,
        "Đen" to Color.BLACK,
        "Vàng" to Color.rgb(255, 196, 0),
        "Đỏ" to Color.rgb(220, 42, 42),
        "Xanh lá" to Color.rgb(34, 150, 88),
        "Xanh dương" to Color.rgb(35, 102, 206),
        "Xanh nhạt" to Color.rgb(207, 236, 255),
        "Hồng nhạt" to Color.rgb(255, 218, 225),
        "Trong suốt" to Color.TRANSPARENT
    )

    fun show(context: Context, initial: NoteStyle, onSave: (NoteStyle) -> Unit) {
        val state = initial.copyStyle()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = context.dp(18)
            setPadding(p, context.dp(8), p, context.dp(8))
        }
        val scroll = ScrollView(context).apply { addView(root) }
        fun colorRow(title: String, get: () -> Int, set: (Int) -> Unit) {
            val btn = Button(context)
            fun refresh() {
                val name = colorOptions.firstOrNull { it.second == get() }?.first ?: "#%08X".format(get())
                btn.text = "$title: $name"
            }
            refresh()
            btn.setOnClickListener {
                val names = colorOptions.map { it.first }.toTypedArray()
                AlertDialog.Builder(context).setTitle(title).setItems(names) { _, index ->
                    set(colorOptions[index].second); refresh()
                }.setNeutralButton("Mã màu HEX") { _, _ ->
                    val edit = EditText(context).apply {
                        setSingleLine(true)
                        setText("#%08X".format(get()))
                        setSelection(text.length)
                    }
                    AlertDialog.Builder(context).setTitle("Nhập #RRGGBB hoặc #AARRGGBB")
                        .setView(edit)
                        .setPositiveButton("Áp dụng") { _, _ ->
                            try { set(Color.parseColor(edit.text.toString())); refresh() }
                            catch (_: IllegalArgumentException) { Toast.makeText(context, "Mã màu không hợp lệ", Toast.LENGTH_SHORT).show() }
                        }.setNegativeButton("Hủy", null).show()
                }.show()
            }
            root.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        colorRow("Nền chữ", { state.background }, { state.background = it })
        colorRow("Màu chữ", { state.textColor }, { state.textColor = it })
        colorRow("Stroke quanh ký tự", { state.outlineColor }, { state.outlineColor = it })

        fun slider(label: String, initialProgress: Int, maxProgress: Int, onChange: (Int) -> Unit) {
            val info = TextView(context).apply { text = "$label: $initialProgress"; textSize = 14f }
            root.addView(info)
            val sb = SeekBar(context).apply {
                max = maxProgress
                progress = initialProgress.coerceIn(0, maxProgress)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                        info.text = "$label: $value"
                        onChange(value)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                })
            }
            root.addView(sb)
        }
        slider("Cỡ chữ (sp)", state.fontSp.roundToInt(), 52) { state.fontSp = it.coerceAtLeast(10).toFloat() }
        slider("Độ dày stroke (0,1 dp)", (state.outlineDp * 10f).roundToInt(), 100) {
            state.outlineDp = it / 10f
        }
        root.addView(TextView(context).apply { text = "Độ đậm chữ"; textSize = 14f })
        val spinner = Spinner(context)
        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf("Thường (400)", "Vừa (500)", "Đậm (700)"))
        spinner.setSelection(when { state.weight >= 650 -> 2; state.weight >= 500 -> 1; else -> 0 })
        root.addView(spinner)
        AlertDialog.Builder(context).setTitle("Kiểu ghi chú")
            .setView(scroll)
            .setPositiveButton("Lưu") { _, _ ->
                state.weight = when (spinner.selectedItemPosition) { 0 -> 400; 2 -> 700; else -> 500 }
                onSave(state)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}

fun Context.dp(amount: Int): Int = (amount * resources.displayMetrics.density).roundToInt()
