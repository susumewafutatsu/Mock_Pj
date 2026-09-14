# Kế Hoạch Sửa Dự Án — Theo Phản Hồi Của Người Hướng Dẫn

> Nền tảng ôn tập & thi trực tuyến, trọng tâm **Tiếng Nhật (JLPT)**.
> Tài liệu này thay thế phần định hướng của `implementation_plan.md`
> (file cũ chỉ là scaffold chia module, vẫn dùng được cho phần đã làm xong).

---

## 0. Tóm tắt: dự án phải đổi những gì

Phản hồi gom lại thành **4 nhóm thay đổi**, xếp theo mức ảnh hưởng:

| # | Nhóm | Bản chất | Ảnh hưởng |
|---|---|---|---|
| A | **Đổi mô hình tổ chức**: bỏ Lớp → Phòng thi (user group) | Refactor domain | 25 file BE, 8 file FE, 1 changelog migrate |
| B | **Bổ sung nghiệp vụ còn thiếu**: nội dung học tập, lộ trình ôn tập, chấm tự luận, phòng thi thử, bookmark/ghi chú/tìm kiếm | Feature mới | ~50 file mới |
| C | **Chuyên biệt hoá tiếng Nhật** | Feature mới + đổi schema câu hỏi | ~20 file |
| D | **Kỹ thuật phòng thi**: scale autosave, concurrency đa tab, security, thoát trình duyệt | Hardening | ~15 file, có phần đã làm |

**Hai lỗ hổng nghiệp vụ lớn nhất** — lớn hơn cả những gì phản hồi nêu trực tiếp:
1. **Không có nghiệp vụ học tập** (§3.4). Toàn bộ domain chỉ có một động từ: *làm bài*.
   Không bài học, không từ vựng, không kanji, không thẻ ghi nhớ. Thí sinh vào hệ thống
   thì thi được chứ không học được gì.
2. **Không có đường chấm bài tự luận** (§8). Câu tự luận bị đặt 0 điểm rồi nằm đó
   vĩnh viễn vì không tồn tại endpoint nào để chấm.

**Điểm mạnh đang có** (không đập đi): server là nguồn thời gian duy nhất
(`ExamSubmission.expiresAt`), heartbeat + `AtRiskStatus`, job tự nộp bài quá giờ,
snapshot đề thi (`ExamQuestion`/`ExamQuestionAnswer`) nên sửa ngân hàng câu hỏi
không làm hỏng bài đã thi, Redis cache + throttle `LastActiveAt`, khoá UNIQUE
`(ExamID, StudentID, AttemptNumber)` chống trùng phiên, `ExamOptionView` **không**
lộ `isCorrect` ra client.

---

## 1. Thuật ngữ — ✅ ĐÃ LÀM XONG

Phản hồi *"tạo kì thi mới" sai tên use case, dễ lẫn → "tạo bài thi mới"* không chỉ
là đổi một cái nhãn. Hiện hệ thống dùng một chữ "Exam" cho ba khái niệm khác nhau,
đó mới là nguồn gốc nhầm lẫn. Chốt bảng thuật ngữ này rồi áp vào **use case, tài
liệu, tên biến, và chữ trên UI**:

| Thuật ngữ chốt | Tiếng Anh (code) | Là gì | Nhầm với |
|---|---|---|---|
| **Đề thi** | `Exam` | Bộ câu hỏi + thời lượng + cấu hình, do người ra đề tạo | — |
| **Bài thi** | `Exam` (khi nói hành động tạo) | Cách gọi đề thi trên UI người dùng cuối | ~~kì thi~~ |
| **Phòng thi** | `Room` | Nhóm người + đề thi đính kèm + khung giờ | ~~lớp~~ |
| **Lượt làm bài** | `ExamSubmission` / attempt | Một lần thí sinh vào làm | ~~bài thi~~ |
| **Thí sinh** | `Candidate` (giữ `Role.STUDENT`) | Người làm bài, **không thuộc lớp nào** | ~~học sinh~~ |
| **Người ra đề** | `Author` (giữ `Role.TEACHER`) | Người tạo đề, chấm tự luận, mở phòng | ~~giáo viên~~ |

**Đã làm:** quét toàn bộ `kì thi`/`kỳ thi` → `bài thi`, `học sinh` → `thí sinh`,
`giáo viên` → `người ra đề` trên cả backend lẫn frontend; sửa riêng từng chỗ chữ
"lớp" mang nghĩa lớp học (giữ nguyên chỗ "lớp" nghĩa là *class* của Java). Nút
"Quản lý lớp học" trên dashboard người ra đề trỏ sang tab `rooms` — trước khi sửa
nó trỏ tới tab `classes` đã bị xoá, tức là một nút chết.

Không đổi tên bảng/entity `Exam` (rủi ro cao, lợi ích thấp) — chỉ đổi **chữ trên UI
và trong tài liệu**. `ClassEntity` thì xoá hẳn ở §2.

> **⚠ Bẫy của việc tìm-thay hàng loạt — đã vấp phải hai lần trong một lần chạy:**
>
> 1. **Sửa file changelog đã chạy làm hỏng Liquibase.** Liquibase lưu checksum của
>    từng changeSet; đổi một chữ trong chú thích cũng đủ để app từ chối khởi động
>    với `Validation Failed: 4 changesets check sum`. **Changeset đã chạy ở đâu đó
>    là bất biến** — muốn đổi thì thêm changeSet mới. Đã hoàn nguyên toàn bộ chữ
>    trong `db/changelog/**`.
>
> 2. **Sửa nhầm dữ liệu từ điển.** Seed từ vựng N5 có cột nghĩa, và lệnh thay
>    hàng loạt đã biến 学生 (*gakusei* = học sinh) thành "thí sinh", 先生
>    (*sensei* = giáo viên) thành "người ra đề". Đó là **nghĩa từ điển, không phải
>    nhãn giao diện** — người học sẽ học thuộc cái sai. Đã hoàn nguyên và ghi cảnh
>    báo ngay đầu file seed.
>
> Bài học chung: thuật ngữ của ứng dụng chỉ áp lên **nhãn giao diện và tài liệu**.
> Không bao giờ quét nó lên dữ liệu nội dung hay lên changelog đã phát hành.

---

## 2. Bỏ Lớp → Phòng thi (user group) — ✅ ĐÃ LÀM XONG

> *"+1 bỏ lớp để actor thí sinh tự do thi → tổ chức theo user group: rỗng, giới hạn
> người, ai nhanh thì vào → đính kèm đề thi vào group"*

### Hiện trạng
`ClassEntity` + `ClassStudent` là **điều kiện bắt buộc** để làm bài giao:
`SubmissionServiceImpl.requireEnrolled()` chặn ai không có trong `ClassStudents`.
Thí sinh tự do chỉ vào được nhánh `practice-exams`. Mô hình lớp cố định (giáo viên
thêm từng học sinh) không hợp với sản phẩm luyện thi mở.

### Mô hình mới

```
Room (Phòng thi)
├─ roomId, name, code (mã tham gia, unique)
├─ ownerId → User (người ra đề)
├─ capacity        NULL = không giới hạn; >0 = giới hạn người, ai nhanh thì vào
├─ joinPolicy      OPEN | CODE | APPROVAL
├─ status          DRAFT | OPEN | RUNNING | CLOSED
├─ startTime, endTime
└─ createdAt

RoomMember   (roomId, userId) PK kép, joinedAt, seatNo, status ACTIVE|LEFT|KICKED
RoomExam     (roomId, examId) PK kép, orderNo   ← đính kèm đề thi vào group
```

`Exam.classEntity` → **bỏ hẳn**. Đề thi không còn thuộc về nhóm nào; quan hệ đi qua
`RoomExam`. Một đề dùng lại được ở nhiều phòng — đây chính là thứ mô hình lớp cũ
không làm được. Đề không nằm trong `RoomExam` nào và có `isPublic = true` thì là
**đề luyện tập tự do**, ai cũng làm được.

### "Ai nhanh thì vào" — chỗ dễ sai nhất

Đây là một **race condition thật**: 200 người bấm Tham gia cùng lúc vào phòng 50 chỗ.
`SELECT COUNT(*) ... IF count < capacity THEN INSERT` sẽ cho vào quá số chỗ.

**Đã cài đặt, theo thứ tự:**
1. Khoá dòng phòng (`SELECT ... FOR UPDATE` trên `Rooms`) để xếp hàng hai request
   tranh cùng một phòng. Hai phòng khác nhau vẫn nhận người song song.
2. Đọc số ghế lớn nhất **bằng khoá**: `SELECT COALESCE(MAX(SeatNo),0) FROM
   RoomMembers WHERE RoomID = ? FOR UPDATE`.
3. `UNIQUE (RoomID, SeatNo)` là lưới an toàn cuối; đụng nó thì trả `409` với câu
   "Phòng vừa hết chỗ" chứ không để lộ ra 500.
4. Idempotent: người đã là thành viên bấm lại → trả về ghế cũ, không cấp ghế mới.
   Người đã rời phòng quay lại cũng nhận **đúng ghế cũ** — ghế đã cấp không bao
   giờ cấp lại.

> **⚠ Bẫy đã vấp phải khi chạy thật — bước 2 là kết quả của nó, không phải thiết kế ban đầu.**
>
> Bản đầu tiên chỉ có khoá dòng phòng rồi `SELECT COUNT(*)` thường. Chạy 3 người
> tranh 2 ghế thì **hai người cùng nhận ghế số 1** và đụng UNIQUE. Nguyên nhân:
> MySQL InnoDB mặc định ở mức `REPEATABLE READ`. Ảnh chụp dữ liệu của một
> transaction được lập ở câu **đọc thường đầu tiên** của nó (ở đây là lúc tra
> phòng theo mã), và mọi câu đọc thường sau đó — **kể cả sau khi đã giành được
> khoá** — vẫn nhìn vào ảnh chụp cũ đó. Khoá xếp đúng thứ tự nhưng không làm mới
> ảnh chụp; chỉ đọc-có-khoá (`FOR UPDATE`) mới thấy bản mới nhất đã commit.
>
> Bài học: **khoá không đồng nghĩa với đọc được dữ liệu mới.** Ở mức
> `REPEATABLE READ`, mọi con số dùng để quyết định ghi phải đọc bằng `FOR UPDATE`.
>
> Redis `INCR` (bản kế hoạch ban đầu) **chưa làm** — khoá DB đã đủ đúng và đủ
> nhanh ở quy mô một phòng vài trăm người, còn thêm Redis vào đường ghi là thêm
> một nguồn sai lệch phải đồng bộ. Để dành cho lúc đo tải cho thấy cần.

### Đã làm

**Database** — `db/changelog/v1.5.0/` (3 file tách riêng có chủ đích):
- `01-create-rooms.yaml` — Rooms, RoomMembers, RoomExams + `Exams.IsPublic`,
  index `(RoomID, SeatNo) UNIQUE`, `(UserID)`.
- `02-migrate-classes-to-rooms.yaml` — Class → Room (`capacity` NULL,
  `joinPolicy = CODE`, `code = CourseCode`); ClassStudent → RoomMember, `SeatNo`
  cấp theo thứ tự `JoinedAt`; `Exam.ClassID` → RoomExam; `ClassID IS NULL` →
  `IsPublic = TRUE`. **RoomID giữ đúng ClassID cũ** để log và đường dẫn cũ còn
  đối chiếu được.
- `03-drop-classes.yaml` — bỏ FK `Exams.ClassID`, drop Classes/ClassStudents.
  **KHÔNG LÙI ĐƯỢC** — đầu file có sẵn câu SQL đối chiếu số dòng để chạy trước.

**Kết quả migrate trên DB thật:** 3 lớp → 3 phòng, 4 học sinh → 4 thành viên,
9 đề gắn lớp → 9 dòng RoomExams, 28 đề tự do → 28 đề công khai. Khớp từng cặp.

**Backend — xoá:** `ClassEntity`, `ClassStudent(Key)`, `ClassRepository`,
`ClassStudentRepository`, `ClassService(Impl)`, `StudentClassService(Impl)`,
`ClassResponse`, `ClassStudentResponse`, `ClassExamGroup`,
`ClassCreateRequest`, `ClassUpdateRequest`.

**Backend — thêm:** `domain/model/Room|RoomMember|RoomExam(+Key)`,
`domain/enums/JoinPolicy|RoomStatus|MemberStatus`, `repository/Room*Repository`,
`service/RoomService(+Impl)` (create/open/close, join có tranh ghế, attach đề thi,
kick), `controller/RoomController` (`/api/rooms/**`).

**Backend — sửa:** `SubmissionServiceImpl.requireEnrolled()` →
`requireCanTakeExam(exam, user)`: đề công khai thì cho qua; đề gắn phòng thì phải là
`RoomMember` ACTIVE của một phòng đang OPEN/RUNNING có chứa đề đó.
Kèm theo: `ExamServiceImpl`, `TeacherExamServiceImpl`, `AnalyticsServiceImpl`,
`ExamRepository` (bỏ query theo ClassID), `StudentController`, `TeacherController`.

**Frontend — xoá/đổi:** `ClassManager.jsx` → `RoomManager.jsx` (có vòng đời phòng
Nháp→Mở→Đang thi→Đóng, mã phòng bấm để chép, danh sách thành viên theo ghế, gắn/gỡ
bài thi); `classService.js` → `roomService.js`; panel "Lớp học của tôi" của thí sinh
→ **`StudentRooms`** có ô nhập mã phòng ngay trên đầu; sửa `ExamList.jsx`,
`ExamManager.jsx`, `QuestionBank.jsx`, `examService.js`.

Form tạo bài thi bỏ dropdown "Lớp áp dụng", thay bằng lựa chọn **Công khai / Chỉ
trong phòng thi**: tạo bài thi và giao bài thi giờ là hai thao tác tách rời, vì một
bài thi gắn được vào nhiều phòng nên "nơi áp dụng" không còn là thuộc tính của bài thi.

---

## 3. Thí sinh tự do: học tập / lộ trình / tìm kiếm / bookmark / ghi chú

> *"mỗi người có 1 rec lộ trình luyện thi → bỏ lớp, học sinh (mà họ tìm kiếm/bookmark/ghi chú)"*

Bỏ lớp thì thí sinh mất đường vào nội dung. Phải thay bằng **công cụ tự tìm nội dung**.

### 3.1 Tìm kiếm (UC-Tìm đề thi / câu hỏi)
- `GET /api/search?q=&type=EXAM|QUESTION&level=&skill=&tags=&page=`
- Giai đoạn 1: MySQL `FULLTEXT INDEX` trên `Exams.Title`, `Questions.Content`.
  **Lưu ý tiếng Nhật:** bắt buộc `FULLTEXT(...) WITH PARSER ngram` — tiếng Nhật
  không có dấu cách giữa từ, parser mặc định sẽ coi cả câu là một token và tìm kiếm
  hỏng hoàn toàn.
- Giai đoạn 2 (nếu kịp): Elasticsearch + kuromoji analyzer.
- Đã có sẵn `Tag` + `QuestionTags` → dùng làm facet lọc, không phải làm mới.

### 3.2 Bookmark & Ghi chú
```
Bookmark  (userId, targetType EXAM|QUESTION, targetId, createdAt)  UNIQUE(3 cột đầu)
UserNote  (noteId, userId, targetType, targetId, content TEXT, updatedAt)
```
- `POST/DELETE /api/me/bookmarks`, `GET /api/me/bookmarks?type=`
- `PUT /api/me/notes/{type}/{id}`, `GET /api/me/notes`
- **Bảo mật:** ghi chú là dữ liệu riêng tư — mọi query đều kèm `userId` lấy từ token,
  không bao giờ lấy từ request body.
- **Nghiệp vụ:** trong phòng thi thật **không** hiện ghi chú (tránh thành phao).
  Chỉ hiện ở chế độ luyện tập và trang xem lại bài.

### 3.3 Lộ trình ôn tập (UC-Xem lộ trình, UC-Gợi ý lộ trình) — **thiếu hoàn toàn**

> **Chặng không chỉ là bài thi.** Nếu mỗi `StudyPathStage` chỉ trỏ tới một `examId`
> thì cái gọi là "lộ trình học" thực ra chỉ là một chuỗi bài thi xếp theo thứ tự —
> thí sinh vẫn chỉ đi làm bài chứ chưa hề được học. Vì vậy chặng có **ba loại**, và
> nội dung học tập là §3.4.

```
StudyPath        (pathId, levelId → SubjectLevel, name, description, isDefault)
StudyPathStage   (stageId, pathId, orderNo, title, skill, targetTagIds,
                  stageType LESSON | DECK | EXAM,      ← chặng học / chặng thẻ / chặng kiểm tra
                  refId,                                ← lessonId | deckId | examId tuỳ stageType
                  minScoreToPass)
PathEnrollment   (enrollmentId, userId, pathId, currentStageId, startedAt, targetDate)
StageProgress    (enrollmentId, stageId, status LOCKED|IN_PROGRESS|PASSED, bestScore, attempts)
```

Một chặng điển hình của N5 vì thế là: **học** ngữ pháp 「〜てもいいですか」 →
**thuộc** bộ 40 thẻ từ vựng bài đó → **kiểm tra** 15 câu. Điều kiện qua chặng cũng
khác nhau theo loại: `LESSON` xong là đọc hết mục; `DECK` xong là thuộc ≥ 80% số thẻ;
`EXAM` xong là `bestScore >= minScoreToPass`.

**Cơ chế gợi ý (rule-based, không cần ML — đủ cho scope đồ án):**
1. **Bài kiểm tra xếp trình độ** khi đăng ký (20 câu trải N5→N2) → suy ra `levelId`
   khởi điểm → gán `StudyPath` mặc định của level đó.
2. Sau mỗi lượt làm bài, `AnalyticsService` tổng hợp tỉ lệ đúng **theo Tag và theo
   kỹ năng** (`SubmissionDetail` join `QuestionTags`) → điểm yếu = tag có tỉ lệ đúng
   thấp nhất **và** đủ số câu mẫu (≥ 5 câu, tránh kết luận từ 1 câu).
3. `GET /api/me/recommendations` trả về: chặng kế tiếp + 3 đề luyện tập nhắm vào tag
   yếu nhất + số ngày còn lại tới `targetDate`.
4. Chặng mở khoá tuần tự: `PASSED` khi `bestScore >= minScoreToPass`.

**Frontend mới:** `pages/student/StudyPath.jsx` (đường đi các chặng: đã qua / đang mở
/ còn khoá), `pages/student/PlacementTest.jsx`, `components/path/StageCard.jsx`,
`components/path/WeakSkillRadar.jsx`, `pages/student/MyLibrary.jsx` (bookmark + ghi chú).

**Ước lượng: 5–6 ngày.**

### 3.4 Nghiệp vụ HỌC TẬP — **lỗ hổng lớn nhất của cả dự án**

Toàn bộ domain hiện tại chỉ có một động từ: **làm bài**. Không tồn tại bất kỳ entity
nào mang nội dung để học — không bài học, không từ vựng, không kanji, không thẻ ghi
nhớ. Một nền tảng luyện thi ngôn ngữ mà thí sinh không học được gì thì nó là **phần
mềm chấm thi**, không phải phần mềm ôn tập. Đây là khoảng cách lớn hơn cả việc bỏ lớp.

Bốn khối dưới đây xếp theo tỉ lệ *giá trị / công sức*, cao xuống thấp.

#### (a) Sổ tay câu sai — ✅ ĐÃ LÀM XONG

Dữ liệu **đã có sẵn**: `SubmissionDetail.isCorrect = false`. Chỉ cần đọc ra và dựng
thành hàng đợi ôn lại, không cần nhập thêm một dòng nội dung nào.

```
MistakeEntry (userId, questionId, wrongCount, lastWrongAt, masteredAt NULL, nextReviewAt)
```
- `GET /api/me/mistakes?skill=&level=` — danh sách câu từng sai, kèm giải thích.
- `POST /api/me/mistakes/practice` — sinh một bộ luyện tập **từ chính câu đã sai**.
- Sai lại thì `wrongCount++` và đẩy `nextReviewAt` gần lại; đúng 2 lần liên tiếp thì
  `masteredAt` được đặt và câu rời khỏi hàng đợi.

**Đã cài đặt:** `MistakeEntry` + `MistakeEntryRepository` + `MistakeBookService(Impl)`,
API `GET /api/student/mistakes` và `POST /api/student/mistakes/{questionId}/attempt`,
màn hình `pages/student/MistakeBook.jsx`. Câu sai được đẩy vào sổ tay ngay trong
`SubmissionServiceImpl.finishSession()` — bọc try/catch vì sổ tay lỗi không được
phép làm hỏng việc nộp bài. Câu **tự luận không vào sổ tay**: nó mang
`IsCorrect = false` vì chưa được chấm chứ không phải vì làm sai.

#### (b) Thẻ ghi nhớ từ vựng & Kanji + lặp lại ngắt quãng (SRS) — ✅ ĐÃ LÀM XONG

Đây là **nghiệp vụ học cốt lõi** của mọi app học tiếng Nhật nghiêm túc, và hiện không
có gì. Học JLPT về bản chất là thuộc khoảng 800 (N5) đến 10 000 (N1) từ và 100–2 000
chữ Hán — việc đó không giải quyết được bằng cách làm đề.

```
VocabItem   (vocabId, word, reading, meaning, jlptLevel, partOfSpeech,
             exampleSentence, exampleMeaning, audioUrl)
KanjiItem   (kanjiId, character, onyomi, kunyomi, meaning, strokeCount,
             radical, jlptLevel, mnemonics)
Deck        (deckId, name, levelId, ownerId NULL = hệ thống, isPublic)
DeckItem    (deckId, itemType VOCAB|KANJI, itemId, orderNo)
UserCardState (userId, itemType, itemId,
               easeFactor, intervalDays, repetitions, dueAt, lapses, lastReviewedAt)
```

- **Thuật toán:** SM-2 (bản rút gọn của Anki) hoặc Leitner 5 hộp. SM-2 chỉ khoảng 30
  dòng code và đủ để bảo vệ đồ án: người học tự đánh giá `AGAIN | HARD | GOOD | EASY`,
  hệ thống cập nhật `easeFactor` và `intervalDays` rồi tính `dueAt`.
- `GET /api/me/reviews/due` — thẻ đến hạn hôm nay (mặc định giới hạn 100 thẻ/ngày,
  tránh dồn cục làm người học bỏ cuộc).
- `POST /api/me/reviews/{itemType}/{itemId}` — gửi kết quả ôn, nhận `dueAt` mới.
- **Xử lý riêng cho tiếng Nhật** (dùng lại §4.4): so đáp án gõ tay phải chuẩn hoá NFKC;
  thẻ kanji hiển thị được cả 音読み/訓読み; thẻ từ vựng phát audio; furigana bật/tắt.
- Nguồn dữ liệu: seed sẵn danh sách từ vựng/kanji theo cấp JLPT bằng Liquibase
  (`v1.4.0/03-seed-vocab-kanji-n5.yaml`) — không bắt người ra đề nhập tay 800 từ.

**Đã cài đặt:** `VocabItem`, `KanjiItem`, `Deck`, `DeckItem`, `UserCardState`;
thuật toán SM-2 tách thành hàm thuần `service/srs/Sm2Scheduler` (test được không
cần Spring); API `GET /decks`, `POST /decks/{id}/enroll`, `GET /reviews/due`,
`POST /reviews/{itemType}/{itemId}`; màn hình `pages/student/Flashcards.jsx` có
phím tắt Space và 1–4. Seed sẵn 40 từ vựng + 20 chữ Hán N5 trong 5 bộ thẻ.

> **Bẫy đã gặp khi chạy thật — ghi lại để không ai vấp lại.**
> Cột `DATETIME` của MySQL không có giây lẻ và nó **làm tròn** thay vì cắt bỏ:
> `09:00:15.837` vào DB thành `09:00:16`, tức một mốc nằm ở *tương lai* so với
> lúc ghi. Hậu quả: ghi danh xong mở hàng đợi ngay thì thấy trống trơn, vì mọi
> thẻ vừa tạo đều "chưa tới hạn". Lỗi này **không lộ ra ở test đơn vị** — nơi
> không có MySQL. Cách chữa là cắt về giây ở phía Java trước khi ghi:
> `com.example.demo.util.DbTime`. Mọi mốc thời gian được đem so với hiện tại
> đều phải đi qua đó.

#### (c) Khoá học — bài học lý thuyết, đọc xong bấm hoàn thành — ✅ ĐÃ LÀM XONG

> **Sửa lại sau phản hồi:** *"ôn tập không chỉ là từ vựng mà còn là ngữ pháp,
> kanji không hẳn là dùng bộ thẻ"* và *"khoá học ở đây chỉ đơn thuần là click vào
> đọc được lý thuyết, nhấn hoàn thành là ra phần trăm, không phức tạp"*.

##### Vì sao bản trước sai: ép ba loại nội dung vào một hình dạng

Bản §3.4(b) coi mọi thứ đều là thẻ lật. Đó là sai, vì ba loại nội dung của JLPT có
hình dạng khác hẳn nhau:

| | Từ vựng (語彙) | Chữ Hán (漢字) | Ngữ pháp (文法) |
|---|---|---|---|
| Bản chất | Một từ ↔ một nghĩa | Một **nút trong mạng lưới**: nhiều âm đọc, bộ thủ, số nét, và các từ ghép chứa nó | Cấu trúc + cách nối + sắc thái + lỗi thường gặp |
| Học bằng cách | Lật thẻ, nhớ mặt chữ | Nhận mặt chữ **+** đọc âm **+** viết — ba việc tách rời | Đọc giải thích rồi làm bài tập |
| Thẻ lật có đủ không | Đủ | **Không** — thẻ chỉ là một mặt của nó | **Hoàn toàn không** |

Một thẻ ghi 「〜てもいい / được phép」 gần như vô dụng: người học không thiếu bản
dịch, họ thiếu *nối với thể gì, khác 〜てもかまわない ở đâu, khi nào không dùng được*.
Thứ trả lời được những câu đó là một trang lý thuyết, không phải mặt sau của thẻ.

##### Mô hình chốt — cố ý đơn giản

```
Course        (courseId, title, description, levelId, authorId → User,
               status DRAFT | PENDING | PUBLISHED | REJECTED,
               reviewedBy, reviewedAt, reviewNote, createdAt)
CourseLesson  (lessonId, courseId, orderNo, title,
               lessonType GRAMMAR | KANJI | VOCAB | READING | LISTENING,
               content   ← lý thuyết để đọc, một khối văn bản
               estimatedMinutes,
               deckId?   ← bộ thẻ ôn kèm, không bắt buộc
               examId?)  ← bài kiểm tra cuối bài, không bắt buộc
Enrollment    (userId, courseId) PK kép, startedAt
LessonDone    (userId, lessonId) PK kép, completedAt
```

Tiến độ = `số bài đã hoàn thành / tổng số bài`. Không có "đang học dở", không đo
thời gian đọc, không chia khối nội dung. Đọc xong, bấm **Hoàn thành**, phần trăm
nhích lên — đúng bằng thế.

**Ba quyết định đáng ghi lại:**

1. **Nội dung là MỘT khối văn bản, không phải nhiều khối ghép.** Bản trước có
   `LessonBlock` với sáu loại khối — kéo theo một trình soạn thảo khối, thứ tốn
   nhiều ngày nhất của cả §3.4. Một ô soạn thảo cho ra 90% giá trị với 10% công sức.

2. **`deckId` và `examId` để trống được.** Bài ngữ pháp có thể chẳng cần thẻ nào;
   bài từ vựng thì gắn bộ thẻ để ôn sau khi đọc. Bộ thẻ giữ đúng vai trò của nó —
   công cụ **ôn lại**, không phải thứ thay cho bài học.

3. **Kanji là một `lessonType` riêng.** Trang lý thuyết của nó hiện mặt chữ lớn,
   âm On/Kun, số nét, bộ thủ, mẹo nhớ và từ ghép — thứ một thẻ lật không chứa nổi.
   Dữ liệu đã có sẵn ở `KanjiItems` từ §3.4(b), chỉ thiếu chỗ để đọc.

##### Ai tạo, ai xuất bản — **người ra đề soạn, Admin duyệt**

```
Người ra đề          Admin              Thí sinh
   soạn  ──DRAFT──▶  
   gửi duyệt ─PENDING─▶ xem xét
                        ├─ duyệt ──PUBLISHED──▶ thấy và học được
                        └─ từ chối ─REJECTED──▶ (kèm lý do, soạn lại được)
```

Lý do chọn mô hình này thay vì hai phương án kia:

- **Không để Admin tự soạn hết**: thứ tự dạy ngữ pháp N5, chọn 800 từ nào trước —
  đó là kiến thức của người dạy. Admin không nhất thiết là người dạy tiếng Nhật, và
  bắt một người soạn hết N5→N1 là dựng sẵn một nút thắt cổ chai.
- **Không để người ra đề tự xuất bản**: lộ trình là thứ hàng nghìn người đi theo từ
  đầu tới cuối. Một bài xếp sai thứ tự không hỏng một buổi học, nó hỏng cả quá trình
  của người đi theo nó — mà thí sinh mới thì không có cách nào tự phát hiện. Đây
  đúng là chỗ đáng đặt một cửa kiểm.
- Khoá đã `PUBLISHED` mà sửa thì quay lại `PENDING`: nội dung đã có người đang học
  không được đổi sau lưng họ mà không ai xem lại.

##### API

| | Endpoint |
|---|---|
| Người ra đề | `GET /api/courses/mine` · `POST /api/courses` · `PUT /api/courses/{id}` · `DELETE /api/courses/{id}` · `POST /api/courses/{id}/submit` |
| | `POST/PUT/DELETE /api/courses/{id}/lessons[/{lessonId}]` |
| Admin | `GET /api/admin/courses?status=PENDING` · `POST /api/admin/courses/{id}/approve` · `POST /api/admin/courses/{id}/reject` |
| Thí sinh | `GET /api/courses` (chỉ PUBLISHED) · `GET /api/courses/{id}` · `POST /api/courses/{id}/enroll` · `GET /api/courses/{id}/lessons/{lessonId}` · `POST /api/courses/{id}/lessons/{lessonId}/complete` · `GET /api/me/courses` |

##### Frontend
`pages/teacher/CourseManager.jsx` (soạn khoá + soạn bài + gửi duyệt),
`pages/admin/Dashboard.jsx` (hàng đợi duyệt — **chức năng Admin thật đầu tiên**,
trước đó trang này chỉ là khung rỗng có TODO),
`pages/student/Courses.jsx` (danh sách khoá → danh sách bài → trang đọc, ba bước
của cùng một việc nên nằm chung một file).

##### Hai luật nhỏ nhưng đáng ghi lại, cả hai đều lộ ra khi chạy thử

**1. Sửa khoá đã xuất bản thì nó quay lại hàng đợi duyệt.** Nếu không, cửa kiểm
thành hình thức: gửi một khoá sơ sài cho qua, rồi thay sạch nội dung bên trong.

**2. Nhưng người ĐÃ ghi danh vẫn vào được khoá đang chờ duyệt lại.** Đây là lỗi
tôi phát hiện khi chạy thật: ban đầu chỉ xét `PUBLISHED`, nên tác giả sửa một lỗi
chính tả là mọi người đang học dở **mất quyền vào giữa chừng** — bài họ đang đọc
biến mất mà không ai giải thích gì. Cửa kiểm sinh ra để chặn nội dung xấu được
*phát hiện và adopt*, không phải để giật khoá khỏi tay người đã theo nó. Khoá vẫn
biến mất khỏi danh sách công khai với người chưa ghi danh.

##### Đã kiểm chứng bằng cách chạy thật
- Thí sinh: ghi danh → đọc 6 bài → phần trăm nhích **16 → 33 → 50 → 66 → 83 → 100%**;
  bấm lại bài cũ không đổi số (idempotent).
- Người ra đề: tạo khoá (DRAFT) → gửi duyệt khi chưa có bài → **409 "chưa có bài nào"**;
  thêm bài → gửi duyệt → PENDING → sửa khi đang PENDING → **409 "đang chờ duyệt"**.
- Admin: từ chối không kèm lý do → **409**; kèm lý do → REJECTED và tác giả đọc được
  lý do, sửa rồi gửi lại → duyệt → PUBLISHED → thí sinh thấy.
- Phân quyền: người ra đề gọi hàng đợi duyệt → **403**; thí sinh gọi duyệt khoá → **403**;
  thí sinh mở khoá PENDING chưa ghi danh → **404**.

**Thực tế: khoảng 1 ngày công** — ít hơn hẳn ước lượng 3 ngày, vì nội dung chỉ là
một ô soạn thảo thay vì một trình soạn theo khối.

#### (d) Theo dõi thói quen học — thứ giữ người học quay lại

Kết quả thi đo *năng lực*; phần này đo *nỗ lực*, và nó mới là thứ hiển thị hằng ngày.
```
StudyStreak (userId, currentStreakDays, longestStreakDays, lastStudyDate)
StudyLog    (logId, userId, activityType LESSON|REVIEW|PRACTICE|EXAM, refId,
             minutesSpent, itemsDone, occurredAt)
DailyGoal   (userId, targetMinutes, targetCards)
```
- Màn hình chính của thí sinh đổi từ "danh sách đề thi" sang **bảng học hôm nay**:
  chuỗi ngày liên tiếp · số thẻ đến hạn · chặng đang học · số câu sai chờ ôn.
- Đây cũng là nguồn dữ liệu cho gợi ý ở §3.3: người học 20 phút/ngày và người học
  2 tiếng/ngày cần lộ trình có mật độ khác nhau.
- **Ước lượng: 1,5 ngày.**

#### Tình trạng §3.4

| | Khối | Trạng thái |
|---|---|---|
| (a) | Sổ tay câu sai | ✅ xong |
| (b) | Thẻ ghi nhớ + SRS (từ vựng) | ✅ xong |
| (c) | Khoá học — ngữ pháp & chữ Hán, có duyệt | ✅ xong |
| (d) | Streak / nhật ký học / mục tiêu ngày | ⬜ chưa |

Ba khối đầu đã đủ để dự án có nghiệp vụ học tập thật, và quan trọng hơn là đủ
**đúng hình dạng**: từ vựng học bằng thẻ, ngữ pháp và chữ Hán học bằng bài đọc.
Bỏ (d) thì mất phần giữ chân người dùng nhưng không hỏng nghiệp vụ nào.

**Thực tế đã dùng: khoảng 5 ngày công** cho (a) + (b) + (c), so với ước lượng ban
đầu 11 ngày cho cả bốn khối.

---

## 4. Chuyên biệt hoá Tiếng Nhật

> *"tập trung trọng tâm vào học ngôn ngữ như tiếng nhật và có cái xử lý riêng"*

Hiện hệ thống hoàn toàn trung tính về môn học: `QuestionType` chỉ có
`MULTIPLE_CHOICE / ESSAY / MATCHING`, không có gì nói lên đây là hệ thống JLPT ngoài
mấy dòng seed `Subjects`. Đây là khoảng cách lớn nhất giữa đề bài và sản phẩm.

### 4.1 Cấu trúc kỹ năng JLPT — phải có
```java
enum JlptSkill { MOJI_GOI, BUNPOU, DOKKAI, CHOUKAI }   // 文字語彙・文法・読解・聴解
```
- Thêm `Questions.Skill`, `ExamQuestions.Skill` (snapshot theo).
- **Chấm điểm theo phần (điểm liệt):** JLPT không cộng tổng rồi so một ngưỡng — mỗi
  phần có điểm sàn riêng, trượt một phần là trượt cả bài dù tổng cao. Thêm
  `ExamSection { examId, skill, durationMinutes, maxScore, passScore, orderNo }` và
  `SubmissionSectionScore { submissionId, skill, score, passed }`.
  `finishSession()` phải trả kết quả **theo từng phần + kết luận đỗ/trượt**, không
  chỉ một con số `totalScore` như hiện tại.

### 4.2 Dạng câu hỏi riêng — thêm vào `QuestionType`
| Dạng | Mô tả | Xử lý riêng |
|---|---|---|
| `LISTENING` | 聴解, có audio | Giới hạn số lần phát (server đếm), khoá tua |
| `READING_PASSAGE` | 読解, nhiều câu chung một đoạn văn | Cần `QuestionGroup` (đoạn văn) — hiện chưa có |
| `KANJI_READING` | Chọn cách đọc của kanji | Hiển thị furigana có điều kiện |
| `FILL_IN_KANA` | Điền kana/kanji, tự chấm được | Chuẩn hoá chuỗi trước khi so (§4.4) |
| `SENTENCE_ORDER` | 並び替え — sắp xếp thứ tự từ | Chấm theo mảng thứ tự |

### 4.3 Nghe (聴解) — phần cần hạ tầng riêng
- `Question.mediaUrl`, `mediaDurationSeconds`, `maxPlayCount` (mặc định 1 — đúng đề thật).
- Đếm lượt phát **ở server**: `POST /exams/{id}/questions/{qid}/play` trả URL có chữ
  ký, hết lượt thì `409`. Đếm ở client thì F5 là reset.
- Audio ở S3/Drive, phát qua **signed URL TTL ngắn** — không để URL trần trong payload
  đề thi, nếu không thí sinh tải cả bộ audio về trước khi thi.
- Phần nghe **không cho quay lại câu trước** (forward-only) — đúng quy chế JLPT.

### 4.4 Chuẩn hoá văn bản tiếng Nhật — chỗ tự chấm hay sai
Trước khi so đáp án `FILL_IN_KANA` / tự luận ngắn:
- `Normalizer.normalize(s, Form.NFKC)` — gộp **半角/全角** (ｱ→ア, １→1, ＡＢ→AB).
  Không làm bước này thì thí sinh gõ bằng IME khác bàn phím là sai oan.
- Bỏ khoảng trắng thừa, kể cả `　` (khoảng trắng full-width).
- Bảng biến thể chấp nhận được cho mỗi đáp án (`AcceptedAnswer`, nhiều dòng):
  ví dụ 「わかる」/「分かる」/「解る」, khác biệt 送り仮名.
- Tuỳ chọn `ignoreKanjiKana`: chấp nhận trả lời bằng kana thuần khi câu hỏi không
  kiểm tra chữ Hán.

### 4.5 Frontend
- Font **Noto Sans JP** (thêm vào `index.css`, có fallback), `lang="ja"` trên vùng
  nội dung để trình duyệt chọn glyph đúng (chữ Hán Nhật ≠ Trung).
- `word-break: normal` + `line-break: strict` — cấm ngắt dòng sai chỗ ở tiếng Nhật.
- `components/question/FuriganaText.jsx` — render `<ruby>漢字<rt>かんじ</rt></ruby>`,
  có nút bật/tắt furigana (N5 bật, N2 tắt).
- `components/question/AudioPlayer.jsx` — không thanh tua, hiện số lượt phát còn lại.
- Ô nhập tiếng Nhật: bắt `compositionstart` / `compositionend` —
  **không autosave giữa lúc IME đang ghép chữ**, nếu không sẽ lưu chuỗi romaji dở dang.

**Ước lượng: 6–7 ngày.**

---

## 5. Luyện thi vs Phòng thi thử vs Thi thật — tách ba chế độ

> *"luyện thi (ABCD tự luận), phòng thi thử, làm xong thì chấm như nào (tự luận)"*

Hiện cả ba đi chung một luồng `startOrResume → saveAnswer → submit`, chỉ khác ở chỗ
đề có `classId` hay không. Phải tách rõ vì luật chơi khác nhau:

| | **Luyện tập** | **Phòng thi thử** | **Thi thật (phòng)** |
|---|---|---|---|
| Đồng hồ | Không / mềm, dừng được | Có, theo từng phần JLPT | Có, cứng |
| Lượt làm | Không giới hạn | Không giới hạn | `maxAttempts` |
| Phản hồi | **Ngay sau mỗi câu** + giải thích | Sau khi nộp | Theo `allowReview` |
| Quay lại câu trước | Có | Có (trừ phần 聴解) | Có |
| Ghi chú / bookmark | Hiện | Ẩn | Ẩn |
| Chống gian lận | Không | Nhẹ | Đầy đủ (log sự kiện) |
| Vào bảng xếp hạng | Không | Có | Có |

**Việc phải làm:**
- `Exam.mode: PRACTICE | MOCK | OFFICIAL` (thay cho cách suy ra từ `classId` hiện nay).
- `PracticeService` riêng: `POST /api/practice/{examId}/check` — chấm **một câu** ngay,
  trả `isCorrect` + `explanation`, **không** tạo `ExamSubmission` (luyện tập không cần
  phiên nặng, chỉ ghi `PracticeLog` để nuôi phần gợi ý ở §3.3). Đây cũng là cách gỡ
  tải lớn nhất cho vấn đề scale ở §6.
- Phòng thi thử dùng `ExamSection` (§4.1): mỗi phần một đồng hồ, hết phần thì khoá,
  không quay lại được. `ExamSubmission` thêm `currentSectionId`.

---

## 6. Autosave đáp án — scale & chỗ lưu

> *"nhấn lưu luôn đáp án → chưa scale khi nhiều request"*
> *"→ lưu session storage (mở window/tab mới → tính là session → đóng thì tự xóa) / localStorage (ko xóa đi → vẫn ở browser)"*

### Hiện trạng
`ExamRoom.jsx` gọi `PUT /exams/{id}/answers` **mỗi lần bấm đáp án**; tự luận debounce
800ms. Mỗi request là một transaction MySQL ghi `SubmissionDetails`.
Ước tính: 500 thí sinh × 40 câu / 60 phút ≈ **5–6 write/giây trung bình**, nhưng thực
tế dồn cục ở đầu và cuối giờ → **đỉnh 50–100 write/giây**, cộng heartbeat. MySQL đơn
node sẽ nghẽn ở phút cuối — đúng lúc không được phép hỏng.

### Kiến trúc chốt (3 tầng) — tầng 1 và 2 ✅ ĐÃ LÀM XONG

**Tầng 1 — client, `localStorage`** (`utils/examDraft.js`):
- Mỗi thao tác ghi ngay vào `localStorage['exam:draft:{submissionId}']`. Tức thời,
  0 request, sống qua F5, qua tab bị trình duyệt gỡ khỏi bộ nhớ, **và qua tắt hẳn
  trình duyệt**.
- **Đổi quyết định cũ: dùng `localStorage`, không dùng `sessionStorage`.** Bản trước
  chọn `sessionStorage` để không để lại bài trên máy dùng chung. Nhưng
  `sessionStorage` bị xoá khi đóng hẳn trình duyệt — đúng lúc cần nó nhất: thí sinh
  tắt máy khi còn vài câu chưa kịp gửi, mở lại trong giờ thì phải còn.
  Rủi ro máy dùng chung được xử lý bằng cách giữ **ít nhất có thể, trong thời gian
  ngắn nhất có thể**:
  - Nháp **chỉ chứa câu chưa được server xác nhận** — không phải toàn bộ bài.
  - Xoá khi: server xác nhận câu đó · nộp bài · phiên đóng (409) · quá hạn phiên ·
    **đăng xuất** (`authService.logout` bắn nốt nháp lên server rồi mới xoá).
- **Bản nháp không bao giờ là nguồn sự thật.** Vào lại phòng thì lấy đáp án từ
  server, nháp chỉ phủ lên những câu chưa gửi. Mỗi câu trong nháp mang `stagedAt` —
  thời điểm sửa **quy về giờ server** (lệch giờ đo từ `serverTime` của mỗi response).
  Server có `answeredAt` muộn hơn `stagedAt` quá 2 giây → thí sinh đã sửa câu đó ở
  máy khác sau đó → **giữ bản server, bỏ nháp** và báo cho thí sinh biết. Dung sai 2
  giây vì cột DATETIME làm tròn phần lẻ giây (đã thấy thật: `36.66` → `37`).
- Nháp mang `submissionId`, và lô gửi lên cũng mang nó: server trả 409 nếu không
  khớp lượt đang mở. Không có chốt này, nháp sót của **lượt 1** sẽ bị ghi vào **lượt
  2** cùng đề (server tìm phiên theo đề + thí sinh, không theo lượt).

**Tầng 2 — gửi theo lô** (`hooks/useAnswerSync.js`):
- Bỏ "bấm là gửi". Quy tắc gửi lô:

  | Khi nào | Vì sao |
  |---|---|
  | Chậm nhất **10 giây** sau thay đổi *đầu tiên* chưa gửi | Đếm từ thay đổi đầu, không phải debounce — gõ tự luận liên tục cũng không hoãn mãi |
  | Đủ **10 câu** chưa gửi | Không để lô phình to |
  | **Phút cuối** (≤ 60 giây): gửi sau 1 giây | Đáp án tới server sau deadline là không được tính |
  | Tab bị ẩn / trang đóng | Xem §7.1, §7.5 |
  | Có mạng lại · tab hiện lại mà còn câu chưa gửi | |
  | Bấm nộp | Không gửi lô riêng — nháp đi kèm luôn trong request nộp |

  Bỏ ý "flush khi chuyển câu" của bản trước: thí sinh làm một câu rồi chuyển câu,
  nên quy tắc đó thực chất quay về một request mỗi câu.
- Endpoint mới `PUT /api/student/exams/{id}/answers/batch` nhận
  `{ submissionId, answers[] }`, tối đa 200 câu, **cả lô một transaction**. Endpoint
  đơn lẻ giữ cho tương thích.
- **Kiểm hết cả lô rồi mới ghi.** Các method phiên thi khai báo
  `noRollbackFor = BusinessException` (để việc tự nộp khi hết giờ không bị hoàn tác),
  nên nếu kiểm-ghi xen kẽ thì câu thứ ba sai sẽ để lại hai câu đầu đã ghi — lô bị lưu
  một nửa. Đường `submit` có cùng lỗi và đã sửa luôn.
- Lỗi mạng / 5xx: thử lại lùi dần 2s → 4s → … → 30s, không giới hạn số lần — nháp
  chỉ bị xoá khi server xác nhận nên thử bao nhiêu lần cũng không mất gì. 4xx: gửi lẻ
  từng câu để tách câu hỏng ra, không để một câu chặn cả hàng đợi.
- Mỗi câu có `rev`: câu bị sửa tiếp **trong lúc lô đang bay** thì không bị xoá khỏi
  nháp khi lô đó được xác nhận, lô sau sẽ gửi bản mới.
- **Gửi lúc thoát bằng `fetch(..., { keepalive: true })`, không phải `sendBeacon`.**
  `sendBeacon` không gắn được header `Authorization` (API xác thực bằng Bearer), và
  front-end khác origin với back-end nên body JSON qua `sendBeacon` bị CORS chặn.
  `keepalive` có cùng đặc tính "trình duyệt không huỷ khi trang đóng" mà vẫn gửi được
  header. Giới hạn 64KB — lô lớn hơn thì nằm lại trong nháp, lần sau gửi thường.
- Kèm theo: `SecurityConfig` thêm `setMaxAge(3600)` cho CORS. Trước đó Spring không
  gửi `Access-Control-Max-Age`, nên request keepalive lúc đóng tab phải chờ thêm một
  vòng preflight OPTIONS đúng lúc trang không còn thời gian.
- Trạng thái trên thanh trên: *Đang gửi… · Đã lưu trên máy, chờ gửi n câu · n câu
  chưa gửi được · Đã lưu*. Ô câu hỏi **chỉ tô vàng khi gửi thất bại** — chờ tới lượt
  gửi là bình thường, đáp án đã an toàn trên máy.

**Tầng 3 — server, Redis là nơi ghi trước:**
- `saveAnswerBatch` ghi vào Redis hash `exam:answers:{submissionId}` (đã có sẵn
  `ExamRedisService`, thêm nhóm key mới) → trả 200 ngay, **không chạm MySQL**.
- Flush xuống `SubmissionDetails` khi: nộp bài · job nền mỗi 60s cho phiên đang mở ·
  Redis báo TTL sắp hết. MySQL chuyển từ "mỗi thao tác một write" sang "mỗi phiên vài
  write" — giảm 1–2 bậc độ lớn.
- **Redis chết vẫn phải thi được:** `ExamRedisService` đã có quy ước fallback sẵn —
  giữ nguyên tinh thần đó, Redis không dùng được thì ghi thẳng MySQL như hiện nay,
  chậm hơn nhưng không mất bài.

**Đo đạc — phải có số, không nói suông:**
- Kịch bản k6/Gatling: 500 VU vào cùng một phòng, làm 45 phút, dồn nộp trong 2 phút cuối.
- Ngưỡng chấp nhận: p95 `saveAnswer` < 300ms, p99 `submit` < 1s, **0 bài mất đáp án**.
- Ghi kết quả vào `docs/load-test-report.md` — bằng chứng trả lời trực tiếp câu
  "chưa scale khi nhiều request".

**Ước lượng: 4 ngày (2 BE, 1 FE, 1 đo tải).**

---

## 7. Thoát trình duyệt / đa tab / concurrency / security

> *"khi đang làm bài mà người dùng thoát ra trình duyệt sẽ xử lý nn"*
> *"chưa discuss kĩ về security/concurence trong màn luyện thi"*
> *"khi bật tab mới → tab cũ time sẽ bị freeze (saving, chạy ngầm tiết kiệm ram)"*

### 7.1 Thoát trình duyệt — quy tắc chốt

**Nguyên tắc: đồng hồ không bao giờ dừng vì thí sinh thoát ra.**
`ExpiresAt` chốt một lần lúc bắt đầu và không bao giờ được nới. Mất mạng, sập nguồn,
đóng máy — thời gian vẫn trôi, hệt như thi trên giấy mà đứng dậy ra khỏi phòng.

| Tình huống | Hệ thống làm gì |
|---|---|
| Đóng tab / F5 / tắt hẳn trình duyệt | `beforeunload` cảnh báo; `visibilitychange: hidden` + `pagehide` bắn nốt nháp bằng `fetch keepalive` ✅ |
| Tắt ngang (sập nguồn, kill tiến trình) | Không kịp bắn gì. Nháp vẫn nằm trong `localStorage` ✅ |
| Mở lại trong giờ, vào lại phòng | `POST /start` idempotent → phiên cũ + đáp án server, **phủ nháp chưa gửi lên trên** rồi đẩy ngay; báo "Đã khôi phục n câu" ✅ |
| Mở lại trong giờ nhưng **không** vào lại phòng | Trang chủ thí sinh (`ExamList`) tự đẩy nháp còn sót lên server (`pushLeftoverDrafts`) ✅ |
| Mất mạng | Nháp giữ đáp án, thử lại lùi dần; mất heartbeat > 90s → `AtRiskStatus = true`, người ra đề thấy đèn đỏ |
| Không quay lại tới hết giờ | Job `autoSubmitExpired` (30s/lần) tự nộp, `AutoSubmitted = true`, chấm trên những gì **server đã có** |
| Vào lại sau khi hết giờ | 409 → hiện thông báo của server; nháp của đề đó bị xoá (không còn lượt nào nhận nó) ✅ |

**Giới hạn phải nói thẳng khi bảo vệ:** thí sinh tắt ngang *và không bao giờ mở lại
trong giờ* thì những câu chưa kịp gửi mất — tối đa **10 giây** thao tác cuối (1 giây ở
phút cuối). Đó là cái giá của việc gửi theo lô thay vì từng câu, và là cái giá chấp
nhận được: đổi lại số request giảm cả chục lần. Đóng tab / tắt trình duyệt *bình
thường* thì không mất gì, vì trình duyệt luôn bắn `visibilitychange: hidden` trước.

**Bổ sung cần làm** (phần trên đã có, phần dưới chưa):
- `ExamSessionEvent { submissionId, type, occurredAt, meta }` với type
  `BLUR | FOCUS | OFFLINE | ONLINE | TAB_TAKEOVER | RESUME`, gửi theo lô cùng heartbeat.
  Người ra đề xem được "thí sinh này rời màn hình 6 lần, tổng 4 phút" — vừa chống gian
  lận nhẹ, vừa là bằng chứng khi có khiếu nại.
- Banner **"Bạn có 1 bài đang làm dở — còn 23:14"** trên `ExamList.jsx`, đọc từ
  `GET /api/student/active-session`. Hiện thí sinh thoát ra là mất dấu phiên dở.
- `beforeunload` chỉ bật ở chế độ thi thật; luyện tập thì thôi, không phiền người dùng.

### 7.2 Đa tab — cơ chế **lease**, và tab cũ bị đóng băng

Đây là gợi ý *"bật tab mới → tab cũ time sẽ bị freeze"*, làm đúng như sau:

1. `POST /start` trả về **`sessionToken`** (UUID, một lượt làm bài một token) và ghi
   `exam:lease:{submissionId} = sessionToken` trên Redis.
2. **Mọi** request save/heartbeat/submit gửi kèm header `X-Session-Token`.
   Không khớp lease hiện tại → `409 LEASE_TAKEN`.
3. Tab mới mở → `/start` cấp token mới, **cướp lease**. Tab cũ ở request kế tiếp nhận
   409 → **đóng băng ngay**: `timer.pause()`, dừng heartbeat, dừng debounce tự luận,
   khoá toàn bộ ô nhập, hiện overlay *"Bài thi đang mở ở tab khác"* + nút
   **"Tiếp tục ở tab này"** (bấm là cướp lease ngược lại).
4. Không đợi tới request kế tiếp mới biết: dùng **`BroadcastChannel('exam')`** — tab
   mới phát `takeover`, tab cũ đóng băng **tức thì**. Không có `BroadcastChannel` thì
   fallback qua sự kiện `storage` của `localStorage`.
5. Lợi ích đúng như phản hồi nêu: tab bị đóng băng không chạy interval, không giữ kết
   nối, **không tốn RAM/CPU chạy ngầm** — và quan trọng hơn, hai tab không còn ghi đè
   đáp án của nhau.

Lưu ý: freeze là **freeze đồng hồ hiển thị của tab đó**, không phải freeze phiên thi.
Server vẫn đếm giờ. Phải nói rõ điều này trên overlay để thí sinh không hiểu nhầm là
được tạm dừng bài.

### 7.5 Tab chạy nền — trình duyệt tiết kiệm tài nguyên ✅ ĐÃ LÀM XONG

Thí sinh chuyển sang tab khác, thu nhỏ cửa sổ, hay gập laptop thì trình duyệt làm
bốn việc với tab phòng thi, mỗi việc cần một cách xử lý:

| Trình duyệt làm gì | Ảnh hưởng | Xử lý |
|---|---|---|
| Tiết chế timer: tối đa 1 lần/giây khi ẩn; Chrome dồn còn **1 lần/phút** sau 5 phút ẩn | Hẹn gửi lô 10 giây có khi thành vài phút; heartbeat 20s thành 60s | Gửi lô **ngay lúc tab bị ẩn** (`visibilitychange: hidden`), không đợi hẹn. Heartbeat 60s vẫn dưới ngưỡng 90s nên không bị báo mất kết nối oan |
| Đóng băng tab (Page Lifecycle `freeze`) | JS dừng hẳn, không heartbeat | Nháp đã gửi lúc ẩn. Server đánh dấu `AtRisk` sau 90s — đúng sự thật, em không còn ở đó. Quay lại → heartbeat → tắt cờ |
| **Gỡ tab khỏi bộ nhớ** (Memory Saver, máy thiếu RAM) | Quay lại là trang tải lại từ đầu, mất sạch state React | `POST /start` vào lại phiên + nháp trong `localStorage` còn nguyên. Nhận ra bằng `document.wasDiscarded` và báo *"Trình duyệt đã tải lại trang này để tiết kiệm bộ nhớ…"* để thí sinh không hoảng |
| Máy ngủ (gập laptop) | `performance.now()` **đứng yên** lúc máy ngủ trên một số nền tảng → đồng hồ trong tab "còn" thừa đúng bằng thời gian ngủ | Tab hiện lại (`visibilitychange: visible`) hoặc khôi phục từ back/forward cache (`pageshow.persisted`) → **gọi heartbeat ngay**, chỉnh đồng hồ theo server, không đợi nhịp 20 giây |

Đồng hồ **không** dừng khi tab nằm nền — server vẫn đếm. Hết giờ trong lúc tab nằm
nền thì job của server tự nộp; quay lại tab là heartbeat nhận `autoSubmitted` và
đóng phòng.

Không chuyển heartbeat sang Web Worker (worker ít bị tiết chế hơn): 1 lần/phút đã
đủ dưới ngưỡng 90 giây, và worker không cứu được trường hợp tab bị đóng băng hay bị
gỡ — hai trường hợp duy nhất thật sự gây mất nhịp.

### 7.3 Concurrency — bảng rủi ro và chốt chặn

| Rủi ro | Chốt chặn |
|---|---|
| Double-click "Bắt đầu" → 2 phiên | Redis lock `exam:lock:start` (**đã có**) + UNIQUE `(ExamID, StudentID, AttemptNumber)` (**đã có**) |
| 2 tab ghi đè đáp án | Lease (§7.2) + `@Version` optimistic lock trên `ExamSubmission` |
| Nộp bài 2 lần cùng lúc | `submit` idempotent: đã `SUBMITTED/GRADED` thì trả kết quả cũ, không chấm lại. **Thêm Redis lock cho submit** (hiện chỉ start mới có) |
| Job tự nộp chạy trùng lúc thí sinh bấm nộp | `SELECT ... FOR UPDATE` trên phiên trước khi chốt |
| Nhiều instance backend cùng chạy job | Khoá phân tán **ShedLock** — hiện `@Scheduled` chạy trên **mọi** instance, deploy 2 node là job chạy đôi |
| Tranh ghế phòng thi | Redis `INCR` + `UNIQUE(RoomID, SeatNo)` (§2) |

### 7.4 Security — màn luyện thi

**Đã đúng, giữ nguyên:** đồng hồ chốt ở server; `ExamOptionView` không mang
`isCorrect`; đề thi lấy từ snapshot; đáp án gửi lên là `snapshotAnswerId` chứ không
phải nội dung, nên client không bịa được lựa chọn.

**Còn thiếu, phải làm:**
1. **Xáo câu và xáo đáp án theo từng lượt.** Lưu `shuffleSeed` trên `ExamSubmission`,
   xáo **ở server** bằng seed đó → thứ tự ổn định khi F5 nhưng khác nhau giữa các thí
   sinh. Hiện mọi người thấy đề giống hệt nhau theo đúng một thứ tự.
2. **Rate limit** `saveAnswer` / `heartbeat` / `play-audio` (Bucket4j + Redis),
   ví dụ 60 req/phút/phiên. Hiện không có gì chặn script bắn 1000 req/giây.
3. **Kiểm quyền theo phòng ở mọi endpoint phiên thi**, không chỉ lúc `/start` —
   người bị kick khỏi phòng phải mất quyền ngay.
4. **Không trả lời giải trước khi nộp.** Rà lại `ExamQuestionView` sau khi thêm dạng
   câu hỏi mới ở §4, tránh vô tình đính `explanation` vào payload đề.
5. **Signed URL cho audio/ảnh**, TTL 5 phút (§4.3).
6. **Audit log** cho hành vi nhạy cảm: sửa điểm, mở lại phiên đã nộp, xoá bài.
7. Refresh token nên chuyển sang **HttpOnly cookie** — token trong `localStorage` là
   mồi cho XSS. Đưa vào backlog nếu không kịp trong scope đồ án.

**Ước lượng: 5 ngày.**

---

## 8. Chấm bài tự luận — mảng thiếu hẳn

> *"làm xong thì chấm như nào (tự luận)"*

### Hiện trạng — lỗ hổng nghiệp vụ rõ nhất
`SubmissionServiceImpl.finishSession()` gặp câu `ESSAY` thì đặt
`isCorrect = false, scoreEarned = 0` rồi để phiên ở trạng thái `SUBMITTED`.
**Và không tồn tại bất kỳ endpoint nào để chấm nó.** Bài tự luận vào hệ thống là nằm
đó vĩnh viễn với 0 điểm. Trạng thái `SUBMITTED` cũng đang mang hai nghĩa ("đã nộp" và
"chờ chấm") nên không query ra được hàng đợi cần chấm.

### Quy trình chấm chốt

```
Nộp bài
  ├─ Câu ABCD  → chấm tự động ngay (đã có)
  └─ Câu tự luận
       ├─ Tự luận NGẮN (điền từ, chia động từ) → tự chấm bằng so khớp chuẩn hoá (§4.4)
       └─ Tự luận DÀI (作文)
            ├─ AI chấm nháp (Gemini) → điểm gợi ý + nhận xét theo rubric
            └─ Người ra đề duyệt / sửa → chốt điểm
                   ↓
            Tính lại tổng + điểm từng phần → GRADED → thông báo cho thí sinh
```

**Bước 1 — sửa trạng thái:** `SubmissionStatus` thêm `AWAITING_GRADING`.
`finishSession()` đặt trạng thái này khi còn câu tự luận chưa chấm; `SUBMITTED` chỉ
còn nghĩa "đã nộp, chấm xong phần tự động".

**Bước 2 — rubric:**
```
EssayRubric      (rubricId, questionId, criterion, maxScore, description, orderNo)
RubricScore      (detailId, rubricId, score, comment)
SubmissionDetail + gradedBy, gradedAt, teacherFeedback, aiSuggestedScore, aiFeedback
```
Rubric tiếng Nhật gợi ý: 内容 (nội dung) · 文法 (ngữ pháp) · 語彙 (từ vựng) ·
構成 (bố cục) · 表記 (chính tả / chữ viết).

**Bước 3 — API cho người ra đề:**
- `GET /api/teacher/grading/queue?examId=&skill=` — hàng đợi bài chờ chấm, sắp theo
  thời gian nộp; **ẩn tên thí sinh** (chấm mù, chống thiên vị).
- `GET /api/teacher/grading/{detailId}` — bài làm + rubric + gợi ý AI.
- `POST /api/teacher/grading/{detailId}` — `{ rubricScores[], feedback }` → tính lại
  `totalScore` và điểm từng phần → hết câu chờ thì chuyển `GRADED` + bắn `Notification`.
- `POST /api/teacher/grading/batch-ai` — chạy AI chấm nháp cho cả đề.

**Bước 4 — AI chấm nháp (dùng lại `AiQuestionService` đã có):**
- Prompt kèm: đề bài, rubric, bài làm, thang điểm; yêu cầu trả **JSON** đúng schema.
- **Bắt buộc:** kết quả AI là **gợi ý, không bao giờ là điểm cuối** — phải có người
  bấm duyệt. Ghi rõ trong tài liệu và hiện rõ trên UI ("Điểm AI đề xuất — chưa chốt").
  Đây là ranh giới phải nói rõ khi bảo vệ đồ án.
- Timeout + fallback: AI lỗi thì hàng đợi vẫn chấm tay được, không chặn.

**Bước 5 — Frontend:**
- `pages/teacher/GradingQueue.jsx` — danh sách chờ chấm, badge số lượng.
- `pages/teacher/GradingDetail.jsx` — bài làm bên trái, rubric + ô điểm bên phải, gợi ý
  AI thu gọn được, phím tắt `Ctrl+Enter` = lưu & sang bài kế.
- `pages/student/SubmissionReview.jsx` (đã có) — thêm khối nhận xét của người chấm và
  điểm từng tiêu chí.

**Ước lượng: 5–6 ngày.**

---

## 9. Lộ trình thực hiện

Thứ tự đặt theo **phụ thuộc kỹ thuật**, không theo mức độ dễ.

### Sprint 1 (tuần 1) — Nền móng
| # | Việc | Mục | Người |
|---|---|---|---|
| ~~1~~ | ~~Chốt thuật ngữ, sửa tên use case, sửa chữ trên UI~~ — ✅ xong | §1 | — |
| ~~2~~ | ~~Domain Room/RoomMember/RoomExam + migration + API phòng thi~~ — ✅ xong | §2 | — |
| ~~3~~ | ~~Bỏ Class khỏi toàn bộ service / controller / FE~~ — ✅ xong | §2 | — |
| 4 | `Exam.mode` + `ExamSection` + `JlptSkill` (đổi schema sớm, tránh migrate lại) | §4.1, §5 | B |

### Sprint 2 (tuần 2) — Phòng thi vững
| 5 | ~~Autosave theo lô + `fetch keepalive`~~ ✅ · Redis write-behind | §6 | C |
| 6 | Lease đa tab + BroadcastChannel + đóng băng tab cũ | §7.2 | C |
| 7 | `ExamSessionEvent` + banner "bài đang làm dở" | §7.1 | C |
| 8 | Xáo đề theo seed, rate limit, ShedLock, submit idempotent | §7.3–7.4 | A |
| 9 | Đo tải 500 VU, viết `docs/load-test-report.md` | §6 | C |

### Sprint 3 (tuần 3) — Nghiệp vụ tiếng Nhật
| 10 | Dạng câu hỏi LISTENING / READING_PASSAGE / FILL_IN_KANA | §4.2 | B |
| 11 | Audio: giới hạn lượt phát ở server, signed URL, player không tua | §4.3 | B |
| 12 | Chuẩn hoá NFKC + bảng biến thể đáp án | §4.4 | B |
| 13 | Furigana, font, IME-safe input | §4.5 | C |
| 14 | Phòng thi thử: đồng hồ theo phần, điểm liệt từng phần | §4.1, §5 | B |

### Sprint 4 (tuần 4) — Chấm tự luận & lộ trình
| 15 | `AWAITING_GRADING` + rubric + API chấm | §8 | A |
| 16 | UI hàng đợi chấm + màn chấm | §8 | A |
| 17 | AI chấm nháp (Gemini) | §8 | B |
| ~~18~~ | ~~**Sổ tay câu sai**~~ — ✅ xong | §3.4(a) | — |
| ~~19~~ | ~~**SRS từ vựng / kanji** + seed N5~~ — ✅ xong | §3.4(b) | — |
| 20 | StudyPath (3 loại chặng) + placement test + gợi ý theo tag yếu | §3.3 | C |
| 21 | Chế độ luyện tập chấm-ngay `PracticeService` | §5 | B |

### Sprint 5 (tuần 5) — Học tập đầy đủ *(chỉ làm nếu còn thời gian)*
| 22 | Bài học có nội dung + trình soạn bài | §3.4(c) | B |
| 23 | Streak / nhật ký học / mục tiêu ngày | §3.4(d) | C |
| 24 | Tìm kiếm (FULLTEXT ngram) + bookmark + ghi chú | §3.1–3.2 | C |

> **Cảnh báo về sức chứa.** §3.4 thêm 5,5–11 ngày công vào một kế hoạch vốn đã kín
> 4 tuần. Bốn tuần **không còn đủ**. Hai lựa chọn: kéo sang tuần thứ 5, hoặc giữ 4
> tuần và chỉ làm §3.4(a) + (b) rồi đẩy phần còn lại sang sau. Cần chốt với người
> hướng dẫn trước khi bắt đầu Sprint 1, vì lựa chọn này đổi cả phân công.

### Nếu thiếu thời gian — cắt theo thứ tự này
Cắt trước: Elasticsearch (§3.1 giai đoạn 2) → bài học có nội dung (§3.4c) → streak /
nhật ký học (§3.4d) → AI chấm nháp (§8 bước 4, chấm tay vẫn chạy được) →
`SENTENCE_ORDER` / `KANJI_READING` (§4.2, giữ 3 dạng chính) → HttpOnly cookie (§7.4.7).

**Không được cắt:** §2 (bỏ lớp), §3.4(a)+(b) (sổ tay câu sai + SRS — không có thì dự
án không có nghiệp vụ học tập), §6 (scale autosave), §7.2 (lease đa tab),
§8 bước 1–3 (chấm tự luận).

---

## 10. Kiểm chứng

**Test tự động cần thêm:**
- `RoomServiceTest` — 200 luồng tranh 50 ghế, assert đúng 50 người vào được.
- `SubmissionConcurrencyTest` — hai tab cùng save; submit gọi hai lần song song chỉ
  tạo một kết quả.
- `JapaneseNormalizerTest` — 半角/全角, 送り仮名, khoảng trắng full-width.
- `GradingServiceTest` — chấm rubric → tổng điểm và điểm từng phần đúng, kể cả trường
  hợp điểm liệt.
- `ExamTimerTest` (FE) — đổi giờ hệ thống không thay đổi thời gian còn lại.
- ✅ `Sm2SchedulerTest` (6 test) — giãn cách 1 → 6 → 15 ngày; `AGAIN` reset
  `repetitions` và tăng `lapses`; hệ số dễ có sàn 1,30; khoảng cách không bao giờ
  đứng yên; ngưỡng thuộc 21 ngày.
- ✅ `MistakeEntryTest` (4 test) — đúng một lần chưa phải là thuộc; câu đã thuộc mà
  sai lại thì quay về hàng đợi.

**Kiểm thử tay bắt buộc trước khi bảo vệ:**
1. Thi giữa chừng → tắt Wi-Fi 2 phút → bật lại → đáp án còn nguyên, đồng hồ đúng.
2. Thi → mở tab thứ hai → tab cũ đóng băng có overlay → "Tiếp tục ở tab này" chạy đúng.
3. Thi → tắt trình duyệt → đợi quá giờ → bài tự nộp, có điểm phần ABCD.
4. ✅ 10 người bấm vào phòng 3 chỗ cùng lúc → đúng 3 người vào (ghế 1,2,3), 7 người nhận 409 "Phòng đã đủ 3 người". Đã chạy thật.
5. Nộp bài có tự luận → hiện "chờ chấm" → người ra đề chấm → thí sinh nhận thông báo
   và thấy điểm cuối.
6. Đề nghe: phát 1 lần, không tua được, hết lượt thì không phát lại kể cả khi F5.
7. Học một chặng `LESSON` → sang chặng `DECK` ôn thẻ → chặng `EXAM` mở khoá đúng lúc.
8. ✅ Ghi danh bộ thẻ → mở hàng đợi ngay → thẻ hiện ra (đã kiểm, xem bẫy `DATETIME` ở §3.4b).
9. ✅ Nộp bài 0 điểm 5 câu → sổ tay có đúng 5 câu, tất cả ở trạng thái đến hạn.
10. ✅ Gửi `answerId` của câu khác vào `/mistakes/{id}/attempt` → 409, không được chấm đúng.
11. ✅ Ôn thẻ không nằm trong lịch học của mình → 404.

---

## 11. Bảng đối chiếu phản hồi → mục xử lý

| Phản hồi | Mục | Trạng thái hiện tại |
|---|---|---|
| Học ôn tập theo lộ trình chưa có | §3.3, §3.4 | ✅ Có khoá học (ngữ pháp/kanji), sổ tay câu sai, SRS từ vựng · lộ trình gợi ý tự động chưa |
| Trọng tâm tiếng Nhật, có xử lý riêng | §4 | Mới có seed môn / level |
| Thoát trình duyệt giữa lúc thi | §7.1 | ✅ Bắn nốt nháp bằng `fetch keepalive`, khôi phục nháp khi mở lại, đẩy nháp sót từ trang chủ · còn thiếu banner "bài đang dở" + event log |
| Chuyển tab → trình duyệt tiết kiệm tài nguyên | §7.5 | ✅ Gửi ngay khi ẩn tab, hỏi lại server khi hiện tab, xử lý tab bị gỡ khỏi bộ nhớ |
| Mỗi người 1 lộ trình; bỏ lớp; tìm kiếm / bookmark / ghi chú | §2, §3 | ✅ Bỏ lớp xong · lộ trình/tìm kiếm/bookmark chưa |
| Nhấn lưu luôn đáp án, chưa scale | §6 | ✅ Nháp localStorage + gửi theo lô (tầng 1–2) · Redis write-behind (tầng 3) và đo tải chưa |
| sessionStorage vs localStorage | §6 tầng 1 | ✅ Chọn `localStorage` (đổi so với bản trước — lý do ở §6) |
| Chưa bàn kỹ security / concurrency | §7.3, §7.4 | Có nền; thiếu lease, xáo đề, rate limit, ShedLock |
| Tab mới → tab cũ freeze | §7.2 | Chưa có |
| "tạo kì thi mới" sai tên | §1 | ✅ Đã đổi toàn bộ sang "bài thi" |
| Bỏ lớp → user group giới hạn người, ai nhanh thì vào | §2 | ✅ Đã bỏ Lớp, thay bằng Phòng thi có sức chứa |
| Luyện thi / phòng thi thử / chấm tự luận | §5, §8 | Chấm tự luận **thiếu hoàn toàn** |

---

## 12. Phản hồi đợt 2 — ✅ ĐÃ LÀM XONG

| Phản hồi | Đã làm |
|---|---|
| Tạo khoá học không ổn, nên là **lộ trình ôn tập** | §12.1 |
| Tạo bài thi cần **bộ lọc** bộ câu hỏi theo mức độ | §12.2 |
| Mở phòng rồi chỉ **bấm bắt đầu làm bài** hoặc **tới giờ hẹn** mới làm được | §12.3 |
| Hết giờ thì hiện **bảng xếp hạng** + kết quả thí sinh trong phòng | §12.4 |
| Đề tự do cũng hiện **kết quả xếp hạng** | §12.4 |
| Bỏ "Thao tác nhanh" / "Cần xử lý", dùng **thuật ngữ ôn luyện thi** | §12.5 |

### 12.1 Khoá học → lộ trình ôn tập

Khác biệt nằm ở **luật đi**, không ở cái tên:

- **Chặng mở tuần tự** — chặng sau khoá cho tới khi chặng trước đã qua. Server chặn cả
  mở chặng lẫn bấm qua chặng (409 kèm tên chặng phải qua trước).
- **Chặng có bài kiểm tra thì phải đạt mới qua.** Ngưỡng `MinScorePercent` đặt theo
  từng chặng, mặc định 60%; điểm tính là lượt tốt nhất. Lộ trình đo *độ sẵn sàng thi*,
  không đo việc đã bấm "đọc xong".
- **Bài kiểm tra của chặng phải là đề tự do (công khai)** — đề chỉ nằm trong phòng thì
  người theo lộ trình không vào làm được và kẹt vĩnh viễn ở chặng đó. Server từ chối gắn.
- Tác giả và Admin không bị khoá chặng (họ soạn / duyệt, không đi lộ trình).
- Bảng giữ nguyên tên `Courses` / `CourseLessons` (migration `v1.7.0/02` chỉ thêm cột
  `MinScorePercent`), đường dẫn API giữ nguyên — đổi tên là một đợt chuyển dữ liệu không
  mang lại gì cho người dùng. Mọi chữ người dùng nhìn thấy đã đổi: lộ trình / chặng /
  qua chặng / bắt đầu lộ trình.

### 12.2 Bộ lọc câu hỏi khi soạn đề

Trong form tạo đề / gắn câu hỏi: **trình độ (N5…N1) → bộ câu hỏi** (có tuỳ chọn "tất cả
bộ của trình độ" để soạn đề tổng hợp) **→ mức độ** (5 mức, mỗi mức ghi sẵn số câu còn
lại) **→ dạng câu → tìm nội dung**. "Chọn n câu đang lọc" chỉ tick những câu đang hiện.
Dòng tóm tắt nói cơ cấu đề đang soạn: *"Đã chọn 12 câu — 4 dễ · 6 trung bình · 2 khó"*.

### 12.3 Phòng thi: sảnh chờ → bắt đầu làm bài

Pha của phòng tính từ trạng thái + giờ (`RoomPhase`), không lưu cột — phòng hẹn giờ phải
tự vào thi và tự hết giờ mà không cần job canh từng giây.

| Pha | Vào phòng | Làm bài | Bảng xếp hạng (thí sinh) |
|---|---|---|---|
| Nháp | ✗ | ✗ | ✗ |
| **Sảnh chờ** (đã mở, chưa tới giờ) | ✓ | ✗ | ✗ |
| **Đang thi** (bấm bắt đầu *hoặc* tới giờ hẹn) | ✗ chốt danh sách | ✓ | ✗ |
| **Đã kết thúc** | ✗ | ✗ | ✓ |

- **Hết giờ = giờ bắt đầu + thời lượng đề dài nhất trong phòng.** Bài của thí sinh bị
  chặn ở mốc này: vào muộn 10 phút thì cũng chỉ còn phần giờ còn lại của cả phòng — tới
  giờ là thu bài cả phòng, bảng xếp hạng không phải chờ người vào muộn nhất.
- **Kết thúc sớm**: kéo hạn nộp mọi bài dở về "bây giờ" rồi chốt ngay qua đúng đường
  chấm của bài hết giờ — bảng xếp hạng có ngay, không chờ job 30 giây.
- Đang thi / đã kết thúc thì **không gắn / gỡ đề** được (gắn đề dài hơn là kéo dài giờ
  thi giữa chừng; gỡ đề sau khi kết thúc là xoá luôn bảng xếp hạng của đề đó).
- Người ra đề: nút chính của thẻ phòng đổi theo pha (*Mở sảnh chờ → Bắt đầu làm bài →
  Theo dõi bảng xếp hạng*), đồng hồ đếm tới giờ bắt đầu / giờ hết, ô **hẹn giờ bắt đầu**
  trong form phòng.
- Thí sinh: dải báo pha đầu trang phòng ("Sảnh chờ — chờ người ra đề bấm bắt đầu"),
  trang tự làm mới 10 giây một lần để nút làm bài mở ngay khi bắt đầu.
- **Đề tự do (công khai) không bị luật phòng chặn** — gắn đề công khai vào phòng thì nó
  vẫn làm được ngoài phòng như cũ. Muốn thi có giờ chung thì dùng đề không công khai.
- ⚠️ Thay đổi hành vi: các phòng đang OPEN từ trước giờ là **sảnh chờ** — thí sinh không
  làm bài được cho tới khi người ra đề bấm "Bắt đầu làm bài".

### 12.4 Bảng xếp hạng

Luật xếp hạng dùng chung: điểm cao hơn đứng trên; bằng điểm thì làm nhanh hơn đứng trên;
bằng cả hai thì cùng hạng (1, 2, 2, 4). Mỗi thí sinh một dòng — lượt tốt nhất.

- **Phòng thi** (`GET /api/rooms/{id}/leaderboard`): mỗi đề một bảng, gồm cả người chưa
  làm (cuối bảng, không hạng). Người ra đề xem **trực tiếp trong giờ thi** (tự làm mới 15
  giây); thí sinh chỉ xem **sau khi phòng hết giờ** — thấy bảng giữa giờ thì thành cuộc
  đua chứ không còn là bài thi. Chỉ tính lượt bắt đầu trong khung giờ của buổi thi.
- **Đề tự do** (`GET /api/student/exams/{id}/leaderboard`): tốp 20 + dòng của chính mình
  kể cả khi ở ngoài tốp. Hiện ngay trên màn hình vừa nộp bài ("Bạn đứng hạng 3/42").
- Thí sinh chỉ thấy `submissionId` (nút "Xem bài") của chính mình.
- Mục **"Bảng xếp hạng"** của thí sinh trước đây là "Phần này chưa được xây dựng", và khối
  "Top lớp N4" ở trang chủ là dữ liệu giả tên người bịa — cả hai đã thay bằng dữ liệu thật.

### 12.5 Thuật ngữ

- Bỏ khối **"Thao tác nhanh"** và **"Cần xử lý"** ở trang người ra đề; bỏ **"Cần xử lý"**
  ở tổng quan Admin (số liệu nào cần hành động thì thẻ số có đường dẫn thẳng tới danh sách).
- Admin: *Tổng quan ôn luyện* với ba nhóm *Hoạt động luyện đề · Học viên · Học liệu ôn
  thi*; *Duyệt lộ trình*. Người ra đề: *Đề luyện thi*, *Đang mở luyện*, *Đề chưa có câu hỏi*.

### 12.6 Lỗi có sẵn phát hiện trong lúc làm — đã sửa

| Lỗi | Hậu quả |
|---|---|
| `TeacherExamResponse.isPublic` ra JSON là `"public"` (Lombok + Jackson bỏ tiền tố "is") | Form sửa đề mở ra với ô "công khai" bị bỏ tick → **lưu lại là đề công khai thành đề riêng** |
| `ReviewCardResponse.isNew` ra JSON là `"new"` | Thẻ mới hiện "Đã ôn 0 lần" |
| Form sửa bài học không gửi `deckId` / `examId` | **Lưu một bài là mất bộ thẻ và bài kiểm tra đã gắn** |
| `ExamRow` kiểm `source === 'CLASS'` (đã đổi thành `ROOM` từ khi bỏ lớp) | Đề trong phòng bị ghi "Luyện tập tự do" |
| Class `sd-primary-btn` không tồn tại trong CSS | Nút "Vào phòng" không có kiểu |

### 12.7 Kiểm chứng

Chạy thật trên MySQL: phòng thi 30/30 kiểm (sảnh chờ chặn làm bài, bắt đầu, người mới bị
chặn, hạn nộp ≤ giờ kết thúc phòng, kết thúc sớm thu bài dở, quyền xem bảng, đồng hạng),
lộ trình 19/19 kiểm (khoá tuần tự, ngưỡng đạt, đề không công khai bị từ chối). Giao diện
chụp bằng Edge headless điều khiển qua DevTools Protocol.
