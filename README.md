# NoteVid — Trình phát video offline với ghi chú nổi theo thời gian

**NoteVid** là ứng dụng Android mã nguồn mở, dành cho việc học sáo, đọc nhạc và học kỹ thuật qua video. Bạn dừng video ở một mốc, **kéo ngón tay vẽ một khung** ở vị trí muốn ghi chú, gõ nội dung và lưu. Khi mở lại đúng video, nội dung tự hiện ở thời điểm đã lưu.

> **Trạng thái:** Đây là **repository mã nguồn v1.0**, chưa phải APK đã được kiểm thử trên điện thoại. Khi đưa repo lên GitHub, workflow **Build debug APK** sẽ thử biên dịch và chạy unit test. Nếu workflow đỏ, cần xử lý thông báo lỗi trước khi phát hành. Môi trường tạo repository này không có Android SDK/Gradle và không thể biên dịch APK tại chỗ.

## Chức năng có trong mã nguồn

- Phát video **offline** với AndroidX Media3 ExoPlayer (MP4, MKV, WebM… tùy codec của điện thoại).
- Kéo để vẽ khung ghi chú; nhập chữ, tự lưu theo mốc **mili giây**. Ví dụ `00:02:12.45` = `132450 ms`.
- Ghi chú tự hiện trong khi video đang phát. Có thể thay đổi **thời điểm bắt đầu** và **thời lượng** của từng ghi chú.
- Nền khung mặc định **trắng**, chữ mặc định **đen, đậm vừa (500)**, **stroke màu vàng quanh TỪNG KÝ TỰ** (không phải viền khung). Có thể đổi màu nền, màu chữ, màu stroke, độ dày stroke, cỡ và độ đậm chữ.
- Khung có vị trí/kích thước theo tỷ lệ hình video, không phụ thuộc pixel điện thoại; sửa bằng kéo và chấm xanh ở góc dưới-phải.
- Trong Chỉnh sửa, chạm vào ghi chú để thấy **Sửa chữ · Di chuyển · Đổi màu · Thời gian · Xóa**.
- Danh sách ghi chú theo mốc; tốc độ 0.5×/0.75×/1×/1.25×/1.5×; tua ±5 giây; lặp đoạn A–B.
- Không có nút Ẩn/Hiện ghi chú (theo yêu cầu). Trong chế độ Xem, ghi chú không chặn thao tác tua từ các nút bên dưới video.
- Lưu `TenVideo.notevid.json` ngay cạnh `TenVideo.mp4`, **giữ nguyên video gốc**.
- CI biên dịch APK debug; **GitHub Actions tạo khóa ký một lần**, phát hành APK đã ký; kiểm tra và cài cập nhật OTA qua GitHub Releases với SHA-256.

## Hướng dẫn sử dụng trên điện thoại

1. Tạo một thư mục như **Movies/HocSao** trong bộ nhớ, chép video vào đó. Trong NoteVid, bấm **Thư mục** và cấp quyền đọc/ghi thư mục đó bằng bộ chọn thư mục Android.
2. Sau khi chọn thư mục, nếu có đúng **1 video**, NoteVid sẽ **tự mở và phát**. Nếu nhiều video, bấm **Danh sách**. Nếu Android không hiển thị tệp trong thư mục dù tệp có tồn tại, bấm **Mở video**, chọn tệp bằng trình chọn tệp Android. NoteVid chỉ cho phép mở tệp trong **đúng thư mục được cấp quyền**, bảo đảm file JSON được lưu cùng vị trí với video.
3. Tua đến đoạn cần chú thích, bấm **Chỉnh sửa** (video tạm dừng). Kéo trên phần hình video để tạo khung. Gõ chữ và bấm **Lưu**.
4. Chạm vào khung vừa tạo và chọn **Di chuyển** để kéo đến chỗ mới, hoặc kéo **chấm xanh ở góc dưới-phải** để thay đổi kích thước. Chọn **Đổi màu**, **Thời gian**, **Sửa chữ**, **Xóa** nếu cần.
5. Bấm **Xong** để về chế độ xem. Khi phát đến mốc thời gian, ghi chú tự xuất hiện.
6. Video `Que_Huong.mp4` có tệp ghi chú `Que_Huong.notevid.json` bên cạnh. Sao chép **cả hai tệp** để mang sang máy khác.

**Quan trọng về Android 11+:** Android không cho chọn thư mục gốc bộ nhớ hoặc thư mục gốc `Download` bằng `ACTION_OPEN_DOCUMENT_TREE`. Hãy tạo thư mục con có thể cấp quyền như `Movies/HocSao` hoặc `Download/HocSao` (nếu trình chọn tệp cho phép) rồi đặt video ở đó. Nhà cung cấp tài liệu chỉ-đọc hoặc nền tảng đám mây có thể chặn lưu tệp cạnh video. Ứng dụng sẽ báo lỗi thay vì âm thầm lưu vào nơi khác.

## Upload toàn bộ repo lên GitHub (không cần Android Studio)

1. Vào **GitHub → New repository**, đặt tên `NoteVid`, chọn **Public** nếu muốn cập nhật OTA không cần đăng nhập. Tạo repo trống, **không** chọn tự tạo README hay .gitignore.
2. Giải nén `NoteVid-repo.zip`. Trên GitHub: **Add file → Upload files**, kéo **toàn bộ nội dung bên trong thư mục `NoteVid/`** vào, bao gồm cả thư mục `.github`. Kiểm tra trong GitHub có `app/`, `.github/workflows/`, `build.gradle.kts`, `settings.gradle.kts` ở cấp gốc.
3. Commit trực tiếp vào branch `main`. Vào tab **Actions → Build debug APK**. Chọn workflow chạy tự động (hoặc **Run workflow**).
4. Nếu CI thành công, mở job **test-and-build** → mục **Artifacts** → tải `NoteVid-debug-APK`, giải nén artifact để lấy `NoteVid-1.0.1-debug.apk` và cài thử trên điện thoại. Tên APK trong mỗi artifact mang phiên bản. **APK debug chỉ để thử**, không cập nhật được lên bản release ký bằng key khác. Khi chuyển sang bản release, cần gỡ bản debug trước; ghi chú `.notevid.json` cạnh video vẫn là tệp riêng và không bị gỡ theo ứng dụng.
5. Nếu muốn APK phát hành và OTA lâu dài, làm **Thiết lập key ký một lần** ở bên dưới, rồi vào **Actions → Signed release and OTA → Run workflow**, nhập `1.0.1` để GitHub tạo release và tag mới.

### Lưu ý cách biên dịch

Workflow dùng `gradle/actions/setup-gradle@v4` để tự cài **Gradle 8.13**, Android SDK API 36 và JDK 17. Không cần cam kết Gradle Wrapper JAR vào repository để build trên GitHub. Nếu build trên máy tính, hãy cài Android Studio hoặc Gradle 8.13 + Android SDK 36 rồi chạy `gradle :app:assembleDebug`.

## Thiết lập khóa ký APK (GitHub tạo, chỉ một lần)

**Tuyệt đối không đưa tệp `.jks`, mật khẩu, hoặc chuỗi base64 keystore vào mã nguồn/public repo.** Không tạo khóa mới mỗi lần cập nhật: Android cần các bản APK được ký với cùng chứng chỉ tương thích.

1. Trong GitHub repo chọn **Settings → Secrets and variables → Actions → New repository secret**, tạo secret tên `KEY_BOOTSTRAP_PASSPHRASE`. Đặt một mật khẩu dài, duy nhất và lưu bên ngoài GitHub bằng trình quản lý mật khẩu. Mật khẩu này **chỉ dùng để mã hóa bản sao key**.
2. Vào **Actions → One-time signing key bootstrap → Run workflow**. Workflow dùng `keytool` tạo keystore RSA 3072-bit tên `notevid-release.jks`, alias `notevid`, mật khẩu ngẫu nhiên. Nó **mã hóa** keystore và thông tin đăng nhập bằng GPG AES-256 trước khi upload artifact. Không có keystore plaintext trong artifact.
3. Tải artifact `notevid-encrypted-signing-backup` **trong 1 ngày**. Giải nén ZIP artifact để lấy `notevid-signing-backup.tar.gz.gpg`. Giải mã trên máy cá nhân (cần cài GPG):

   ```bash
   gpg --output notevid-signing-backup.tar.gz --decrypt notevid-signing-backup.tar.gz.gpg
   tar -xzf notevid-signing-backup.tar.gz
   ```

   Nhập `KEY_BOOTSTRAP_PASSPHRASE` đã dùng. Sau đó thu được **`notevid-release.jks`** và **`credentials.txt`**. Hãy giữ bản sao offline bảo mật của **cả hai tệp**.
4. Trong repo tạo **4 secrets** sau:

   | Secret | Nội dung |
   |---|---|
   | `NOTEV_ID_KEYSTORE_B64` | Toàn bộ nội dung base64 **một dòng** của tệp `.jks` |
   | `NOTEV_ID_STORE_PASSWORD` | Giá trị từ `credentials.txt` |
   | `NOTEV_ID_KEY_PASSWORD` | Giá trị từ `credentials.txt` |
   | `NOTEV_ID_KEY_ALIAS` | `notevid` |

   Trên Linux: `base64 -w 0 notevid-release.jks`. Trên macOS: `base64 < notevid-release.jks | tr -d '\n'`. Trên Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes('notevid-release.jks'))`. **Không paste kết quả vào issue hay commit.**
5. **Dễ nhất, không cần git CLI:** Vào **Actions → Signed release and OTA → Run workflow**, nhập ô **version** là `1.0.1` rồi bấm **Run workflow**. Workflow sẽ tạo tag `v1.0.1`, biên dịch APK đã ký, tính `SHA256SUMS.txt` và tự tạo GitHub Release.

   Nếu bạn quen dòng lệnh, cũng có thể tạo tag rồi đẩy lên GitHub để kích hoạt workflow:

   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

   **Đừng tự tạo Release trống trước đó trên web**, vì workflow sẽ tạo Release kèm APK.

**Cực kỳ quan trọng:** Key ký chỉ được tạo **một lần**. Chạy bootstrap lần hai không phải cách sửa lỗi nếu đã phát hành APK. Nếu mất key và bản sao lưu, bạn không thể cập nhật đè APK cũ bằng key khác theo cơ chế thông thường. Key tạo trên GitHub Actions **không tự ghi được thành GitHub Secrets** — phải nhập 4 secrets một lần theo các bước trên. Đây là lựa chọn bảo mật, không phải thiếu chức năng.

## Phát hành các phiên bản OTA tiếp theo

- Chỉnh mã nguồn; đưa thay đổi lên branch `main` và đợi CI xanh.
- Vào **Actions → Signed release and OTA → Run workflow**, nhập `1.0.1`. (Hoặc tạo tag `v1.0.1` và `git push origin v1.0.1`.)
- GitHub Actions tự build + ký APK bằng **cùng keystore**, tạo Release mới và file SHA-256. Ví dụ tên bản phát hành: `NoteVid-1.0.1.apk` (không có chữ `v` trong tên APK; tag GitHub vẫn là `v1.0.1`).
- NoteVid kiểm tra `https://api.github.com/repos/<owner>/NoteVid/releases/latest`. Nếu mới hơn, hỏi người dùng, tải APK qua HTTPS, xác minh SHA-256 và chuyển cho trình cài đặt Android. **Android vẫn phải cho phép cài từ nguồn này và người dùng phải xác nhận cài**. Đây là cập nhật qua APK, không phải app âm thầm thay đổi hệ thống.
- Ứng dụng phát hành từ GitHub nhúng đúng tên repo thông qua `-PgithubRepo="$GITHUB_REPOSITORY"`. Có thể chỉnh tay trong **Cài đặt** trên điện thoại.
- Mã phiên bản APK được tính từ tag theo công thức `major*1,000,000 + minor*1,000 + patch` (minor/patch không vượt 999). Luôn tăng phiên bản.
- Nếu workflow không thể tạo GitHub Release do quyền token bị hạn chế, kiểm tra **Settings → Actions → General → Workflow permissions** và cấp quyền đọc/ghi cho Actions (nếu chính sách repo cho phép).
- OTA qua API GitHub **chỉ hoạt động thuận tiện với repo Public**. Với repo Private phải bổ sung xác thực an toàn; **không nhúng PAT vào APK**.

## Cấu trúc repository

```text
NoteVid/
├── .github/workflows/
│   ├── ci.yml
│   ├── bootstrap-signing-key.yml
│   └── release.yml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/notevid/app/
│       │   ├── MainActivity.kt       # Video, ghi chú, thao tác
│       │   ├── NoteOverlayView.kt    # Khung theo tỷ lệ + vẽ stroke chữ
│       │   ├── NoteModels.kt         # Kiểu dữ liệu, định dạng, timecode
│       │   ├── NoteStore.kt          # Đọc/ghi JSON cạnh video bằng SAF
│       │   ├── StyleDialog.kt        # Màu, độ đậm, stroke
│       │   └── UpdateManager.kt      # GitHub OTA, SHA-256, APK installer
│       └── res/
├── docs/
│   ├── DATA-FORMAT.md
│   └── ROADMAP.md
├── build.gradle.kts
└── settings.gradle.kts
```

## Bảo mật và giới hạn

- Video và ghi chú xử lý offline; `INTERNET` chỉ dùng truy cập **GitHub API và tải APK cập nhật**.
- NoteVid không xin quyền truy cập mọi tệp trên điện thoại; quyền truy cập cấp theo thư mục bạn chọn và được lưu lại bởi Android.
- Tệp JSON cạnh video được ghi trực tiếp qua `ContentResolver`; các document provider không bảo đảm ghi atomic, nên sao lưu khi có bộ ghi chú quý giá. Nếu dữ liệu đọc bị lỗi, app không tự ghi đè.
- `.notevid.json` không tự gắn với video sau khi bạn **chỉ** đổi tên video: hãy đổi tên cả sidecar cho đồng bộ.
- Không thể cài bản cập nhật im lặng; Android là bên kiểm tra chữ ký và yêu cầu chấp thuận của người dùng.

## Thông số build

- Kotlin 2.1.20, Android Gradle Plugin 8.11.1, Gradle 8.13
- JDK 17, Android compileSdk 36, targetSdk 35, minSdk 26
- AndroidX Media3 1.11.1, DocumentFile 1.0.1
- App ID: `com.notevid.app`
- License: MIT


## Sửa lỗi CI: Failed to find package 'tools' (2026-11-09)

Các bản NoteVid ZIP ban đầu dùng `android-actions/setup-android@v3`.
Google đã ngừng cung cấp gói SDK `tools`, khiến workflow dừng trước bước Gradle.
Bản sửa đổi này dùng `android-actions/setup-android@v4` với `packages: 'platform-tools'` ở cả
`.github/workflows/ci.yml` và `.github/workflows/release.yml`.
Không cần thay đổi mã nguồn ứng dụng để xử lý lỗi SDK này.
Sau khi thay hai tệp workflow trên GitHub, chọn **Actions → Build debug APK → Run workflow**.
Nếu còn lỗi khác, hãy xem log của bước Gradle (nếu đã đến bước này).

## NoteVid 1.0.1 – Sửa chọn video, icon và tên APK

- Icon NoteVid (video màu xanh, thẻ ghi chú vàng) có trong `res/mipmap-*` và icon adaptive Android 8+.
- APK debug: `NoteVid-1.0.1-debug.apk`. APK release: `NoteVid-1.0.1.apk` khi phát hành với tag `v1.0.1`.
- Chọn thư mục có **1 video** sẽ tự mở và bắt đầu phát. Khi Android không liệt kê được file, dùng nút **Mở video** để chọn trực tiếp (chấp nhận MIME `application/octet-stream` nếu đuôi video được hỗ trợ).
- Chỉ nhận video thuộc thư mục đã cấp quyền đọc/ghi; không lưu tệp ghi chú khác thư mục video.
- Khi lỗi codec/đọc file, app hiển thị mã lỗi Media3 và nguyên nhân để gửi hỗ trợ thay vì chỉ hiện màn hình đen.
- Nếu đã cài bản debug, hãy gỡ bản debug trước khi cài bản release ký bằng key riêng; sao lưu tệp ghi chú cạnh video nếu cần. Các lần build CI debug cũng có thể dùng key khác nhau; để cập nhật không gỡ app hãy dùng bản release có ký ổn định.
- Tính năng chưa được biên dịch và chạy trực tiếp trên điện thoại trong môi trường tạo gói này; cần CI GitHub xác nhận và kiểm thử thực tế.

**Lưu ý khi mở trực tiếp tệp:** Trong trình chọn tệp Android, hãy vào *Bộ nhớ trong → Movies → HocSao*; tránh chọn từ mục **Gần đây/Ảnh** vì chúng có thể trả về URI của ứng dụng thư viện ảnh thay vì URI của thư mục đã cấp quyền.

## NoteVid 1.0.2 – Xoay ngang, toàn màn hình và ẩn menu

Khi đã mở video, trong hàng nút bên dưới trình phát:

- **⛶ Ngang**: xoay màn hình sang ngang và tự động chuyển sang chế độ xem toàn màn hình; video và ghi chú nổi vẫn hiển thị. Ứng dụng yêu cầu xoay ngang kể cả khi hệ thống đang khóa tự động xoay.
- **Ẩn menu**: ẩn mọi thanh công cụ, thanh tiến trình, tiêu đề, chỉ dẫn và thanh trạng thái/điều hướng Android. Chỉ còn vùng video với ghi chú nổi tại đúng thời điểm. Dùng được cả khi đang dọc hoặc ngang.
- **Chạm một lần lên video** để hiện lại các menu. Khi đang ngang, bấm **↕ Dọc** để trở về màn hình dọc.
- Khi đang chỉnh sửa ghi chú, vào chế độ xem tập trung sẽ tự tắt chỉnh sửa, tránh vô tình thay đổi ghi chú. Video giữ nguyên tỷ lệ khung hình (`fit`) để không cắt nội dung hoặc làm lệch vị trí các ghi chú.

Kiểm thử thủ công: phát video có ghi chú → bấm **⛶ Ngang** → kiểm tra ghi chú nằm đúng vị trí và không còn các menu → chạm video để hiện lại menu → bấm **↕ Dọc** → bấm **Ẩn menu** ở màn hình dọc → chạm video để trở về. Xác nhận cả video dọc, video ngang và thiết bị dùng nút hoặc cử chỉ điều hướng.
