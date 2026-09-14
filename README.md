# Mock Project — Backend

Nền tảng ôn tập & thi trực tuyến, trọng tâm **Tiếng Nhật (JLPT)**.
Spring Boot 3.2 · Java 21 · MySQL 8 · Redis (tuỳ chọn) · Liquibase.

Frontend nằm ở repo riêng: <https://github.com/susumewafutatsu/Mock_Project>

---

## Chạy lần đầu — 4 bước

### 1. Cần có sẵn
| | Phiên bản | Ghi chú |
|---|---|---|
| JDK | **21** | `java -version` phải ra 21.x |
| MySQL | **8.x** | Đang chạy ở `localhost:3306` |
| Redis | 6+ | **Không bắt buộc.** Không có Redis app vẫn chạy, chỉ chậm hơn — xem phần dưới |
| Maven | không cần cài | Dùng `mvnw` đi kèm repo |

### 2. Tạo database rỗng

```sql
CREATE DATABASE mockpj CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Chỉ cần tạo database rỗng. **Đừng import file .sql nào** — Liquibase tự dựng toàn
bộ bảng và nạp dữ liệu mẫu khi app khởi động lần đầu.

`utf8mb4` là bắt buộc: nội dung có tiếng Nhật (kana, kanji) lẫn tiếng Việt có dấu.

### 3. Tạo file `.env`

```bash
cp .env.example .env
```

Rồi sửa `DB_USERNAME` / `DB_PASSWORD` cho khớp MySQL của bạn.
File `.env` đã bị `.gitignore` chặn — **không bao giờ commit nó lên git**.

Phần `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` để trống cũng chạy được; chỉ
riêng nút "Đăng nhập bằng Google" là không dùng được.

### 4. Chạy

```bash
./mvnw spring-boot:run
```

Lần đầu Liquibase sẽ chạy 77 changeset để dựng schema và nạp dữ liệu mẫu — mất
khoảng 20–30 giây. Những lần sau chỉ vài giây.

Xong thì mở <http://localhost:8080/swagger-ui.html> để xem toàn bộ API.

---

## Tài khoản mẫu

Dữ liệu mẫu được nạp sẵn (context `demo` bật mặc định). Mật khẩu **tất cả** là
`demo1234`:

| Vai | Email | Dùng để thử |
|---|---|---|
| Người ra đề | `teacher@demo.local` | Soạn đề, mở phòng thi, soạn khoá học |
| Quản trị viên | `admin@demo.local` | Tổng quan, quản lý người dùng, duyệt khoá học |
| Thí sinh | `student1@demo.local` | Làm bài, học khoá, ôn thẻ |
| Thí sinh | `student2@demo.local`, `student3@demo.local` | Thử phòng thi nhiều người |

Muốn một DB sạch không có dữ liệu mẫu (khi deploy thật): đặt
`LIQUIBASE_CONTEXTS=production` trong `.env`.

---

## Những chỗ dễ vấp

### "Validation Failed: N changesets check sum"
App không khởi động được vì có người **sửa nội dung một changeset đã chạy**.
Liquibase lưu checksum của từng changeset; đổi dù chỉ một dấu phẩy trong chú thích
cũng làm nó từ chối chạy.

**Quy tắc: changeset đã chạy ở đâu đó là bất biến.** Muốn đổi schema thì thêm
changeset mới trong một thư mục version mới, không sửa cái cũ.

Nếu đã lỡ sửa: hoặc hoàn nguyên đúng nội dung cũ, hoặc xoá database và tạo lại
(chỉ làm được khi chưa có dữ liệu thật).

### "Schema-validation: missing table/column"
Entity JPA không khớp với bảng dưới DB. `ddl-auto: validate` nên Hibernate không
tự sửa schema — đó là chủ đích, Liquibase mới là nơi quản lý schema. Thường là do
vừa `git pull` về changeset mới mà chưa chạy lại app, hoặc entity thêm cột mà quên
viết changeset.

### Không có Redis thì sao?
App **vẫn chạy bình thường**. Redis chỉ gánh ba việc cho MySQL: cache đề thi, khoá
lúc tạo phiên thi, và nhịp heartbeat. Mất Redis thì mọi lối gọi tự quay về đường
MySQL — chậm hơn nhưng không sai. Log sẽ có cảnh báo kết nối Redis, bỏ qua được.

### Chữ Nhật hiện thành dấu hỏi
Database không phải `utf8mb4`. Xoá và tạo lại đúng như bước 2.

---

## Cấu trúc thư mục

```
src/main/java/com/example/demo/
├── config/         SecurityConfig, RedisConfig, ExamSessionScheduler
├── controller/     REST endpoints
├── domain/         entity + enum
├── dto/            request / response
├── repository/     Spring Data JPA
├── security/       JWT + OAuth2
├── service/        nghiệp vụ (interface + impl)
└── util/           DbTime — cắt thời gian về giây trước khi ghi DB

src/main/resources/db/changelog/
├── db.changelog-master.yaml    ← khai báo thứ tự chạy
├── v1.0.x/  schema ban đầu + dữ liệu mẫu
├── v1.1.0/  tag cho câu hỏi
├── v1.2.0/  nhiều lượt làm bài
├── v1.4.0/  học tập: sổ tay câu sai, từ vựng, kanji, bộ thẻ
├── v1.5.0/  bỏ Lớp → Phòng thi
├── v1.6.0/  khoá học (ngữ pháp, chữ Hán) + duyệt
├── v1.7.0/  khoá tài khoản, lộ trình theo chặng
├── v1.8.0/  phần thi JLPT, bài đọc, dạng câu
├── v1.9.0/  nghe, thông báo, đánh dấu, khoá job
├── v2.0.0/  bộ dữ liệu thật (context realdata)
├── v2.1.0/  làm lại phòng thi
└── v2.2.0/  làm lại thẻ ghi nhớ
```

---

## Tài liệu dự án

Nằm trong [`docs/`](docs/):

| File | Nội dung |
|---|---|
| [`TRINH_BAY.md`](docs/TRINH_BAY.md) | Tóm tắt để trình bày: tính năng, phản hồi đã xử lý, kịch bản demo |
| [`PHAN_CONG.md`](docs/PHAN_CONG.md) | Phân loại chức năng và phân công cho ba thành viên |
| [`revision_plan.md`](docs/revision_plan.md) | Kế hoạch sửa theo phản hồi của người hướng dẫn, kèm các bẫy đã gặp |
| [`audit_2026-09.md`](docs/audit_2026-09.md) | Tự rà soát toàn dự án, kết quả 4 đợt sửa |
| [`room_review_2026-09.md`](docs/room_review_2026-09.md) | Rà soát và làm lại phòng thi |
| [`flashcard_review_2026-09.md`](docs/flashcard_review_2026-09.md) | Rà soát và làm lại thẻ ghi nhớ |

---

## Chạy test

```bash
./mvnw test
```

Một số test cần MySQL đang chạy (`DemoApplicationTests`, `StudentExamQueriesTest`)
vì chúng kiểm tra rằng các câu truy vấn thực sự chạy được trên DB thật, không chỉ
đúng cú pháp JPQL.
