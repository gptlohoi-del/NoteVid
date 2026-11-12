package com.notevid.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.*
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.util.Locale

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : Activity() {
    private val folderRequest = 7001
    private val videoRequest = 7002
    private val prefs by lazy { getSharedPreferences("notevid", 0) }
    private lateinit var store: NoteStore
    private lateinit var updates: UpdateManager
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var overlay: NoteOverlayView
    private lateinit var focusTapTarget: View
    private lateinit var title: TextView
    private lateinit var topScroll: HorizontalScrollView
    private lateinit var instructions: TextView
    private lateinit var clock: TextView
    private lateinit var seek: SeekBar
    private lateinit var progressRow: LinearLayout
    private lateinit var playbackRow: LinearLayout
    private lateinit var notesRow: LinearLayout
    private lateinit var btnPlay: Button
    private lateinit var btnEdit: Button
    private lateinit var btnSpeed: Button
    private lateinit var btnLoop: Button
    private lateinit var btnOrientation: Button
    private var folderUri: Uri? = null
    private var currentVideo: NoteStore.VideoFile? = null
    private val notes = mutableListOf<VideoNote>()
    private var editing = false
    private var menusHidden = false
    private var focusBackCallback: OnBackInvokedCallback? = null
    private var isSeeking = false
    private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)
    private var speedIndex = 2
    private var loopA: Long? = null
    private var loopB: Long? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (::player.isInitialized) {
                val position = player.currentPosition.coerceAtLeast(0L)
                val a = loopA; val b = loopB
                if (a != null && b != null && b > a && position >= b && player.isPlaying) player.seekTo(a)
                overlay.timeMs = player.currentPosition.coerceAtLeast(0L)
                if (!isSeeking) {
                    val duration = player.duration
                    seek.progress = if (duration > 0 && duration != C.TIME_UNSET)
                        ((position.toDouble() / duration) * 1000).toInt().coerceIn(0, 1000) else 0
                }
                clock.text = "${Timecode.format(position)} / ${if (player.duration > 0) Timecode.format(player.duration) else "--:--:--.--"}"
                btnPlay.text = if (player.isPlaying) "Ⅱ Dừng" else "▶ Phát"
            }
            handler.postDelayed(this, 80)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = NoteStore(this)
        updates = UpdateManager(this)
        player = ExoPlayer.Builder(this).build()
        createUi()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val name = currentVideo?.name ?: "video"
                showError(
                    "Không phát được $name.\n\n" +
                    "Mã lỗi Media3: ${error.errorCodeName}\n" +
                    "Chi tiết: ${error.cause?.message ?: error.message}\n\n" +
                    "Hãy thử video MP4 mã hóa H.264/AAC. Nếu vẫn lỗi, gửi ảnh thông báo này để mình kiểm tra."
                )
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val w = videoSize.width * videoSize.pixelWidthHeightRatio
                val h = videoSize.height.toFloat()
                if (w <= 0 || h <= 0) return
                val ratio = if (videoSize.unappliedRotationDegrees % 180 != 0) h / w else w / h
                overlay.videoAspectRatio = ratio
            }
        })
        overlay.onNewBox = { box -> createNote(box) }
        overlay.onSelect = { note -> noteTools(note) }
        overlay.onPositionChanged = { saveNotes() }
        handler.post(ticker)
        folderUri = prefs.getString("folderUri", null)?.let(Uri::parse)
        if (folderUri == null) {
            AlertDialog.Builder(this).setTitle("Chào mừng đến NoteVid")
                .setMessage("Chọn thư mục CHỨA video học sáo (ví dụ: Movies/HocSao). NoteVid sẽ lưu tệp .notevid.json ngay cạnh video. Trên Android 11+, không thể cấp quyền cho toàn thư mục gốc Download.")
                .setPositiveButton("Chọn thư mục") { _, _ -> selectFolder() }
                .setNegativeButton("Để sau", null).show()
        } else {
            try {
                val last = prefs.getString("lastVideo", null)
                val video = store.videosIn(folderUri!!).firstOrNull { it.name == last }
                if (video != null) openVideo(video)
                else {
                    val found = store.videosIn(folderUri!!)
                    if (found.size == 1) openVideo(found.first())
                    else {
                        val lastUri = prefs.getString("lastPickedVideoUri", null)?.let(Uri::parse)
                        val fromPicker = lastUri?.let { store.videoFromPickedFile(folderUri!!, it) }
                        if (fromPicker != null) openVideo(fromPicker) else showVideos()
                    }
                }
            } catch (e: Exception) { showError("Quyền truy cập thư mục đã hết hiệu lực. Hãy chọn lại. ${e.message}") }
        }
        updates.maybeAutoCheck()
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        setAllCaps(false)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { onClick() }
    }

    private fun createUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF4F5F1.toInt()) }
        setContentView(root)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(button("Thư mục") { selectFolder() })
        top.addView(button("Mở video") { pickVideo() })
        top.addView(button("Danh sách") { showVideos() })
        top.addView(button("Cài đặt") { showSettings() })
        top.addView(button("Cập nhật") { updates.check() })
        topScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(top) }
        root.addView(topScroll)
        title = TextView(this).apply { text = "NoteVid · Chưa mở video"; textSize = 15f; setPadding(dp(12), dp(4), dp(12), dp(4)); maxLines = 1 }
        root.addView(title)
        val scene = LayoutInflater.from(this).inflate(R.layout.player_scene, root, false)
        playerView = scene.findViewById(R.id.video_player)
        overlay = scene.findViewById(R.id.note_overlay)
        focusTapTarget = scene.findViewById<View>(R.id.focus_tap_target).apply {
            setOnClickListener { setMenusHidden(false) }
        }
        root.addView(scene, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        progressRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(1), dp(6), dp(1)) }
        seek = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) { isSeeking = true }
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser && ::player.isInitialized) {
                        val duration = player.duration
                        if (duration > 0) clock.text = Timecode.format(duration * value / 1000L)
                    }
                }
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    if (player.duration > 0) player.seekTo(player.duration * (bar?.progress ?: 0) / 1000L)
                    isSeeking = false
                }
            })
        }
        progressRow.addView(seek, LinearLayout.LayoutParams(0, dp(42), 1f))
        clock = TextView(this).apply { text = "00:00:00.00"; textSize = 11f; setPadding(dp(3), dp(12), dp(3), 0) }
        progressRow.addView(clock)
        root.addView(progressRow)
        playbackRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnPlay = button("▶ Phát") { if (player.isPlaying) player.pause() else player.play() }
        playbackRow.addView(btnPlay, LinearLayout.LayoutParams(0, dp(48), 1f))
        playbackRow.addView(button("−5s") { player.seekTo((player.currentPosition - 5000L).coerceAtLeast(0)) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        playbackRow.addView(button("+5s") { player.seekTo(player.currentPosition + 5000L) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        btnSpeed = button("1×") { speedIndex = (speedIndex + 1) % speeds.size; player.setPlaybackSpeed(speeds[speedIndex]); btnSpeed.text = "${speeds[speedIndex]}×" }
        playbackRow.addView(btnSpeed, LinearLayout.LayoutParams(0, dp(48), 1f))
        btnLoop = button("Lặp A–B") { toggleLoop() }
        playbackRow.addView(btnLoop, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(playbackRow)
        notesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnEdit = button("✎ Chỉnh sửa") { setEditing(!editing) }
        notesRow.addView(btnEdit, LinearLayout.LayoutParams(0, dp(50), 1f))
        notesRow.addView(button("☷ Ghi chú") { showNoteList() }, LinearLayout.LayoutParams(0, dp(50), 1f))
        btnOrientation = button("⛶ Ngang") { toggleOrientation() }
        notesRow.addView(btnOrientation, LinearLayout.LayoutParams(0, dp(50), 1f))
        notesRow.addView(button("Ẩn menu") { setMenusHidden(true) }, LinearLayout.LayoutParams(0, dp(50), 1f))
        root.addView(notesRow)
        instructions = TextView(this).apply {
            text = "Xem: ghi chú tự hiện. Chỉnh sửa: dừng video, kéo để vẽ khung; chạm khung để mở 5 công cụ."
            textSize = 11f; setPadding(dp(12), dp(2), dp(12), dp(7))
        }
        root.addView(instructions)
        updateOrientationUi()
    }

    private fun selectFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, folderRequest)
    }

    /** Direct SAF picker to handle vendor gallery folders not listing MIME types consistently. */
    private fun pickVideo() {
        if (folderUri == null) {
            AlertDialog.Builder(this).setTitle("Chọn thư mục trước")
                .setMessage("Để lưu .notevid.json cùng thư mục với video, NoteVid cần quyền đọc/ghi thư mục video trước.")
                .setPositiveButton("Chọn thư mục") { _, _ -> selectFolder() }
                .setNegativeButton("Đóng", null).show()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // Supports videos incorrectly reported as application/octet-stream.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, videoRequest)
    }

    @Deprecated("Legacy result API is sufficient for the two SAF pickers")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            folderRequest -> {
                try {
                    val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    contentResolver.takePersistableUriPermission(uri, flags)
                    folderUri = uri
                    prefs.edit().putString("folderUri", uri.toString())
                        .remove("lastVideo").remove("lastPickedVideoUri").apply()
                    closeVideo()
                    val videos = store.videosIn(uri)
                    when (videos.size) {
                        1 -> {
                            toast("Đã tìm thấy ${videos.first().name}; đang mở video")
                            openVideo(videos.first())
                        }
                        else -> showVideos()
                    }
                } catch (e: Exception) {
                    showError("Không đọc được thư mục vừa chọn: ${e.message}. Hãy chọn thư mục con như Movies/HocSao.")
                }
            }
            videoRequest -> {
                try {
                    val flags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                    if (flags != 0) {
                        try { contentResolver.takePersistableUriPermission(uri, flags) }
                        catch (_: SecurityException) { /* Tree permission may already cover the file. */ }
                    }
                    val rootUri = folderUri ?: run { selectFolder(); return }
                    val video = store.videoFromPickedFile(rootUri, uri)
                    if (video != null) {
                        openVideo(video)
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Video không nằm trong thư mục đã cấp quyền")
                            .setMessage("NoteVid phải lưu ghi chú cạnh video. Hãy chọn đúng thư mục CHỨA video này, sau đó bấm Mở video. Trong trình chọn tệp, vào Bộ nhớ trong → thư mục video; đừng chọn từ Gần đây/Ảnh nếu chúng dùng nhà cung cấp khác. Video chưa được mở để tránh lưu ghi chú sai chỗ.")
                            .setPositiveButton("Chọn lại thư mục") { _, _ -> selectFolder() }
                            .setNegativeButton("Đóng", null).show()
                    }
                } catch (e: Exception) {
                    showError("Không mở được tệp đã chọn: ${e.message}")
                }
            }
        }
    }

    private fun showVideos() {
        val uri = folderUri ?: run { selectFolder(); return }
        try {
            val files = store.videosIn(uri)
            when (files.size) {
                0 -> {
                    val entries = store.allNamesIn(uri).take(10)
                    val examples = if (entries.isEmpty()) "Android không trả về tệp nào trong thư mục này."
                        else "Các mục Android trả về: ${entries.joinToString(", ")}"
                    AlertDialog.Builder(this).setTitle("Chưa tìm thấy video")
                        .setMessage("$examples\n\nNếu thư mục thật sự có video, bấm Mở video để chọn trực tiếp tệp đó; hoặc đổi thư mục chứa video. Hỗ trợ MP4, MKV, AVI, MOV, WebM và các tên video thông dụng.")
                        .setPositiveButton("Mở video") { _, _ -> pickVideo() }
                        .setNeutralButton("Đổi thư mục") { _, _ -> selectFolder() }
                        .setNegativeButton("Đóng", null).show()
                }
                1 -> openVideo(files.first())
                else -> AlertDialog.Builder(this).setTitle("Chọn video (${files.size})")
                    .setItems(files.map { it.name }.toTypedArray()) { _, which -> openVideo(files[which]) }
                    .setNeutralButton("Mở video trực tiếp") { _, _ -> pickVideo() }
                    .setNegativeButton("Đóng", null).show()
            }
        } catch (e: Exception) {
            showError("Không liệt kê được thư mục video: ${e.message}. Hãy cấp lại quyền thư mục hoặc thử nút Mở video.")
        }
    }

    private fun openVideo(video: NoteStore.VideoFile) {
        val loaded = try { store.read(video) } catch (e: Exception) {
            showError("Không đọc được tệp ghi chú của ${video.name}. Đã NGỪNG mở để tránh ghi đè dữ liệu lỗi. ${e.message}")
            return
        }
        closeVideo()
        currentVideo = video
        notes.clear(); notes.addAll(loaded)
        overlay.notes = notes
        overlay.select(null)
        overlay.videoAspectRatio = 16f / 9f
        overlay.isEditing = false
        editing = false
        btnEdit.text = "✎ Chỉnh sửa"
        title.text = video.name + "   ·   ${notes.size} ghi chú"
        prefs.edit().putString("lastVideo", video.name)
            .putString("lastPickedVideoUri", video.uri.toString()).apply()
        try {
            contentResolver.openFileDescriptor(video.uri, "r")?.use { /* Test readability before playback. */ }
                ?: throw java.io.IOException("Android không trả về tệp video")
            player.setMediaItem(MediaItem.fromUri(video.uri))
            player.prepare()
            player.play()
        } catch (e: Exception) {
            showError("Không mở được ${video.name}: ${e.message}. Hãy chọn video ở bộ nhớ máy, không phải tệp cloud chưa tải về.")
            closeVideo()
            return
        }
        val rememberPos = prefs.getLong("position_${video.uri.hashCode()}", 0L)
        if (rememberPos > 0) player.seekTo(rememberPos)
    }

    private fun closeVideo() {
        currentVideo?.let { video -> prefs.edit().putLong("position_${video.uri.hashCode()}", player.currentPosition.coerceAtLeast(0)).apply() }
        player.stop()
        player.clearMediaItems()
        currentVideo = null
        notes.clear()
        if (::title.isInitialized) title.text = "NoteVid · Chưa mở video"
        editing = false
        loopA = null; loopB = null
        if (::btnLoop.isInitialized) btnLoop.text = "Lặp A–B"
        if (::overlay.isInitialized) {
            overlay.notes = emptyList()
            overlay.isEditing = false
        }
    }

    private fun setEditing(value: Boolean) {
        if (currentVideo == null) { toast("Hãy mở video trước"); return }
        editing = value
        if (value) player.pause()
        overlay.isEditing = value
        btnEdit.text = if (value) "✓ Xong" else "✎ Chỉnh sửa"
        if (value) toast("Kéo trên hình để tạo khung; chạm ghi chú để mở công cụ")
    }

    private fun createNote(box: android.graphics.RectF) {
        val video = currentVideo ?: return
        val start = player.currentPosition.coerceAtLeast(0)
        val duration = prefs.getLong("defaultDurationMs", 5000L).coerceIn(100, 600_000)
        val note = VideoNote(
            startMs = start, endMs = start + duration, text = "",
            x = box.left, y = box.top, w = box.width(), h = box.height(), style = getDefaultStyle()
        ).also { it.clampPosition() }
        editText(note, create = true)
    }

    private fun editText(note: VideoNote, create: Boolean = false) {
        val edit = EditText(this).apply {
            minLines = 2; maxLines = 8; setSingleLine(false)
            setText(note.text); setSelection(text.length)
            hint = "Ví dụ: Luyến láy ở nốt Đô"
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (create) "Ghi chú mới · ${Timecode.format(note.startMs)}" else "Sửa chữ")
            .setView(edit)
            .setPositiveButton("Lưu", null)
            .setNegativeButton("Hủy", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val content = edit.text.toString().trim()
                if (content.isEmpty()) { edit.error = "Hãy nhập nội dung"; return@setOnClickListener }
                note.text = content
                if (create) notes.add(note)
                refreshNotes()
                overlay.select(note.id)
                saveNotes()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun noteTools(note: VideoNote) {
        player.pause()
        overlay.select(note.id)
        val actions = arrayOf("Sửa chữ", "Di chuyển", "Đổi màu", "Thời gian", "Xóa")
        AlertDialog.Builder(this).setTitle("Ghi chú · ${Timecode.format(note.startMs)}")
            .setItems(actions) { _, index ->
                when (index) {
                    0 -> editText(note)
                    1 -> { overlay.moveSelected(); toast("Kéo khung để di chuyển; kéo chấm xanh góc phải-dưới để đổi kích thước") }
                    2 -> StyleDialog.show(this, note.style) { newStyle -> note.style = newStyle; refreshNotes(); saveNotes() }
                    3 -> editTime(note)
                    4 -> AlertDialog.Builder(this).setTitle("Xóa ghi chú?")
                        .setMessage(note.text).setPositiveButton("Xóa") { _, _ -> notes.removeAll { it.id == note.id }; overlay.select(null); refreshNotes(); saveNotes() }
                        .setNegativeButton("Hủy", null).show()
                }
            }.setNegativeButton("Đóng", null).show()
    }

    private fun editTime(note: VideoNote) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(4)) }
        root.addView(TextView(this).apply { text = "Bắt đầu (giờ:phút:giây.phần trăm)" })
        val start = EditText(this).apply { setSingleLine(true); setText(Timecode.format(note.startMs)); selectAll() }
        root.addView(start)
        root.addView(TextView(this).apply { text = "Thời lượng hiển thị (giây, ví dụ 5.00)" })
        val duration = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(String.format(Locale.US, "%.2f", (note.endMs - note.startMs) / 1000.0))
        }
        root.addView(duration)
        val dialog = AlertDialog.Builder(this).setTitle("Thời gian ghi chú")
            .setView(root).setPositiveButton("Lưu", null).setNegativeButton("Hủy", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val startMs = Timecode.parse(start.text.toString())
                val seconds = duration.text.toString().replace(',', '.').toDoubleOrNull()
                if (startMs == null) { start.error = "Thời gian không đúng"; return@setOnClickListener }
                if (seconds == null || seconds < 0.1 || seconds > 600) {
                    duration.error = "Nhập 0,1–600 giây"; return@setOnClickListener
                }
                note.startMs = startMs
                note.endMs = startMs + (seconds * 1000).toLong()
                player.seekTo(note.startMs)
                refreshNotes(); saveNotes()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showNoteList() {
        if (currentVideo == null) { toast("Chưa mở video"); return }
        if (notes.isEmpty()) { toast("Chưa có ghi chú. Bật Chỉnh sửa rồi kéo vẽ khung."); return }
        val ordered = notes.sortedBy { it.startMs }
        AlertDialog.Builder(this).setTitle("${notes.size} ghi chú · chạm để đến đoạn")
            .setItems(ordered.map { "${Timecode.format(it.startMs)}    ${it.text.replace('\n', ' ').take(45)}" }.toTypedArray()) { _, index ->
                val note = ordered[index]
                player.pause(); player.seekTo(note.startMs)
                setEditing(true); overlay.select(note.id)
                noteTools(note)
            }.setNegativeButton("Đóng", null).show()
    }

    private fun toggleLoop() {
        if (currentVideo == null) { toast("Chưa mở video"); return }
        when {
            loopA == null -> {
                loopA = player.currentPosition; btnLoop.text = "Đặt B"
                toast("Đã đánh dấu A = ${Timecode.format(loopA!!)}")
            }
            loopB == null -> {
                val b = player.currentPosition
                if (b <= loopA!!) { toast("Điểm B phải sau A"); return }
                loopB = b; btnLoop.text = "Tắt A–B"
                toast("Đã chọn đoạn lặp A–B")
            }
            else -> { loopA = null; loopB = null; btnLoop.text = "Lặp A–B" }
        }
    }

    private fun refreshNotes() {
        overlay.notes = notes.toList()
        title.text = "${currentVideo?.name ?: "NoteVid"}   ·   ${notes.size} ghi chú"
    }

    private fun saveNotes() {
        val video = currentVideo ?: return
        try { store.write(video, notes); refreshNotes() }
        catch (e: Exception) { showError("KHÔNG lưu được ghi chú cạnh video. Hãy kiểm tra quyền ghi và dung lượng trống. ${e.message}") }
    }

    private fun getDefaultStyle(): NoteStyle {
        val s = prefs.getString("defaultStyle", null) ?: return NoteStyle()
        return try { NoteStyle.fromJson(JSONObject(s)) } catch (_: Exception) { NoteStyle() }
    }

    private fun showSettings() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), dp(10)) }
        root.addView(TextView(this).apply { text = "Mặc định: nền trắng, chữ đen đậm vừa, stroke vàng quanh từng ký tự. Bạn có thể chỉnh riêng từng ghi chú."; textSize = 13f })
        root.addView(button("Chỉnh kiểu mặc định") {
            StyleDialog.show(this, getDefaultStyle()) { style ->
                prefs.edit().putString("defaultStyle", style.toJson().toString()).apply()
                toast("Đã lưu kiểu mặc định cho ghi chú mới")
            }
        })
        root.addView(TextView(this).apply { text = "Thời lượng mặc định (giây)" })
        val duration = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.1f", prefs.getLong("defaultDurationMs", 5000L) / 1000.0))
        }
        root.addView(duration)
        root.addView(TextView(this).apply { text = "GitHub repository (owner/NoteVid). Chỉ dùng cho cập nhật OTA." })
        val repo = EditText(this).apply { setSingleLine(true); setText(updates.getRepo()) }
        root.addView(repo)
        val auto = CheckBox(this).apply { text = "Kiểm tra cập nhật mỗi 24 giờ khi mở ứng dụng"; isChecked = prefs.getBoolean("autoCheck", true) }
        root.addView(auto)
        val scroll = ScrollView(this).apply { addView(root) }
        AlertDialog.Builder(this).setTitle("Cài đặt NoteVid")
            .setView(scroll).setPositiveButton("Lưu") { _, _ ->
                val d = duration.text.toString().replace(',', '.').toDoubleOrNull() ?: 5.0
                val durationMs = (d.coerceIn(0.1, 600.0) * 1000).toLong()
                prefs.edit().putLong("defaultDurationMs", durationMs)
                    .putString("githubRepo", repo.text.toString().trim())
                    .putBoolean("autoCheck", auto.isChecked).apply()
                toast("Đã lưu cài đặt")
            }.setNegativeButton("Hủy", null).show()
    }

    private fun showError(message: String) = AlertDialog.Builder(this).setTitle("NoteVid")
        .setMessage(message).setPositiveButton("Đóng", null).show()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            currentVideo?.let { prefs.edit().putLong("position_${it.uri.hashCode()}", player.currentPosition.coerceAtLeast(0)).apply() }
            player.pause()
        }
    }

    /** Rotate into landscape focus mode, or return to portrait with controls visible. */
    private fun toggleOrientation() {
        if (currentVideo == null) { toast("Hãy mở video trước"); return }
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            setMenusHidden(false)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            // Lock only to a landscape orientation; the screen can still flip by 180°.
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            setMenusHidden(true)
        }
    }

    /** In focus mode no app or system controls cover the video or its timed notes.
     *  A tap anywhere on the video (or the Back button) restores the controls.
     */
    private fun setMenusHidden(hidden: Boolean) {
        if (hidden && currentVideo == null) { toast("Hãy mở video trước"); return }
        if (hidden && editing) setEditing(false)
        menusHidden = hidden
        focusTapTarget.visibility = if (hidden) View.VISIBLE else View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses predictive Back instead of the legacy callback.
            if (hidden && focusBackCallback == null) {
                val callback = OnBackInvokedCallback { setMenusHidden(false) }
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback
                )
                focusBackCallback = callback
            } else if (!hidden) {
                focusBackCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
                focusBackCallback = null
            }
        }
        updateOrientationUi()
        updateSystemBars()
    }

    private fun updateOrientationUi() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        topScroll.visibility = if (menusHidden || landscape) View.GONE else View.VISIBLE
        title.visibility = if (menusHidden || landscape) View.GONE else View.VISIBLE
        instructions.visibility = if (menusHidden || landscape) View.GONE else View.VISIBLE
        progressRow.visibility = if (menusHidden) View.GONE else View.VISIBLE
        playbackRow.visibility = if (menusHidden) View.GONE else View.VISIBLE
        notesRow.visibility = if (menusHidden) View.GONE else View.VISIBLE
        btnOrientation.text = if (landscape) "↕ Dọc" else "⛶ Ngang"
    }

    private fun updateSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                val bars = WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                if (menusHidden) {
                    systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(bars)
                } else {
                    show(bars)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (menusHidden) {
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOrientationUi()
        updateSystemBars()
        overlay.invalidate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::focusTapTarget.isInitialized) updateSystemBars()
    }

    @Deprecated("On older devices, Back restores the player controls")
    override fun onBackPressed() {
        if (menusHidden) setMenusHidden(false)
        else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            focusBackCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
            focusBackCallback = null
        }
        if (::playerView.isInitialized) playerView.player = null
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
