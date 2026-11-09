# NoteVid sidecar data format v1

A video named `Que_Huong.mp4` has a matching sidecar `Que_Huong.notevid.json` in the **same directory**.
The video is never altered. The filename before the last extension is used as the base name.

```json
{
  "app": "NoteVid",
  "schema": 1,
  "videoName": "Que_Huong.mp4",
  "positionMode": "normalized-video-frame",
  "notes": [
    {
      "id": "example-uuid",
      "startMs": 132450,
      "endMs": 137450,
      "text": "Luyến láy ở nốt Đô",
      "x": 0.54, "y": 0.10, "w": 0.40, "h": 0.20,
      "style": {
        "background": -1,
        "textColor": -16777216,
        "outlineColor": -15360,
        "outlineDp": 2.2,
        "fontSp": 20.0,
        "weight": 500
      }
    }
  ]
}
```

- All times are in **milliseconds**; `00:02:12.45` maps to `132450`.
- `startMs` is inclusive; `endMs` is exclusive.
- `x, y, w, h` are fractions of the actual video image rectangle **after letterboxing**, not absolute screen pixels.
- Colors are Android signed 32-bit ARGB integers. Stroke is around each **glyph**, not around the rectangle.
- `notevid.json` is UTF-8 with Vietnamese Unicode text and can be backed up with its matching video.
- `schema` is explicit to prevent silently overwriting incompatible future formats.
- Sidecars created by the app are written using Android Storage Access Framework document URIs.
