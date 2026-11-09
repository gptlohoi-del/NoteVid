# NoteVid development milestones

## v1.0 scope

- Offline video playback (Media3 ExoPlayer), flexible speed, seek, A/B loop.
- Draw/rescale/move note rectangles directly over video content.
- Text is black/medium, box background white and per-character yellow text stroke by default.
- Five-item note popup: Sửa chữ, Di chuyển, Đổi màu, Thời gian, Xóa.
- Sidecar `same-base-name.notevid.json` in the video's folder with persisted SAF directory permission.
- Per-note settings, app defaults, in-player note timeline, automatic appearance.
- GitHub-based build, one-time secure signing-key bootstrap, signed APK, GitHub Releases OTA with SHA-256 check and user-confirmed Android installation.

## Improvements to consider after on-device testing

- Color wheel with color preview / transparency slider.
- Screenshot export for notes without modifying the source video.
- MediaStore integration to open a video immediately from Android's "Share/Open with" UI while requesting access to its folder.
- Optional sorting/searching videos and recursive folder browsing.
- Atomic-save strategy for providers that support rename/move and safe backup rotation.
- Export/import several note sidecars at once.
- Better tablet UI and drag-to-select exact A/B time markers.

## Known limitations of v1.0 source release

- A user must choose the **folder containing** a video; Android 11+ blocks selecting the root Download folder and storage root through SAF.
- On some provider implementations (cloud-backed or read-only), creating a sibling JSON file is impossible; show a write error rather than silently saving elsewhere.
- Display position is normalized to the video area, but font size scales with available display width and may change on dramatically different aspect screens.
- No automatic background APK install: Android requires user approval.
- No Android build was run in the authoring environment; CI compiles/tests after upload. Treat this as an initial source release pending device validation.
