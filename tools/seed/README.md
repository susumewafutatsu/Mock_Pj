# Bộ dữ liệu thật — nạp, gỡ, sửa

Bộ dữ liệu cho nền tảng ôn thi JLPT, thay cho dữ liệu demo: tài khoản với vai trò rõ ràng, câu hỏi JLPT N5→N2 có giải thích tiếng Việt, đề luyện tập, đề thi thử chia phần, bài xếp trình độ, phòng thi, lộ trình ôn tập, bộ thẻ, và lịch sử học tập của học viên.

| File | Vai trò |
|---|---|
| `src/main/resources/db/changelog/v2.0.0/realdata/realdata.sql` | Dữ liệu — SQL thuần, copy chạy được |
| `src/main/resources/db/changelog/v2.0.0/realdata/realdata-rollback.sql` | Gỡ sạch đúng những gì file trên thêm vào |
| `src/main/resources/db/changelog/v2.0.0/01-realdata.yaml` | Changeset Liquibase nạp file SQL trên |
| `tools/seed/generate-realdata.mjs` | Script sinh ra hai file SQL (nội dung nằm ở đây) |

## Nạp dữ liệu

### Cách 1 — qua Liquibase (khuyên dùng)

Thêm vào `Back_end/Mock_Pj/.env`:

```
LIQUIBASE_CONTEXTS=demo,realdata
```

rồi khởi động app như bình thường (`mvnw spring-boot:run`). Liquibase chạy changeset `v2.0.0-01-realdata` một lần duy nhất và ghi vào `DATABASECHANGELOG`. Muốn bỏ dữ liệu demo cũ thì đặt `LIQUIBASE_CONTEXTS=realdata` **trên DB mới** (trên DB đã có dữ liệu demo, bỏ context không xoá dữ liệu cũ).

Không thêm dòng này thì Liquibase bỏ qua hoàn toàn — DB giữ nguyên.

### Cách 2 — chạy file SQL bằng tay

Chỉ chạy **sau khi app đã khởi động ít nhất một lần** (cần đủ schema tới v1.9.0):

```
mysql -u root -p mockpj < src/main/resources/db/changelog/v2.0.0/realdata/realdata.sql
```

hoặc mở file trong MySQL Workbench và chạy toàn bộ.

### Vì sao hai cách không bao giờ đụng nhau

Changeset có precondition: nếu DB đã có tài khoản `tt-*` thì Liquibase **đánh dấu là đã chạy (MARK_RAN)** thay vì chạy lại. Nên chạy tay trước rồi bật context sau cũng không lỗi trùng khoá, không nhân đôi dữ liệu.

## Tài khoản

Mật khẩu chung: **`OnThi@2026`** — email dạng `ten.ho@tangthucac.test`.

| Vai trò | Email | Ghi chú |
|---|---|---|
| Admin | `quan.pham@tangthucac.test` | Duyệt lộ trình, quản lý người dùng |
| Người ra đề | `ha.nguyen@tangthucac.test` | N3 · N2, phòng thi thử N3 |
| Người ra đề | `anh.tran@tangthucac.test` | N4, phòng N4 đã kết thúc, một lộ trình bị trả lại |
| Người ra đề | `phuong.le@tangthucac.test` | N5, chữ Hán |
| Người ra đề | `kien.hoang@tangthucac.test` | Mới vào: một đề chưa có câu, một lộ trình nháp |
| Học viên N5 | `khang.do@`, `anh.vu@`, `huy.bui@`, `vy.dang@`, `bao.ngo@`, `yen.ly@` | |
| Học viên N5 | `linh.phan@tangthucac.test` | Vừa đăng ký, chưa làm gì |
| Học viên N5 | `kiet.mai@tangthucac.test` | Tài khoản Google — **không** đăng nhập bằng mật khẩu |
| Học viên N4 | `nam.trinh@`, `ngoc.ho@`, `dat.duong@`, `thu.ta@`, `manh.cao@`, `nhu.lam@`, `tu.kieu@`, `hang.chu@` | |
| Học viên N4 | `minh.ton@tangthucac.test` | **Đã bị khoá** |
| Học viên N3 | `long.nguyen@`, `duyen.tran@`, `thanh.le@`, `ngoc.pham@`, `phat.huynh@`, `trang.vo@` | |
| Học viên N2 | `nguyen.doan@tangthucac.test` | |

## Có gì trong bộ dữ liệu

| Loại | Số lượng |
|---|---|
| Tài khoản | 29 (1 admin · 4 người ra đề · 24 học viên) |
| Ngân hàng câu hỏi | 10 (N5/N4/N3 × 文字・語彙 · 文法 · 読解, và N2) |
| Câu hỏi | 83, mỗi câu 4 phương án + giải thích tiếng Việt, gắn kỹ năng JLPT và tag |
| Bài đọc | 6 (mỗi bài 2–3 câu) |
| Từ vựng / chữ Hán | 55 / 30 (không trùng dữ liệu N5 có sẵn) |
| Bộ thẻ | 5 |
| Đề | 16: đề luyện tập theo kỹ năng, 2 đề thi thử chia phần (N5, N4), bài xếp trình độ, đề phòng thi, 1 đề đang soạn |
| Phòng thi | 3: đã kết thúc (có bảng xếp hạng) · đang chờ giờ thi · nháp |
| Lộ trình | 6: 3 đã xuất bản (22 chặng tổng), 1 chờ duyệt, 1 bị trả lại kèm lý do, 1 nháp |
| Bài làm đã chấm | 159 (1.642 dòng chi tiết), trải trong ~3 tháng gần nhất |
| Tiến độ | 25 lượt ghi danh, 86 chặng đã qua, 358 mục sổ tay câu sai, 332 thẻ đang ôn |
| Khác | 20 câu đánh dấu kèm ghi chú, 46 thông báo |

Mọi mốc thời gian tính theo `NOW()` lúc nạp, nên dữ liệu luôn trông "mới" dù nạp vào ngày nào.

### Có chủ ý KHÔNG có

- **File nghe (聴解)**: không đóng gói được file âm thanh vào SQL. Vì vậy các đề thi thử để **tắt chấm theo thang JLPT** — bảng điểm JLPT thiếu phần nghe thì luôn ra "Trượt". Thêm câu nghe có audio qua giao diện rồi mới bật lại.
- **Dữ liệu N1**.

## Gỡ dữ liệu

```
mysql -u root -p mockpj < src/main/resources/db/changelog/v2.0.0/realdata/realdata-rollback.sql
```

Gỡ cả những thứ app đã tạo ra dựa trên bộ dữ liệu (bài làm trên các đề này, đề do tài khoản `tt-*` soạn thêm…). Không đụng dữ liệu khác.

Nếu đã nạp qua Liquibase và muốn nạp lại sau khi gỡ, xoá thêm dòng đánh dấu:

```sql
DELETE FROM DATABASECHANGELOG WHERE ID = 'v2.0.0-01-realdata';
```

## Sửa hoặc thêm dữ liệu

1. Sửa nội dung trong `generate-realdata.mjs` (câu hỏi, lộ trình, học viên…).
2. Chạy `node tools/seed/generate-realdata.mjs` — script tự dừng nếu có dấu `;` trong nội dung (Liquibase cắt câu lệnh theo `;`) hoặc từ vựng/chữ Hán trùng dữ liệu có sẵn.
3. **Trên DB đã nạp bản cũ**: chạy rollback, rồi đổi `id` trong `01-realdata.yaml` (vd `v2.0.0-01-realdata-v2`).

**Đừng sửa `realdata.sql` sau khi đã nạp mà giữ nguyên id changeset**: Liquibase lưu checksum của file và sẽ từ chối khởi động app.

## Quy ước để không va chạm dữ liệu khác

- `UserID` bắt đầu bằng `tt-`.
- ID số nằm trong dải `20001+`. Snapshot đáp án `300001+`, chi tiết bài làm `500001+`, đáp án ngân hàng / sổ tay / thẻ / thông báo `100001+`.
- Hệ quả: sau khi nạp, ID tự tăng của các bảng này nhảy lên trên dải trên (vd bài nộp mới có ID 20160…). Không ảnh hưởng gì tới hoạt động của app.

## Đã kiểm chứng

Trên hai bản sao của DB hiện tại:

- **Chạy tay**: nạp → gỡ → nạp lại cho ra đúng cùng số liệu; gỡ trả DB về đúng trạng thái ban đầu. Kiểm tra nhất quán đều bằng 0 lỗi: điểm tổng khớp từng câu, đáp án chọn thuộc đúng đề/câu, không vượt số lượt, bài của phòng nằm trong giờ phòng, chặng "đã qua" đều có bài kiểm tra đạt ngưỡng.
- **Qua Liquibase**: changeset chạy thành công. 37 kiểm tra API bằng các tài khoản trên đều đạt: đăng nhập theo vai trò, tài khoản bị khoá, lịch sử và xem lại bài, bảng xếp hạng, gợi ý ôn, lộ trình, phòng thi đúng pha, thống kê người ra đề, duyệt lộ trình, vào thi đề chia phần.
- **Chạy tay trước rồi bật Liquibase**: changeset được đánh dấu MARK_RAN, không lỗi trùng khoá.
