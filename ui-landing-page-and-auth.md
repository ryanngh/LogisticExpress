# Ghi chú chênh lệch Design UI/UX vs Backend — Branch `ui/landing-page-and-auth`

> File này ghi nhận đầy đủ các tính năng/trường dữ liệu **thiếu hoặc dư thừa** giữa bản thiết kế Stitch (`logistic_express_landing_page` + `auth_state_01` → `05`) và Backend hiện tại của dự án Logistic Express. Không tự ý thêm/xóa/thay đổi nghiệp vụ ngoài phạm vi yêu cầu.

---

## 1. Các tính năng có trong Design nhưng Backend CHƯA hỗ trợ (Thiếu ở Backend)

1. **Xác thực OTP Email (`auth_state_05_nh_p_otp_email`)**
   - **Design:** Sau khi đăng ký (Sign up), hiển thị màn hình nhập mã OTP 6 chữ số gửi qua email kèm đếm ngược `01:45` và nút `Gửi lại mã OTP`.
   - **Backend hiện tại:** Chưa có cơ chế gửi email/SMTP hay API xác thực OTP (`EMAIL_VERIFICATION` theo `ROADMAP.md` được dời sang Phase 6A). Khi gọi `POST /auth/register`, tài khoản Shop được kích hoạt ngay (`Status.ACTIVE`) và cấp phiên đăng nhập (`ACCESS_TOKEN` + `REFRESH_TOKEN`).
   - **Xử lý hiện tại:** Đã **skip bước OTP** theo đúng yêu cầu; đăng ký thành công sẽ đăng nhập và chuyển hướng trực tiếp.

2. **Tùy chọn `"Ghi nhớ đăng nhập"` (`auth_state_01_sign_in_m_c_nh`)**
   - **Design:** Form Sign in có checkbox `Ghi nhớ đăng nhập`.
   - **Backend hiện tại:** `LoginRequest` chỉ nhận `{ usernameOrEmail, password }`, chưa hỗ trợ cờ `rememberMe` để thay đổi TTL của `REFRESH_TOKEN` (hiện cố định 7 ngày trong `application.yml`).
   - **Xử lý hiện tại:** Giữ nguyên checkbox trên UI theo đúng Design (không tự ý xóa), chưa gửi trường `rememberMe` lên backend.

3. **Gửi email thực tế & Trang đặt lại mật khẩu (`Quên mật khẩu` tab)**
   - **Design:** Modal Auth có tab thứ 3 `Quên mật khẩu`, nhưng trong thư mục Stitch không có file state riêng cho form Quên mật khẩu / Đặt lại mật khẩu.
   - **Backend hiện tại:** Đã có `POST /auth/forgot-password` (sinh `VerificationToken` lưu DB) và `POST /auth/reset-password`, nhưng chưa tích hợp dịch vụ gửi email thực tế để chuyển token cho người dùng.
   - **Xử lý hiện tại:** Đã nối tab `Quên mật khẩu` với `POST /auth/forgot-password`; chưa tạo trang `/reset-password` riêng do chưa có design tương ứng.

4. **Các trang đích sau đăng nhập & trang công khai liên kết từ Landing Page**
   - **Backend / Design:**
     - `AuthService.determineRedirectUrl(...)` trả về `/shop/dashboard`, `/shipper/tasks`, `/ops/dashboard`.
     - Header & Footer của Landing Page có các liên kết `Theo dõi vận đơn` (`/track`), `Ước tính cước phí` (`/quote`), `Cổng Shop /shop`, `Ứng dụng Shipper /shipper`, `Cổng Vận hành /ops`.
   - **Hiện trạng:** Hiện mới có REST API backend, chưa có Thymeleaf Page Controller/Template cho các màn hình dashboard, `/track` và `/quote`.
   - **Xử lý hiện tại:** Giữ nguyên các liên kết anchor (`#he-sinh-thai`, `#bang-gia`, `#quy-trinh`, `#faq`) đúng theo bản Design Landing Page và giữ nguyên `redirectUrl` từ backend (không tự ý chế thêm màn hình dashboard ngoài phạm vi task).

---

## 2. Các trường / dữ liệu có ở Backend nhưng Design KHÔNG hiển thị (Dư ở Backend / Tĩnh ở Design)

1. **Trường `shopName` và `username` trong `POST /auth/register`**
   - **Backend hiện tại:** `RegisterRequest` cho phép truyền thêm `shopName` (tùy chọn, mặc định lấy theo `fullName` từ commit `cc31c83`) và `username` (tùy chọn, tự động sinh từ `email` nếu để trống).
   - **Design (`auth_state_02`):** Chỉ có đúng **5 trường bắt buộc**: `Họ tên *`, `Số điện thoại (VN) *`, `Email *`, `Mật khẩu (6–100 ký tự) *`, `Nhập lại mật khẩu *`.
   - **Xử lý hiện tại:** Giữ đúng 5 trường như Design, không tự ý thêm input `shopName` hay `username`.

2. **Số liệu Dashboard minh họa & Bảng giá trên Landing Page**
   - **Design:** Hiển thị số liệu mẫu (`1.248`, `986`, `262`) và giá tham khảo tĩnh (`18.000 đ` STANDARD, `30.000 đ` EXPRESS).
   - **Backend hiện tại:** Đã có `PricingController` / `PricingEngineService` và bảng `rate_cards`, nhưng Landing Page trong Design dùng nội dung giới thiệu tĩnh.
   - **Xử lý hiện tại:** Giữ nguyên nội dung tĩnh theo đúng Design để không lệch bố cục; có thể nối động với `rate_cards` ở task sau nếu cần.

---

## 3. Ghi chú kỹ thuật bổ sung (Auth Token & Blacklist)

- **Cơ chế lưu trữ Token:** Giữ đúng `AGENTS.md → Guardrail #1`: `ACCESS_TOKEN` (`HttpOnly`, `SameSite=Lax`, `Path=/`, TTL ~15 phút) và `REFRESH_TOKEN` (`HttpOnly`, `SameSite=Strict`, `Path=/auth`, TTL ~7 ngày). Không lưu token trong `localStorage`, `sessionStorage` hay response body.
- **Auto-Refresh & Silent Restore:**
  - `static/js/http.js` tự động gọi `POST /auth/refresh` 1 lần khi gặp `401` (dùng chung 1 promise cho các request đồng thời) và retry request gốc.
  - Khi người dùng truy cập `/login?next=...` hoặc `/` mà Access Token đã hết hạn nhưng Refresh Token vẫn còn hợp lệ, client tự động gọi `POST /auth/refresh` ngầm mà **không yêu cầu đăng nhập lại**.
  - Nếu Refresh Token hết hạn, không hợp lệ hoặc bị thu hồi (`REUSE_DETECTED`), backend xóa cả 2 cookie và client chuyển về màn hình đăng nhập kèm thông báo phiên hết hạn.
- **Logout & Access Token Blacklist:**
  - `POST /auth/logout` thu hồi Refresh Token trong `user_sessions`, đồng thời đưa Access Token hiện tại vào `AccessTokenBlacklistService` (Redis key `auth:blacklist:at:<sha256>`, TTL = đúng thời gian còn lại đến `exp` của Access Token, có local fallback nếu Redis không khả dụng).
  - `JwtAuthenticationFilter` kiểm tra blacklist trước khi xác thực, đảm bảo Access Token đã logout không thể tiếp tục sử dụng.
