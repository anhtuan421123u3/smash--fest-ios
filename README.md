# Smash Fest! (iOS — 100% Pure Kotlin/Native)

Trò chơi giải đố bắn pháo vật lý 3D lấy cảm hứng từ **Smash Fest! (Flow Games)**, được viết **100% bằng Kotlin/Native thuần (`.kt`)** biên dịch trực tiếp sang thực thi gốc iOS ARM64 (`SceneKit` + `UIKit` + `AVFAudio` cinterop — không sử dụng bất kỳ file Swift hay Objective-C nào).

## Tính năng chính

1. **Lối chơi cốt lõi & Vật lý 3D (`SceneKit`):**
   - **Aim – Shoot – Smash:** Chạm kéo để căn góc xoay ngang, góc nâng, lực bắn và thời điểm nhả đạn (có đường ngắm quỹ đạo 3D trực quan) nhắm vào các đài lễ hội chuyển động.
   - **Chain of Destruction:** Phá trụ gỗ, kích nổ thùng pháo hoa hoặc làm lăn tảng đá nặng để tạo phản ứng dây chuyền đập vỡ hàng loạt bình gốm và chai pha lê thành từng mảnh 3D.
   - **Âm thanh ASMR êm tai:** Bộ tổng hợp sóng âm PCM 16-bit 44.1kHz viết thuần bằng Kotlin tạo tiếng vỡ pha lê/gốm ngũ cung dịu nhẹ kết hợp rung phản hồi xúc giác (`UIImpactFeedbackGenerator`).
2. **2.000+ Màn chơi & Phân loại Độ khó:**
   - Sinh màn chơi theo seed tất định cho hơn **2.500 màn** với 6 kiến trúc tháp lễ hội.
   - Phần thưởng đúng chuẩn: **Normal (+20 xu)**, **Hard (+50 xu)**, **Super Hard (+100 xu)**.
   - **Hỗ trợ Offline 100%:** Tắt mạng hoặc bật chế độ Offline giúp chơi mượt không quảng cáo xen kẽ.
3. **Hệ thống 5 Mạng (Lives) & Tiền tệ:**
   - Tối đa **5 mạng**, thua màn trừ **1 mạng**, tự động hồi **1 mạng sau mỗi 30 phút**.
   - Khi hết mạng có thể dùng **100 xu** hồi đầy 5 mạng hoặc **xem quảng cáo thưởng** nhận ngay **+1 mạng**.
4. **Đấu trường & Sự kiện (Carnival Champions Cup):**
   - Mở khóa từ **Level 10**, đua bảng xếp hạng cúp lễ hội cùng 19 đối thủ và thử thách đập bình hằng ngày.
5. **Cửa hàng IAP & Quảng cáo (Mô phỏng Offline):**
   - Gói **No Ads ($9.99)**, **Fail Offer ($4.99)** khi hụt đạn, và các gói Xu/Booster từ **$1.99** đến **$54.99**.

## Quy trình CI/CD Xuất File `.ipa`

1. Mọi bản cập nhật được đẩy lên nhánh `build-ipa` (hoặc Pull Request) trước.
2. GitHub Actions trên `macos-latest` chạy toàn bộ Unit Test (`jvmTest`), biên dịch binary `iosArm64` (`SmashFest.kexe`) và đóng gói thành **`SmashFest-unsigned.ipa`** tải về ngay trong mục **Actions -> Artifacts (`SmashFest-unsigned-ipa`)**.
3. Chỉ sau khi job xuất `.ipa` thành công, code mới được merge vào nhánh `main` và tự động phát hành bản **GitHub Release** đính kèm file `.ipa`.

## Cài đặt `.ipa`

Tải `SmashFest-unsigned.ipa` từ tab **Actions** (Artifact `SmashFest-unsigned-ipa`) hoặc mục **Releases**, sau đó cài lên iPhone/iPad (iOS/iPadOS 15.0+) qua **Sideloadly**, **AltStore**, **TrollStore** hoặc **ESign**.
