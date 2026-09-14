# Phân loại chức năng & phân công A · B · C

Chia theo **khối nghiệp vụ liền mạch**, mỗi người ôm trọn một khối từ bảng DB → API → giao diện, để khi trình bày ai cũng nói được một luồng đầu–cuối. Khối lượng cân theo số dòng code thực tế (backend + frontend).

## Tổng quan

| | **A — Nền tảng & soạn đề** | **B — Thi cử** | **C — Học tập & phân tích** |
|---|---|---|---|
| Người dùng chính | Người ra đề (soạn nội dung), Admin | Thí sinh làm bài, người ra đề mở phòng | Học viên tự học, người ra đề xem thống kê |
| Nhóm chức năng | Tài khoản & bảo mật · Quản trị · Thông báo & tìm kiếm · Ngân hàng câu hỏi · Soạn đề JLPT | Danh sách đề · Phiên làm bài · Kết quả & xem lại · Phòng thi | Lộ trình ôn tập · Thẻ ghi nhớ · Sổ tay câu sai & gợi ý ôn · Đánh dấu · Bảng xếp hạng · Thống kê người ra đề |
| Endpoint | ~40 | ~33 | ~45 |
| Bảng DB | 14 | 6 | 13 |
| Khối lượng (dòng code) | ~7.700 | ~8.100 | ~7.900 |

---

## A — Nền tảng, tài khoản & soạn đề

### Chức năng

1. **Tài khoản & bảo mật**
   - Đăng ký bằng email/mật khẩu (luôn là học viên), đăng nhập, đăng nhập Google.
   - JWT, refresh token trong cookie HttpOnly, đăng xuất.
   - Giới hạn tần suất đăng nhập / API, xử lý lỗi chung (`GlobalExceptionHandler`).
2. **Quản trị (Admin)**
   - Tổng quan ôn luyện.
   - Quản lý tài khoản: khoá / mở, nâng quyền.
3. **Thông báo & tìm kiếm**
   - Hạ tầng thông báo trong ứng dụng (ô chuông).
   - Tìm kiếm đề công khai và lộ trình.
4. **Ngân hàng câu hỏi**
   - Bộ câu hỏi theo trình độ / kỹ năng; câu hỏi nhiều dạng (trắc nghiệm, 並べ替え, tự luận luyện tập); tag.
   - Bài đọc hiểu dùng cho nhiều câu.
   - Tải file nghe.
5. **Soạn đề thi JLPT**
   - Tạo / sửa đề; bộ lọc chọn câu (trình độ → bộ → mức độ → dạng).
   - Chia phần thi và áp cấu trúc chuẩn JLPT.
   - Snapshot câu hỏi vào đề; luật quy đổi điểm JLPT 0–180.

### Mã nguồn

| Tầng | File |
|---|---|
| Controller | `AuthController`, `AdminController`, `NotificationController`, `SearchController`, `TeacherQuestionBankController`, `TeacherQuestionController`, `QuestionController`, `TeacherReadingPassageController`, `MediaController`, `TeacherCatalogController`, `TeacherExamController`, `TeacherExamQuestionController`, `TeacherExamSectionController` |
| Service | `AuthService`, `AdminServiceImpl`, `NotificationService`, `QuestionServiceImpl`, `QuestionBankServiceImpl`, `SubjectLevelServiceImpl`, `TeacherExamServiceImpl`, `TeacherExamSectionServiceImpl`, `ExamSnapshotServiceImpl`, `JlptScoringService` |
| Hạ tầng | `security/*` (JWT, OAuth2, `RateLimitFilter`, `RefreshTokenCookie`), `SecurityConfig`, `MediaConfig`, `OpenApiConfig`, `exception/*`, `util/DbTime` |
| Frontend | `pages/auth/*`, `context/AuthContext`, `components/common/ProtectedRoute`, `NotificationBell`, `SearchBox`, `pages/admin/UserManagement`, `Overview`, `Dashboard`, `pages/teacher/QuestionBank`, `components/question/QuestionForm`, `pages/teacher/ExamManager`, `ExamSectionEditor`, `services/api.js`, `authService`, `adminService`, `questionService`, `teacherExamService`, `examStructureService` |
| Bảng DB | `Users`, `Subjects`, `SubjectLevels`, `QuestionBanks`, `Questions`, `Answers`, `Tags`, `QuestionTags`, `ReadingPassages`, `Exams`, `ExamQuestions`, `ExamQuestionAnswers`, `ExamSections`, `Notifications` |
| Migration & dữ liệu | v1.0.x, v1.1.0, v1.2.0, v1.7.0/01 (khoá tài khoản), v1.8.0; bộ dữ liệu thật `tools/seed` + v2.0.0 |

### Điểm nhấn khi trình bày

- Lỗ tự cấp quyền Admin khi đăng ký đã được bịt; sai email và sai mật khẩu trả về cùng một câu thông báo.
- Snapshot đề: sửa ngân hàng câu hỏi không làm hỏng bài đã thi.
- Cấu trúc JLPT: phần thi, điểm quy đổi, điểm liệt từng phần.
- Liquibase context `realdata` có precondition, nên nạp bộ dữ liệu thật không bao giờ bị trùng.

---

## B — Thi cử: làm bài & phòng thi

### Chức năng

1. **Danh sách đề của thí sinh**
   - Bài được giao theo phòng, đề luyện tập tự do có phân trang và lọc theo trình độ.
2. **Phiên làm bài**
   - Vào thi không trùng phiên; đồng hồ theo giờ server.
   - Tự lưu đáp án: nháp `localStorage` + gửi theo lô; khôi phục khi thoát trình duyệt / chuyển tab.
   - Heartbeat và cờ mất kết nối; job tự nộp bài khi quá giờ.
   - Khoá phần thi hết giờ, xáo câu / xáo đáp án, giới hạn lượt nghe.
3. **Kết quả**
   - Lịch sử điểm, xem lại bài có giải thích, thẻ điểm JLPT (Đỗ / Trượt).
4. **Phòng thi**
   - Tạo buổi thi (một phòng một đề), mã phòng / link mời, sức chứa, cấp ghế.
   - Sảnh chờ có đếm ngược, bắt đầu làm bài / hẹn giờ, cho vào muộn.
   - Theo dõi trực tiếp, mời ra (thu bài ngay), kết thúc sớm, nhân bản phòng.
   - Thông báo nhắc giờ thi.

### Mã nguồn

| Tầng | File |
|---|---|
| Controller | `StudentController`, `RoomController` |
| Service | `ExamServiceImpl`, `SubmissionServiceImpl`, `RoomServiceImpl`, `ExamRedisService`, `PaperShuffler`, `ExamSectionTiming` |
| Hạ tầng | `ExamSessionScheduler` (job tự nộp bài, cờ mất kết nối, thông báo phòng), `SchedulerLock`, `RedisConfig` |
| Frontend | `pages/student/ExamList` (khung trang học viên), `ExamRoom`, `SubmissionReview`, `ResultHistory`, `StudentRooms`, `components/exam/JlptScoreCard`, `hooks/useAnswerSync`, `useExamTimer`, `utils/examDraft`, `pages/teacher/RoomManager`, `services/examService`, `roomService` |
| Bảng DB | `ExamSubmissions`, `SubmissionDetails`, `Rooms`, `RoomMembers`, `RoomExams`, `SchedulerLocks` |
| Migration | v1.0.2 (gia cố phiên thi), v1.5.0 (bỏ Lớp → Phòng thi), v1.9.0 (khoá job), v2.1.0 (làm lại phòng thi) |

### Điểm nhấn khi trình bày

- Server là nguồn thời gian duy nhất; thoát trình duyệt không được cộng giờ.
- Chống trùng phiên (khoá duy nhất + khoá dòng); tranh ghế "ai nhanh thì vào" dùng `SELECT … FOR UPDATE`.
- Autosave 2 tầng; chọn `localStorage` thay vì `sessionStorage` và lý do.
- Mỗi phòng thi đi qua các pha Nháp → Sảnh chờ → Đang thi → Kết thúc; pha được tính từ giờ, không cần job canh từng giây.

---

## C — Học tập & phân tích

### Chức năng

1. **Lộ trình ôn tập**
   - Người ra đề soạn chặng; Admin duyệt / trả lại kèm lý do.
   - Học viên đi chặng tuần tự; chặng có bài kiểm tra phải đạt ngưỡng mới qua.
2. **Thẻ ghi nhớ**
   - Bộ có sẵn và bộ tự tạo; soạn thẻ, thêm từ vựng / chữ Hán có sẵn.
   - Lịch SM-2, mỗi ngày mở tối đa 20 thẻ mới, học riêng từng bộ.
3. **Sổ tay câu sai & gợi ý ôn**
   - Tự gom câu làm sai, làm lại tới khi sửa được.
   - Tỉ lệ đúng theo kỹ năng, chủ điểm yếu; bài xếp trình độ đầu vào.
4. **Đánh dấu câu kèm ghi chú.**
5. **Bảng xếp hạng**
   - Của phòng thi và của đề tự do (bằng điểm thì ai làm nhanh hơn đứng trên).
6. **Thống kê cho người ra đề**
   - Tổng quan việc cần làm, danh sách thí sinh, bài nộp theo đề / phòng.
   - Tỉ lệ đúng từng câu và theo tag, xuất CSV.

### Mã nguồn

| Tầng | File |
|---|---|
| Controller | `CourseController`, `StudentStudyController`, `StudentStudyExtrasController`, `TeacherAnalyticsController` |
| Service | `CourseServiceImpl`, `SrsServiceImpl`, `srs/Sm2Scheduler`, `StudyServiceImpl`, `MistakeBookServiceImpl`, `StudyInsightService`, `BookmarkService`, `LeaderboardServiceImpl`, `AnalyticsServiceImpl` |
| Frontend | `pages/student/Courses`, `Flashcards`, `MistakeBook`, `Bookmarks`, `Rankings`, `components/study/InsightsCard`, `components/leaderboard/Leaderboard`, `pages/teacher/CourseManager`, `ResultView`, `StudentsView`, `TeacherOverview`, `pages/admin/CourseReviewQueue`, `services/courseService`, `studyService`, `analyticsService`, `engagementService` |
| Bảng DB | `Courses`, `CourseLessons`, `CourseEnrollments`, `LessonCompletions`, `MistakeEntries`, `VocabItems`, `KanjiItems`, `Decks`, `DeckItems`, `UserCardStates`, `CustomCards`, `DeckEnrollments`, `Bookmarks` |
| Migration | v1.4.0 (sổ tay, từ vựng / chữ Hán, SRS), v1.6.0 (khoá học), v1.7.0/02 (lộ trình theo chặng), v2.2.0 (làm lại thẻ ghi nhớ) |

### Điểm nhấn khi trình bày

- Khác biệt giữa "khoá học" và "lộ trình ôn tập": luật đi tuần tự, ngưỡng đạt tính theo lượt tốt nhất.
- SM-2: nút đánh giá hiện trước khoảng cách gặp lại; thẻ ôn xếp trước, thẻ mới giới hạn mỗi ngày để không học dồn.
- Gợi ý ôn theo điểm yếu dựa trên kỹ năng gắn với từng câu hỏi.
- Bảng xếp hạng: thí sinh chỉ thấy bảng của phòng sau khi hết giờ, và chỉ tính lượt bắt đầu trong giờ phòng.

---

## Chỗ các phần gặp nhau

| Từ | Tới | Giao ước |
|---|---|---|
| A → B | Đề thi | B đọc snapshot câu hỏi, phần thi và `JlptScoringService` của A khi chấm bài; A không đổi chữ ký các hàm này nếu chưa báo B |
| A → B, C | Thông báo | B (phòng thi) và C (lộ trình, nhắc ôn thẻ) gọi `NotificationService.notify / notifyAll`; loại thông báo khai báo trong `NotificationService.Kind` |
| A → tất cả | Xác thực | Mọi API lấy người dùng từ JWT; lỗi nghiệp vụ ném `BusinessException` / `ResourceNotFoundException` để trả đúng 409 / 404 |
| B → C | Bài làm | Bảng xếp hạng, thống kê, sổ tay câu sai, gợi ý ôn và bài kiểm tra của chặng đều đọc `ExamSubmissions` / `SubmissionDetails` của B |
| B ↔ C | Giao diện học viên | `ExamList.jsx` (B) là khung trang và thanh điều hướng; các tab Lộ trình, Thẻ ghi nhớ, Sổ tay, Đánh dấu, Bảng xếp hạng là component của C gắn vào |

**Quy ước chung**
- **Migration Liquibase:** ai tạo bảng thì người đó viết migration. Changeset đã chạy thì **không sửa**, muốn đổi thì thêm changeset mới. File `db.changelog-master.yaml` dùng chung: chỉ thêm dòng vào cuối.
- **File dùng chung:** `services/api.js`, `utils/constants.js`, `index.css` do A giữ; sửa thì báo A.
- **Trình bày:** mỗi người demo đúng luồng của mình theo kịch bản trong `TRINH_BAY.md`. Thứ tự: A (đăng nhập, soạn đề) → B (mở phòng, làm bài, kết quả) → C (thống kê, lộ trình, thẻ ghi nhớ).
