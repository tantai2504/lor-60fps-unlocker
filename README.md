# LoR 60+ FPS Unlocker (Android • Không cần Root)

Ứng dụng Android siêu nhẹ (~50KB), **mã nguồn mở 100%**, **không yêu cầu bất kỳ quyền truy cập nào** (không Internet, không đọc bộ nhớ ngoài), giúp mở khóa **60 FPS / 90 FPS / 120 FPS** cho **Legends of Runeterra (LoR)** trên máy Android chưa root bằng 1 chạm khi chạy cùng **Meta Wolf**.

> Thay vì phải cài MiXplorer, tự gõ đường dẫn `/data/data/...` và sửa tay file XML dễ lỗi, bạn chỉ cần import app này vào Meta Wolf và bấm 1 nút.

---

## Tính năng chính
* **Tự động quét đúng file cấu hình** của LoR trong các thư mục của Meta Wolf (`app_data_anon`, `app_fozak_apks`, `app_split` và các thư mục `app_*` khác).
* **Tự động nhận diện mã tài khoản (`live.xxxx...`)** — hoạt động ngay cả khi file cấu hình chưa có dòng `Video.VideoQuality`.
* **3 chế độ 1 chạm:**
  * `Mở khóa 60+ FPS (90Hz / 120Hz)` (`fps_60_plus`)
  * `Mở khóa 60 FPS (Ổn định, mát máy)` (`fps_60`)
  * `Khôi phục mặc định` (xóa dòng `Video.FPS` về chuẩn 30 FPS/Dynamic)
* **An toàn & minh bạch:**
  * Không có quyền `INTERNET` -> không thể gửi dữ liệu tài khoản ra ngoài.
  * Tự tạo file sao lưu `.bak` trước khi sửa lần đầu.
  * Chỉ gồm 3 file Java ngắn gọn (`MainActivity`, `LorConfigLocator`, `PlayerPrefsPatcher`) để ai cũng có thể đọc kiểm tra.

---

## Hướng dẫn sử dụng cho người chơi (Chỉ làm 1 lần)

### Chuẩn bị
1. Tải và cài **Meta Wolf** từ GitHub chính chủ: https://github.com/Katana-Official/SPatch-Update/releases
2. Tải và cài **LoR-60FPS-Unlocker.apk** từ mục **Releases** (hoặc **Actions**) của repository này.
3. Đảm bảo đã cài sẵn **Legends of Runeterra** trên máy.

### 5 bước thực hiện
1. Mở **Meta Wolf** -> `Settings` -> `Import Local App` -> chọn cả **Legends of Runeterra** và **LoR 60 FPS** -> bấm nút **`1`** bên dưới.
   * *(Mẹo tiết kiệm dung lượng: import xong bạn có thể gỡ bản LoR gốc ngoài máy).*
2. Mở **LoR bên trong Meta Wolf**, đăng nhập và chờ tải xong toàn bộ dữ liệu game.
3. **Tắt hẳn LoR (bắt buộc):** Thoát game, quay lại **Meta Wolf** -> **nhấn giữ icon LoR** -> chọn **tắt tiến trình (Kill)**.
4. Mở **LoR 60 FPS** từ **bên trong Meta Wolf** -> bấm **`Mở khóa 60+ FPS`** (hoặc `60 FPS`).
5. Quay lại **Meta Wolf** -> nhấn giữ icon LoR -> **`Create shortcut`** (`Tạo lối tắt`) ra màn hình chính để lần sau bấm vào chơi luôn.

---

## Cách tự build file `.apk` bằng GitHub Actions (Không cần cài Android Studio)
1. Tạo một repository mới trên GitHub và push toàn bộ thư mục này lên nhánh `main`.
2. Vào tab **Actions** trên GitHub -> chờ ~2 phút để workflow `Build LoR 60 FPS Unlocker APK` chạy xong (tự động chạy unit test + build APK).
3. Tải file **`LoR-60FPS-Unlocker.apk`** ở mục **Artifacts** cuối trang workflow.
4. Nếu muốn tạo trang tải chính thức ở mục **Releases**: tạo tag ví dụ `git tag v1.0.0 && git push origin v1.0.0`, GitHub Actions sẽ tự tạo Release và đính kèm file `.apk`!

---

## Lưu ý / Miễn trừ trách nhiệm
* Dự án này được phát triển độc lập dựa trên phương pháp cộng đồng chia sẻ tại `r/LegendsOfRuneterra`, không liên kết với Riot Games.
* Hãy cân nhắc điều khoản dịch vụ (ToS) khi sử dụng các công cụ chạy song song/nhân bản ứng dụng bên thứ ba.
