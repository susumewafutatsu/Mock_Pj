# Rà soát chức năng Thẻ ghi nhớ — góc nhìn người học (14/09/2026)

Rà soát bằng cách đọc giao diện `Flashcards.jsx`, API `/api/student/decks`, `/reviews` và thuật toán `Sm2Scheduler`, đối chiếu với dữ liệu thật (10 bộ thẻ hệ thống, 366 trạng thái thẻ của 20 học viên).

Phần lõi đúng hướng: thẻ theo lịch SM-2, trạng thái ôn riêng từng người, phím tắt Space / 1–4. Vấn đề là người học **chỉ được dùng bộ thẻ có sẵn**, và vài luật xếp lịch làm hàng đợi sai lệch với cách học lặp lại ngắt quãng.

## A. Giao diện

| # | Bất cập | Ảnh hưởng |
|---|---|---|
| A1 | **Không tạo được bộ thẻ riêng**, không thêm / sửa / xoá thẻ. Bảng `Decks` có sẵn cột chủ bộ nhưng không có API nào | Người học không ghi được từ mới gặp khi đọc, khi làm đề |
| A2 | Không xem được **trong bộ có những thẻ gì** trước và sau khi học | Không biết bộ có hợp không; không tra lại được từ đã học |
| A3 | Chỉ có **một hàng đợi chung** cho mọi bộ | Không ôn riêng một bộ trước buổi kiểm tra |
| A4 | Không **bỏ bộ khỏi lịch học** được | Thêm nhầm là phải ôn mãi |
| A5 | Nút đánh giá chỉ ghi "Giãn chậm / Giãn đều / Giãn nhanh" | Không biết bấm xong bao lâu gặp lại |
| A6 | Thẻ đã quên rồi ôn lại vẫn hiện nhãn **"Thẻ mới"** | Sai thông tin, người học tưởng lỗi |
| A7 | Hết phiên luôn báo "quay lại vào ngày mai", kể cả khi còn thẻ đến hạn vượt trần một phiên | Bỏ sót thẻ |
| A8 | Chặng lộ trình ghi "Ôn bộ thẻ của chặng" nhưng **không bấm được** | Phải tự đi tìm bộ thẻ |

## B. Logic xếp lịch

| # | Bất cập | Ảnh hưởng |
|---|---|---|
| B1 | Bấm "Bắt đầu học bộ này" là **đổ toàn bộ thẻ (tới 500) vào hàng đợi đến hạn ngay**, không có giới hạn thẻ mới mỗi ngày | Thêm 3 bộ là 60+ thẻ mới trong một ngày, sang hôm sau dồn ôn — đúng kiểu học dồn mà SRS muốn tránh |
| B2 | Hàng đợi xếp theo `DueAt`: thẻ mới vừa thêm **chen trước thẻ ôn quá hạn**, thẻ ôn bị đẩy ra ngoài trần 100 thẻ | Thẻ sắp quên bị bỏ lại, thẻ mới lại được ưu tiên |
| B3 | Số "đến hạn" **đếm cả thẻ mới chưa từng học** | Menu, trang chủ, thông báo nhắc hằng ngày đều thổi phồng (báo 90 thẻ đến hạn khi thật ra không có thẻ nào cần ôn) |
| B4 | Hai lần ôn đầu **không phân biệt mức**: Khó / Bình thường / Dễ đều ra 1 ngày, lần hai luôn 6 ngày | Nút "Dễ" vô tác dụng với thẻ mới, thẻ đã biết vẫn bị hỏi lại liên tục |
| B5 | "Thẻ mới" xác định bằng `Repetitions = 0`, mà quên thì `Repetitions` về 0 | Gốc của A6; không đếm được số thẻ mới đã học hôm nay |
| B6 | Người ra đề gắn được **bất kỳ bộ nào** vào chặng lộ trình theo id | Khi có bộ riêng, bộ cá nhân của học viên có thể bị gắn vào khoá học |

## Hướng làm lại

### Dữ liệu
- Bảng `CustomCards`: thẻ tự tạo (mặt trước, cách đọc, mặt sau, ví dụ, ghi chú), loại thẻ mới `CUSTOM` dùng chung cơ chế `DeckItems` / `UserCardStates`.
- Bảng `DeckEnrollments`: người học đã thêm bộ nào vào lịch — để bỏ bộ khỏi lịch và để thẻ thêm sau vào bộ tự vào lịch.
- `UserCardStates.FirstReviewedAt`: mốc học lần đầu, dùng để nhận biết thẻ mới và đếm thẻ mới đã học hôm nay.

### Lịch học
- Thêm bộ vào lịch **không làm thẻ đến hạn ngay**. Mỗi ngày chỉ mở tối đa **20 thẻ mới** (cấu hình được), theo thứ tự trong bộ; có nút "Học thêm thẻ mới" khi muốn vượt.
- Hàng đợi: **thẻ ôn đến hạn trước**, thẻ mới sau.
- "Cần ôn" chỉ đếm thẻ đã từng học; "thẻ mới hôm nay" hiện riêng. Thông báo nhắc dùng cùng cách đếm.
- SM-2 phân biệt mức ngay từ đầu: thẻ mới Khó/Bình thường 1 ngày, Dễ 4 ngày; lần hai Khó 3, Bình thường 6, Dễ 8 ngày; nút đánh giá **hiện trước khoảng cách** sẽ gặp lại.

### Giao diện
- Hai nhóm: **Bộ thẻ của tôi** (nút tạo bộ) và **Bộ thẻ có sẵn**. Mỗi bộ: số thẻ, thanh tiến độ, số thẻ cần ôn, nút Học / Thêm vào lịch / Xem thẻ.
- Trang **chi tiết bộ**: danh sách thẻ kèm trạng thái (mới · đang học · đã thuộc, lần ôn tới); bộ của mình thì thêm thẻ tự soạn, **thêm từ vựng / chữ Hán có sẵn** qua ô tìm kiếm, sửa, xoá thẻ, đổi tên, xoá bộ; bỏ bộ khỏi lịch.
- **Ôn riêng một bộ** hoặc ôn tất cả. Hết phiên báo đúng còn bao nhiêu thẻ, gợi ý học thêm thẻ mới.
- Chặng lộ trình có nút mở thẳng bộ thẻ; chỉ bộ hệ thống được gắn vào chặng.

## Kết quả làm lại

Mọi mục A1–A8 và B1–B6 ở trên đã được làm theo hướng này.

### Backend
- Migration `v2.2.0/01-flashcard-rework.yaml`:
  - Tạo bảng `CustomCards` (thẻ tự soạn) và bảng `DeckEnrollments` (bộ đã thêm vào lịch học).
  - Thêm cột `UserCardStates.FirstReviewedAt` (mốc học lần đầu).
  - Dữ liệu cũ được chuyển tự động:
    - `FirstReviewedAt` lấy bằng `LastReviewedAt`.
    - Bộ nào học viên đã có ít nhất một nửa số thẻ trong lịch thì được coi là đã thêm vào lịch.
- `SrsServiceImpl` viết lại:
  - Quản lý bộ riêng: tạo / sửa / xoá bộ, soạn / sửa / gỡ thẻ, thêm từ vựng / chữ Hán có sẵn.
  - Thêm bộ vào lịch và bỏ bộ khỏi lịch; tiến độ của thẻ còn nằm ở bộ khác đang học thì được giữ.
  - Hàng đợi: thẻ ôn đến hạn xếp trước; mỗi ngày mở tối đa `study.srs.new-per-day` = 20 thẻ mới; có tham số `extraNew` để học thêm; lọc được theo `deckId`.
  - Số "cần học" = thẻ ôn đến hạn + thẻ mới còn được mở hôm nay. Trang chủ, menu và thông báo nhắc hằng ngày dùng chung cách đếm này.
- `Sm2Scheduler`:
  - Thẻ mới chọn Dễ: gặp lại sau 4 ngày.
  - Lần ôn thứ hai: Khó 3 ngày, Nhớ 6 ngày, Dễ 8 ngày.
  - Có hàm `preview` trả trước khoảng cách của từng mức, để nút đánh giá hiện ra.
- Bộ riêng chỉ chủ bộ xem được; người khác nhận 404. Người ra đề không gắn được bộ riêng của học viên vào chặng lộ trình.
- API mới, cùng gốc `/api/student`:

  | Phương thức | Đường dẫn |
  |---|---|
  | `POST` | `/decks` |
  | `GET`, `PUT`, `DELETE` | `/decks/{id}` |
  | `POST` | `/decks/{id}/cards` |
  | `PUT` | `/decks/{id}/cards/{cardId}` |
  | `POST` | `/decks/{id}/items` |
  | `DELETE` | `/decks/{id}/items/{type}/{itemId}` |
  | `DELETE` | `/decks/{id}/enroll` |
  | `GET` | `/study/search` |
  | `GET` | `/reviews/due?deckId=&extraNew=` |

### Frontend
- `Flashcards.jsx` viết lại, gồm:
  - Trang chủ bộ thẻ: dải "Hôm nay cần học" và hai nhóm "Bộ thẻ của tôi" / "Bộ thẻ có sẵn".
  - Trang chi tiết bộ: soạn thẻ, tìm và thêm từ có sẵn, danh sách thẻ kèm trạng thái và ngày gặp lại.
  - Phiên học riêng từng bộ; nút đánh giá hiện trước khoảng cách gặp lại.
  - Hết phiên thì báo đúng số thẻ còn lại, hoặc gợi ý học thêm thẻ mới.
- Chặng lộ trình có nút "Mở bộ thẻ của chặng", mở thẳng trang chi tiết bộ.

### Kiểm thử (DB tạm `mockpj_fc` sao từ `mockpj`, đã xoá sau khi test)
- Unit test `Sm2SchedulerTest` 9/9; toàn bộ unit test backend chạy lại đều qua.
- API end-to-end 40/40:
  - Dữ liệu cũ sau migration: bộ đã học được nhận đúng; hàng đợi khớp số trên trang chủ.
  - Kiểm tra đầu vào: tạo bộ, soạn thẻ báo lỗi khi thiếu tên / thiếu mặt sau.
  - Thêm từ có sẵn: tìm được, thêm trùng → 409, thêm từ không tồn tại → 404.
  - Phân quyền: người khác → 404; sửa bộ có sẵn → 403.
  - Phiên học: xem trước khoảng cách; Dễ → 4 ngày; Quên → thẻ quay lại đầu hàng đợi và không còn nhãn "Thẻ mới".
  - Giới hạn thẻ mới: thêm bộ 30 thẻ chỉ mở 18 thẻ mới trong ngày (tính cả thẻ mới đã học trước đó); `extraNew` mở thêm 10.
  - Thẻ soạn sau tự vào lịch; gỡ thẻ; bỏ bộ khỏi lịch; xoá bộ thì thẻ và tiến độ theo bộ mất hết.
  - Chặn gắn bộ riêng vào chặng lộ trình.
- Giao diện thật (ảnh chụp): trang chủ, tạo bộ, soạn 2 thẻ, tìm và thêm 今日, học bộ vừa tạo, lật thẻ thấy nút đánh giá kèm khoảng cách, quay về trang chủ thấy bộ mới.
