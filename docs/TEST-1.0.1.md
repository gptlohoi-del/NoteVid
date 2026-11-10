# NoteVid 1.0.1 – Hướng dẫn thử trên điện thoại

## Lỗi đã xử lý trong mã nguồn

- Tự mở và phát video khi thư mục đã cấp quyền có đúng **một** video được nhận diện.
- Khi Android không liệt kê video (MIME type không chuẩn), bấm **Mở video** để chọn trực tiếp. Chỉ chấp nhận nếu file thuộc chính thư mục đã cấp quyền để giữ file `.notevid.json` cùng nơi với video.
- Thông báo lỗi Media3 và nguyên nhân nếu không thể phát video (ví dụ codec không hỗ trợ), thay vì chỉ hiển thị màn hình đen.
- Thêm icon NoteVid; tên file APK debug `NoteVid-1.0.1-debug.apk`, release `NoteVid-1.0.1.apk`.

## Kiểm tra nhanh

1. Dùng trình quản lý file tạo `Movies/HocSao` và chép vào đó **một video MP4 mã hóa H.264/AAC**. Nên dùng file nằm thật trong bộ nhớ điện thoại thay vì mục cloud.
2. Mở NoteVid và bấm **Thư mục**, chọn đúng `Movies/HocSao`, xác nhận cấp quyền. App sẽ tự mở và phát video.
3. Nếu vẫn chưa thấy video, bấm **Mở video** và chọn chính file MP4 đó. Nút này cho phép chọn video khi Android báo sai MIME type, nhưng vẫn yêu cầu file nằm trong thư mục đã cấp quyền.
4. Dừng video, bấm **Chỉnh sửa**, kéo khung, nhập chữ rồi bấm Lưu. Kiểm tra xuất hiện file `TenVideo.notevid.json` cạnh file MP4.
5. Đóng app và mở lại video để kiểm tra ghi chú xuất hiện theo thời gian. Bấm ghi chú ở Chỉnh sửa để thấy Sửa chữ / Di chuyển / Đổi màu / Thời gian / Xóa.
6. Nếu video không phát, chụp màn hình thông báo có **Mã lỗi Media3**. Gửi cùng phần mở rộng file (MP4, MKV...) và hãng/mẫu điện thoại để điều tra tiếp.

## Đặt tên và cài đặt APK

- Bản debug trong GitHub Actions là `NoteVid-1.0.1-debug.apk` (bên trong artifact ZIP `NoteVid-debug-APK`).
- Bản release từ GitHub Releases là `NoteVid-1.0.1.apk`, cùng với `SHA256SUMS.txt`.
- Để **cập nhật đè** bản release đang dùng, phải dùng **cùng signing keystore** như bản trước và tăng versionCode; không thể cài APK debug đè lên APK release ký key khác. Các APK debug ở những lần chạy GitHub Actions khác nhau cũng có thể dùng key khác nhau; nếu bị xung đột chữ ký hãy gỡ app debug cũ. File JSON cạnh video vẫn còn.
- Sau khi sửa code, chạy workflow `Build debug APK` trước. Chỉ chạy `Signed release and OTA` với version `1.0.1` khi đã thấy build debug thành công và key ký release đã được giữ nguyên.

> Việc nhận diện `.avi`, `.wmv`, `.flv`, `.ts` chỉ giúp tìm thấy file trong thư mục; thiết bị và Media3 không bảo đảm phát được mọi codec. Nên thử MP4 H.264/AAC trước để phân biệt lỗi quyền thư mục với lỗi định dạng.
