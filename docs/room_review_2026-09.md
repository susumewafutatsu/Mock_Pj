# Rà soát chức năng Phòng thi — góc nhìn người dùng (14/09/2026)

Rà soát bằng cách thao tác thật trên giao diện với bộ dữ liệu thật: người ra đề **Trần Đức Anh** (phòng N4 đã thi xong), thí sinh **Nguyễn Hoàng Long** (phòng N3 đang chờ) và **Hồ Bảo Ngọc** (đã thi phòng N4).

Phần lõi chạy đúng: vào phòng bằng mã, sảnh chờ, bắt đầu bằng tay hoặc hẹn giờ, thu bài khi hết giờ, bảng xếp hạng sau khi kết thúc. Vấn đề nằm ở trải nghiệm và ở vài luật nghiệp vụ chưa hợp với một buổi thi thật.

## A. Người ra đề

| # | Bất cập | Ảnh hưởng |
|---|---|---|
| A1 | Màn chi tiết phòng chỉ ghi "Bài thi trong phòng (1)" kèm ô *gắn thêm* — **không liệt kê đề đang gắn, không gỡ được** | Không biết phòng đang thi đề nào; gắn nhầm thì không sửa được |
| A2 | Một phòng gắn được **nhiều đề**, nhưng buổi thi chỉ dài bằng **đề dài nhất** | Làm hai đề liên tiếp là bị cắt giữa chừng; bảng xếp hạng tách nhiều bảng khó hiểu |
| A3 | Dựng một phòng phải qua 3 bước rời rạc: tạo phòng → mở *Chi tiết* gắn đề → quay ra thẻ bấm *Mở sảnh chờ* | Dễ bỏ sót bước, phòng nằm mãi ở "Nháp" |
| A4 | Ô chọn đề liệt kê mọi đề, kể cả đề **chưa có câu hỏi** | Gắn được đề rỗng |
| A5 | **Không theo dõi được buổi thi**: không biết ai đang ở sảnh, ai đang làm tới câu mấy, ai rớt mạng, ai chưa vào | Người ra đề phải hỏi miệng; không phát hiện thí sinh mất kết nối |
| A6 | Bảng xếp hạng không mở được bài của từng thí sinh | Muốn chữa bài phải sang trang Kết quả và tìm lại |
| A7 | **Không nhân bản được phòng** | Lớp thi định kỳ (vd "tối thứ 3·5") phải tạo phòng mới và mời lại cả lớp bằng mã mới |
| A8 | Phòng **đã thi xong vẫn mở lại được** (đổi trạng thái về OPEN) | Bảng xếp hạng và khung giờ của buổi cũ bị xáo trộn |
| A9 | Mời thí sinh ra **giữa giờ thi** nhưng bài của họ vẫn làm tiếp | Mời ra không có tác dụng |
| A10 | Nút xoá luôn hiện, bấm xong mới báo "phòng đã có người, không xoá được" | Thao tác vô ích |
| A11 | Cột "Thời gian" hiện `17:00` | Dễ đọc thành 5 giờ chiều trong khi là 17 phút làm bài |
| A12 | Không có chỗ ghi **lời dặn** cho thí sinh (mang tai nghe, không mở tài liệu…) | Phải dặn qua kênh khác |
| A13 | Chỉ đọc được mã, không có **link mời** | Lớp học trực tuyến phải gõ tay mã |

## B. Thí sinh

| # | Bất cập | Ảnh hưởng |
|---|---|---|
| B1 | **Đến muộn một phút là không vào được phòng nữa** ("Phòng đã bắt đầu làm bài, không nhận thêm người") | Kỳ thi thật vẫn cho vào muộn trong một khoảng thời gian, chỉ không bù giờ |
| B2 | Phòng chế độ **"Ai cũng vào được" không vào được**: danh sách phòng công khai không có trên giao diện, API cũng giấu mã phòng | Chế độ này vô dụng |
| B3 | Sảnh chờ chỉ ghi giờ bắt đầu, **không đếm ngược**; nút làm bài mở theo nhịp làm mới 10 giây | Mất vài giây đầu giờ, không biết còn bao lâu |
| B4 | Vào phòng xong vẫn đứng ở danh sách, phải tự tìm và bấm vào phòng | Thêm một bước thừa |
| B5 | Phòng đã kết thúc nằm lẫn với phòng đang chờ / đang thi | Danh sách ngày càng rối |
| B6 | Không có nút **rời phòng** (API đã có) | Không rời được phòng vào nhầm |
| B7 | Phòng kết thúc không hiện ngay điểm và hạng của mình ở đầu trang | Phải dò trong bảng |
| B8 | Không có thông báo khi phòng **hẹn giờ** sắp bắt đầu / tự bắt đầu (chỉ có khi người ra đề bấm tay) | Dễ lỡ giờ thi |

## Hướng làm lại

**Nguyên tắc: một phòng thi = một buổi thi cho một đề.** Đề nhiều phần (JLPT) đã có cơ chế phần thi riêng, không cần gắn nhiều đề vào một phòng.

### Người ra đề
- Tạo phòng trong **một form**: tên, **chọn đề** (chỉ đề đã có câu hỏi), sức chứa, cách vào, giờ hẹn, **số phút cho vào muộn**, **lời dặn**, tuỳ chọn *mở sảnh chờ ngay*.
- **Trang điều khiển phòng** (thay các modal): mã phòng to + chép mã + **chép link mời**; pha và đồng hồ đếm ngược; thanh hành động theo pha.
  - Tab **Theo dõi**: bảng thí sinh tự làm mới vài giây một lần — đang ở sảnh / đang làm (x/y câu, cảnh báo rớt mạng) / đã nộp (điểm) / chưa vào; bộ đếm tổng.
  - Tab **Kết quả**: bảng xếp hạng, bấm từng người để mở bài làm, xuất CSV.
- **Nhân bản phòng** cho buổi sau: giữ đề, cài đặt, lời dặn, tuỳ chọn giữ nguyên danh sách thí sinh.
- Chặn mở lại phòng đã thi; mời ra giữa giờ thì **thu bài ngay**; ẩn nút xoá khi phòng đã có người.
- Danh sách phòng chia nhóm: đang diễn ra · sắp tới & nháp · đã kết thúc.

### Thí sinh
- **Cho vào muộn** trong N phút đầu (mặc định 15, người ra đề chỉnh), hết giờ vẫn theo giờ chung của phòng.
- Vào phòng xong **đi thẳng vào sảnh**; hỗ trợ **link mời** tự điền mã.
- Sảnh chờ: lời dặn, **đồng hồ đếm ngược tới giây**, tự mở nút làm bài đúng lúc bắt đầu, báo "đang có mặt" cho người ra đề.
- Mục **Phòng công khai** để vào phòng "ai cũng vào được" không cần mã.
- Danh sách phòng chia nhóm, phòng đã kết thúc thu gọn; **nút rời phòng**; phòng kết thúc hiện ngay điểm và hạng của mình.
- Thông báo **15 phút trước giờ hẹn** và **khi phòng tự bắt đầu**.

## Kết quả làm lại

Mọi mục A1–A13, B1–B8 ở trên đã được làm theo hướng này.

### Backend
- Migration `v2.1.0/01-room-rework.yaml`, chỉ thêm cột:
  - `Rooms`: `Instructions`, `LateJoinMinutes` (mặc định 15), `ReminderSentAt`, `StartNotifiedAt`, `EndNotifiedAt`.
  - `RoomMembers`: `LastSeenAt`.
- `RoomServiceImpl` viết lại:
  - Một phòng dùng đúng một đề, đề phải có câu hỏi; tạo phòng, chọn đề và mở sảnh chờ trong một request.
  - Đổi đề trong phần Sửa phòng.
  - Chặn mở lại phòng đã thi.
  - Mời ra giữa giờ thì thu bài ngay.
  - Không rời phòng được khi đang làm bài dở.
- Endpoint mới:

  | Endpoint | Việc |
  |---|---|
  | `GET /api/rooms/{id}` | chi tiết phòng |
  | `GET /api/rooms/{id}/monitor` | theo dõi buổi thi |
  | `POST /api/rooms/{id}/duplicate` | nhân bản phòng |
  | `POST /api/rooms/{id}/presence` | thí sinh báo đang có mặt |
  | `POST /api/rooms/{id}/join-open` | vào phòng công khai |

- Job `room-scheduled-notice` chạy 30 giây một lần, có `SchedulerLock`, gửi 3 loại thông báo:
  - nhắc trước giờ thi 15 phút;
  - báo phòng hẹn giờ đã bắt đầu;
  - báo phòng hẹn giờ đã kết thúc.

  Thông báo trễ quá 10 phút thì bỏ qua.

### Frontend
- `pages/teacher/RoomManager.jsx` viết lại:
  - Danh sách phòng chia 3 nhóm.
  - Một form tạo/sửa phòng.
  - Trang điều khiển phòng gồm: mã phòng, link mời, đồng hồ đếm ngược, các nút theo pha, tab Theo dõi, tab Kết quả (mở bài làm, xuất CSV), nhân bản.
- `pages/student/StudentRooms.jsx` (file mới):
  - Vào phòng bằng mã hoặc link mời, xong là vào thẳng sảnh.
  - Có mục phòng công khai.
  - Sảnh chờ: đồng hồ đếm ngược, lời dặn, ping có mặt, nút rời phòng.
  - Phòng đã kết thúc hiện hạng và điểm của mình.
- Link mời vẫn giữ mã khi phải đăng nhập trước: `ProtectedRoute` lưu `returnTo`, `LoginPage` quay lại đúng trang đó.
- Bảng xếp hạng: đổi tên cột thành "Thời gian làm", hiện dạng `12′05″`.

### Kiểm thử (DB tạm `mockpj_room`, đã xoá sau khi test)
- Build: backend compile, unit test và `vite build` đều qua.
- API end-to-end **39/39**:
  - từ chối đề rỗng; từ chối gắn đề thứ hai;
  - presence và monitor chặn người ngoài;
  - phòng công khai không lộ mã;
  - vào muộn trong 15 phút đầu được, phòng đặt 0 phút thì bị chặn;
  - mời ra giữa giờ: bài bị thu, không vào làm lại được;
  - không rời phòng được khi đang làm bài;
  - không mở lại phòng đã thi;
  - nhân bản có và không giữ thí sinh;
  - job gửi thông báo nhắc giờ.
- Chạy giao diện thật và chụp màn hình:
  - danh sách phòng, trang điều khiển lúc sảnh chờ, lúc đang thi và lúc đã kết thúc, mở bài làm, form tạo phòng;
  - link mời khi chưa đăng nhập → đăng nhập → vào thẳng sảnh có đếm ngược;
  - sảnh lúc đang thi;
  - phòng đã kết thúc hiện "Hạng 1/1 · 4/6 điểm".

### Còn lại
- Nếu đề **công khai** được dùng trong phòng, thí sinh vẫn tự luyện được đề đó ngoài giờ phòng. Luật này đã có từ trước, lần này chưa đổi. Nên dùng đề riêng tư cho phòng thi, hoặc sau này chặn đề công khai khi gắn vào phòng.
- Khi khởi động backend chính trên DB `mockpj`, Liquibase sẽ tự chạy migration v2.1.0. Migration chỉ thêm cột, không ảnh hưởng dữ liệu đang có.
