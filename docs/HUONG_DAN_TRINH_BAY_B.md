# Hướng dẫn trình bày khối B — Thi cử: làm bài & phòng thi

Tài liệu này để **anh tự học trước khi trình bày**, không phải để đọc trước lớp. Mỗi mục có: câu hỏi người hướng dẫn có thể hỏi → đường dẫn chính xác tới code → cách giải thích bằng lời, ngắn gọn, không đọc code trực tiếp trên màn hình.

**Cách dùng:** đọc một mục, mở đúng file:dòng ghi trong đó, đọc đoạn code thật (không cần đọc hết, khoanh đúng vùng), rồi diễn đạt lại bằng câu của chính anh. Đừng học thuộc câu trong tài liệu này — người hướng dẫn hỏi vặn sẽ lộ ngay.

---

## 0. Bản đồ tổng — cầm cái này khi trình bày

| Nghiệp vụ | File chính | Vai trò |
|---|---|---|
| Danh sách đề | `service/impl/ExamServiceImpl.java` | Gộp đề của phòng + đề tự do, tính trạng thái riêng từng thí sinh |
| Phiên làm bài | `service/impl/SubmissionServiceImpl.java` (1041 dòng — dài nhất dự án) | Vào thi, lưu đáp án, nộp bài, chấm |
| Job nền | `config/ExamSessionScheduler.java` | Tự nộp bài quá giờ, phát hiện rớt mạng, thông báo phòng hẹn giờ |
| Xáo đề | `service/PaperShuffler.java` | Xáo câu/đáp án theo từng lượt |
| Đồng hồ phần thi JLPT | `service/ExamSectionTiming.java` | Phần nào hết giờ thì khoá phần đó |
| Phòng thi | `service/impl/RoomServiceImpl.java` (980 dòng) | Toàn bộ nghiệp vụ phòng: vào phòng, pha, theo dõi, nhân bản |
| Pha phòng (tính toán thuần) | `domain/model/Room.java` | `phaseAt`, `acceptsMembersAt`, `lateJoinUntil` — không đụng DB |
| Autosave phía trình duyệt | `hooks/useAnswerSync.js`, `utils/examDraft.js` | Ghi nháp `localStorage` trước, gửi server theo lô |

**Một câu mở đầu dùng được cho mọi câu hỏi "vì sao":** *"Nguyên tắc xuyên suốt của khối B là server giữ toàn bộ sự thật — giờ giấc, điểm số, ai đang ở đâu. Trình duyệt chỉ hiển thị và nháp tạm, không tự quyết định gì."* Nói được câu này rồi trỏ vào code minh hoạ là qua được phần lớn câu hỏi.

---

## 1. Danh sách đề của thí sinh

**Người hướng dẫn có thể hỏi:** "Một thí sinh vào phòng nhưng đề đó cũng có ở luyện tập tự do thì hiển thị thế nào? Sao đề đang thi trong phòng lại không cho làm ngoài phòng?"

**Trỏ vào:** `ExamServiceImpl.getExamBoard()` — điểm bắt đầu, gộp hai nguồn:
- Đề của các phòng đang tham gia: `roomExamRepository`
- Đề luyện tập tự do: `examRepository.findPracticeExams(...)`

**Giải thích bằng lời:**
> "Một đề thi có thể đồng thời nằm trong phòng thi và là đề luyện tập tự do — hai thứ độc lập. Trạng thái em thấy (đang mở / đang làm dở / đã nộp / đã đóng) được tính riêng cho từng thí sinh bằng cách so `ExamSubmission` mới nhất của em đó với giờ hiện tại của server, không lưu sẵn trong bảng."

Không cần đào sâu mục này khi trình bày — đây là phần "nhẹ" nhất của B, chỉ cần trả lời được câu hỏi trên là đủ.

---

## 2. Phiên làm bài — phần nặng nhất, chuẩn bị kỹ nhất

### 2.1 "Đồng hồ đếm ngược lấy từ đâu? Thí sinh chỉnh giờ máy có gian lận được không?"

**Trỏ vào:** `SubmissionServiceImpl.java:144` và `:215-221`

```java
LocalDateTime now = LocalDateTime.now();
LocalDateTime expiresAt = computeExpiry(exam, now);
```
```java
private LocalDateTime computeExpiry(Exam exam, LocalDateTime startedAt) {
    LocalDateTime byDuration = startedAt.plusMinutes(exam.getDurationMinutes());
    if (exam.getEndTime() != null && exam.getEndTime().isBefore(byDuration)) {
        return exam.getEndTime();
    }
    return byDuration;
}
```

**Giải thích:**
> "`expiresAt` được tính **một lần duy nhất** ngay khi bắt đầu làm bài, bằng giờ của server, và ghi thẳng vào cột `ExpiresAt` trong bảng `ExamSubmissions`. Từ đó về sau, mọi request — lưu đáp án, nộp bài — đều so với cột này, không bao giờ tính lại theo giờ máy trình duyệt. Đồng hồ đếm ngược trên giao diện chỉ là trang trí; thứ quyết định là dòng dữ liệu này trong DB."

Nếu hỏi thêm "vậy trình duyệt lấy giờ ở đâu để hiển thị đúng": trả lời là mỗi response server đều kèm `serverTime`, phía trình duyệt tính độ lệch (`serverTime - Date.now()`) một lần rồi cộng vào để hiển thị, xem `expiresAt` trả về ở dòng 154, 262, 304, 415, 752 của cùng file — chỗ nào trả response về client đều có trường này.

### 2.2 "Hai tab cùng bấm 'Bắt đầu làm bài' một lúc thì sao? Có bị tạo hai phiên không?"

**Trỏ vào:** `SubmissionServiceImpl.java:99-134`

Đọc theo đúng thứ tự code, diễn giải từng bước:
1. **Dòng 100-104**: tra trước — có phiên đang dở thì vào lại luôn, không tạo mới, không cần khoá gì (nhánh nhanh).
2. **Dòng 116-127**: nếu chưa có, xin một khoá Redis theo `(examId, studentId)` trước khi tạo. Khoá bận (`BUSY`) thì từ chối luôn, bảo thử lại; Redis không dùng được (`UNAVAILABLE`) thì rơi về khoá dòng MySQL (`findByIdForUpdate` — dòng 122) để vẫn an toàn.
3. **Dòng 129-134**: sau khi có khoá, **tra lại lần nữa** — vì có thể tab kia đã tạo xong trong lúc mình chờ khoá.
4. **Dòng 149-158**: chỉ tới đây mới thật sự `save()` một `ExamSubmission` mới.

**Câu chốt:**
> "Đây là mẫu *check-lock-check* — kiểm tra, xin khoá, kiểm tra lại. Lớp phòng vệ thứ hai nằm ở tầng dữ liệu: bảng `ExamSubmissions` có ràng buộc **UNIQUE** trên `(ExamID, StudentID, AttemptNumber)`."

**Trỏ tiếp vào ràng buộc DB:** `db/changelog/v1.2.0/01-exam-attempts-and-review.yaml:107-110`
```yaml
- addUniqueConstraint:
    tableName: ExamSubmissions
    columnNames: ExamID, StudentID, AttemptNumber
    constraintName: uq_submission_exam_student_attempt
```
> "Dù tầng service có sơ sểnh thế nào, MySQL vẫn chặn được hai dòng trùng khoá này — đây là lưới an toàn cuối cùng, không phụ thuộc vào code Java viết đúng hay sai."

### 2.3 "Lưu đáp án kiểu gì mà không làm sập server khi cả trăm người cùng gõ?"

Đây là chỗ **hai tầng** — nói rõ ranh giới trình duyệt / server.

**Tầng trình duyệt — `hooks/useAnswerSync.js`:**
- Dòng 205-219 (`stage`): thí sinh gõ xong một câu → ghi ngay vào `localStorage` (qua `utils/examDraft.js`), **không gọi mạng ngay**.
- Dòng 76-82 (`schedule`): hẹn giờ gửi lô — 10 giây một lần bình thường, rút xuống 1 giây khi `urgent` (phút cuối).
- Dòng 141-169 (`flush`): tới giờ hẹn, hoặc gom đủ 10 câu (`FLUSH_BATCH_SIZE`, dòng 21 và 212), mới gửi **một lô** lên server.
- Dòng 172-202 (`flushOnExit`): tab bị ẩn hoặc đóng thì gửi ngay bằng `fetch keepalive` — request này sống sót được dù tab đã đóng.

**Giải thích ngắn:**
> "Không phải gõ một chữ là gọi API một lần. Trình duyệt gom nhiều câu trả lời thành một lô rồi mới gửi, và tự giãn thời gian chờ nếu server đang lỗi (dòng 84-88, giãn theo cấp số nhân, tối đa 30 giây). Vì sao dùng `localStorage` chứ không phải `sessionStorage`: nếu trình duyệt bị tắt đột ngột hay crash, `sessionStorage` mất sạch, còn `localStorage` vẫn giữ được nháp để khôi phục khi mở lại."

**Tầng server — `SubmissionServiceImpl.saveAnswer()` dòng 248-262:** mỗi lần lưu chỉ là một `upsert` đơn giản, không tính toán nặng; phần nặng (chấm điểm) chỉ chạy một lần lúc nộp bài.

### 2.4 "Làm sao biết thí sinh bị rớt mạng giữa chừng?"

**Trỏ vào:**
- `SubmissionServiceImpl.java:394` (`heartbeat`) — client gọi định kỳ, server chỉ cập nhật `LastActiveAt`, **tuyệt đối không nới `ExpiresAt`** (đọc đúng comment dòng 406).
- `SubmissionServiceImpl.java:476` (`flagDisconnectedSessions`) — job quét các phiên im lặng quá lâu, bật cờ `AtRiskStatus`.
- `config/ExamSessionScheduler.java:54-58` — job này chạy định kỳ qua `@Scheduled`.

**Giải thích:**
> "Heartbeat không phải để tính giờ — nó chỉ là 'còn thở'. Server có một job nền quét toàn bộ phiên đang thi, phiên nào quá X giây không gửi heartbeat thì bị đánh dấu `AtRisk`. Người ra đề nhìn thấy cờ này ngay trên màn Theo dõi của phòng thi."

### 2.5 "Ai lo việc tự động nộp bài khi thí sinh không nộp tay?"

**Trỏ vào:**
- `config/ExamSessionScheduler.java:42-49` (`autoSubmitExpired`) — chạy định kỳ (mặc định 30 giây, cấu hình ở `application.yml`).
- `SubmissionServiceImpl.java:460-463` (`autoSubmitExpiredSessions`) — truy vấn `findByStatusAndExpiresAtLessThanEqual`, tức là lấy đúng những phiên đã quá `ExpiresAt` mà vẫn `IN_PROGRESS`.

**Một chi tiết hay để kể:** *"Job này không chỉ chạy định kỳ — nó còn được gọi ngay tại chỗ khi thí sinh quay lại một phiên đã hết giờ (`resumeExisting`, dòng 201-207): phát hiện hết giờ là chốt bài ngay lập tức, không đợi tới lượt job sau."*

### 2.6 "SchedulerLock để làm gì, sao không dùng thư viện có sẵn?"

**Trỏ vào:** `config/SchedulerLock.java`, bảng `SchedulerLocks` (migration `v1.9.0/01-wave4.yaml`).

**Giải thích:**
> "Nếu sau này chạy nhiều máy chủ backend cùng lúc, mỗi máy đều có `@Scheduled` riêng — không khoá lại thì job tự nộp bài chạy trùng ở hai máy, có thể race nhau. Bảng `SchedulerLocks` đóng vai trò một khoá phân tán đơn giản: máy nào chiếm được dòng khoá (theo tên job) mới được chạy lượt đó. Không dùng ShedLock từ ngoài vì chỉ cần một bảng, không cần thêm thư viện."

### 2.7 "Đề JLPT chia phần, hết giờ một phần thì sao?"

**Trỏ vào:** `service/ExamSectionTiming.java:41` (`isOpen`) và `:62` (`closedReason`); nơi gọi nó: `SubmissionServiceImpl.java:634-638` (bên trong `recordAudioPlay`, và một chỗ tương tự trong `saveAnswer`/`upsertAnswer`).

**Giải thích:**
> "Lịch của từng phần thi không lưu sẵn trạng thái 'đang mở/đã đóng' — nó được **tính lại mỗi lần** từ `StartedAt` của phiên cộng với thời lượng từng phần, so với giờ hiện tại. Cách này tránh việc phải có một job riêng canh từng giây cho từng phần thi của từng thí sinh — điều đó không scale được khi có hàng trăm người thi cùng lúc."

### 2.8 "Nghe được bao nhiêu lần? Ai đếm?"

**Trỏ vào:** `SubmissionServiceImpl.java:619-663` (`recordAudioPlay`), route ở `StudentController.java:106`.

Đọc kỹ dòng 640-652: `resolveMaxAudioPlays()` lấy giới hạn từ câu hỏi (mặc định 1, giống JLPT thật), so với `detail.getAudioPlays()` đã lưu trong `SubmissionDetail`, vượt quá thì `BusinessException`.

**Giải thích:**
> "Số lượt nghe đếm ở server, lưu vào cột `AudioPlays` của bảng chi tiết bài làm — không đếm ở trình duyệt. Vì vậy F5 lại trang, hay mở tab mới, cũng không nghe lại được: số đếm nằm trong DB, không nằm trong bộ nhớ tạm của trình duyệt."

### 2.9 "Sao hai bài của hai thí sinh làm cùng đề mà thứ tự câu khác nhau? Có chép bài được không?"

**Trỏ vào:** `service/PaperShuffler.java:23-37`

```java
public static List<ExamQuestionView> shuffle(List<ExamQuestionView> questions, ..., long seed)
...
java.util.Collections.shuffle(copy, new Random(seed * 1_000_003L ...));
```

**Giải thích:**
> "Hạt giống xáo trộn (`seed`) chính là `submissionId` của lượt làm bài đó. Vì vậy thứ tự câu của một người **cố định trong suốt phiên thi đó** — F5 bao nhiêu lần cũng ra đúng thứ tự cũ, nhưng hai người khác nhau thì `submissionId` khác nhau nên thứ tự khác nhau. Nhìn bài người bên cạnh không dùng lại đáp án theo vị trí được."

Nếu hỏi thêm "câu trong cùng một bài đọc có bị xáo lẫn ra ngoài không" — trả lời: không, các câu cùng một `passageId` được giữ liền khối, chỉ xáo vị trí của cả khối đó trong đề, không xáo rời từng câu ra khỏi bài đọc của nó (đọc thêm phần dưới dòng 37 nếu người hướng dẫn hỏi sâu).

---

## 3. Kết quả

**Trỏ vào:** `SubmissionServiceImpl` phần build `ExamResultResponse` (quanh dòng 426 trở đi, hàm `submit`), và frontend `pages/student/ResultHistory.jsx`, `SubmissionReview.jsx`.

**Ranh giới cần nói rõ khi bị hỏi "điểm JLPT quy đổi tính ở đâu":**
> "Phép **quy đổi điểm sang thang JLPT 0–180** nằm ở `JlptScoringService` — đây là phần chung của cả dự án (thuộc khối A vì gắn với cấu trúc đề JLPT), khối B chỉ gọi tới nó sau khi có điểm thô từng câu, rồi hiển thị kết quả ra cho thí sinh qua `ExamResultResponse` và component `JlptScoreCard.jsx`."

Trả lời thẳng ranh giới này thay vì nhận vơ — người hướng dẫn hỏi sâu vào công thức quy đổi thì nói đó là phần của bạn A, tránh bị hỏi dồn vào chỗ mình không nắm.

---

## 4. Phòng thi

### 4.1 "Một phòng thi 'đang ở đâu' — sảnh chờ hay đang thi — lưu ở cột nào?"

**Đây là câu quan trọng nhất của phần phòng thi.** Trỏ vào `Room.java:154-168` (`phaseAt`):

```java
public RoomPhase phaseAt(LocalDateTime now, Integer examMinutes) {
    if (status == RoomStatus.DRAFT) return RoomPhase.DRAFT;
    if (status == RoomStatus.CLOSED) return RoomPhase.ENDED;
    LocalDateTime end = endAt(examMinutes);
    if (end != null && !now.isBefore(end)) return RoomPhase.ENDED;
    boolean started = status == RoomStatus.RUNNING
            || (startTime != null && !now.isBefore(startTime));
    return started ? RoomPhase.IN_PROGRESS : RoomPhase.WAITING;
}
```

**Giải thích:**
> "**Không có cột nào lưu 'pha' cả.** `Rooms` chỉ có cột `Status` (DRAFT/OPEN/RUNNING/CLOSED — người ra đề chủ động đổi) và `StartTime`. Pha hiển thị (`RoomPhase`: Nháp/Sảnh chờ/Đang thi/Kết thúc) được **tính lại mỗi lần gọi**, bằng cách so `now` với `StartTime` và giờ kết thúc suy ra từ thời lượng đề. Nhờ vậy một phòng hẹn giờ trước sẽ *tự* chuyển từ Sảnh chờ sang Đang thi đúng giờ, không cần job nào canh."

Đây là hàm **thuần** (pure function) — không đụng DB, không có `@Transactional` — nên demo được ngay bằng cách gọi tay với vài giá trị `now` khác nhau nếu bị hỏi vặn.

### 4.2 "Hai thí sinh cùng bấm vào phòng lúc phòng còn đúng 1 chỗ — ai vào?"

**Trỏ vào:** `RoomServiceImpl.java:719-761` (`joinRoom`)

Đọc theo thứ tự:
1. **Dòng 720**: `roomRepository.findByIdForUpdate(...)` — khoá dòng phòng ngay từ đầu, request thứ hai phải **đợi** request thứ nhất xong mới được đọc.
2. **Dòng 741**: `findMaxSeatForUpdate` — đọc ghế lớn nhất **cũng có khoá** (xem `RoomMemberRepository`, dùng `SELECT ... FOR UPDATE` thẳng bằng native query).
3. **Dòng 742-744**: so sức chứa, hết chỗ thì báo lỗi ngay trong lúc đang giữ khoá.
4. **Dòng 754-759**: nếu vẫn còn kẽ hở (hiếm), tầng dữ liệu có ràng buộc UNIQUE trên ghế — vi phạm thì bắt `DataIntegrityViolationException` và trả lỗi "vừa hết chỗ" thay vì để lỗi 500 vô nghĩa văng ra.

**Câu chốt:**
> "'Ai nhanh thì vào' được đảm bảo bằng khoá dòng ở MySQL, không phải bằng cách kiểm tra rồi hy vọng không ai chen ngang — kiểu kiểm-tra-rồi-mới-làm (check-then-act) mà không khoá là kẽ hở race condition kinh điển. Ở đây khoá trước, đọc số ghế sau, nên hai request không bao giờ cùng nhìn thấy 'còn 1 ghế' rồi cùng lấy ghế đó."

### 4.3 "Cho vào muộn thì tính giờ nộp bài thế nào?"

**Trỏ vào:** `Room.java:124-132` (`lateJoinUntil`) và `:141-151` (`acceptsMembersAt`), nơi gọi: `RoomServiceImpl.java:765-777` (`requireAcceptingMembers`).

**Giải thích:**
> "`lateJoinUntil` = giờ bắt đầu + số phút cho vào muộn (người ra đề cấu hình khi tạo phòng), nhưng không bao giờ vượt quá giờ kết thúc phòng. Vào muộn không được bù giờ — hạn nộp bài của người vào muộn vẫn là giờ kết thúc chung của cả phòng, xem lại mục 2.1: `expiresAt` của phiên thi bị chặn trần bởi `gate.runningRoomEnd()` (dòng 146-147 trong `SubmissionServiceImpl`)."

### 4.4 "Người ra đề theo dõi phòng thi trực tiếp thế nào — có polling liên tục không?"

**Trỏ vào:** `RoomServiceImpl.java:408` (`monitor`) — đọc tổng thể cấu trúc trả về: đếm số người `NOT_STARTED / IN_PROGRESS / SUBMITTED`, cờ `AtRisk` lấy thẳng từ `ExamSubmission.AtRiskStatus` (chính là cờ ở mục 2.4).

**Giải thích:**
> "Đây không phải kênh đẩy dữ liệu thời gian thực (không dùng WebSocket) — giao diện người ra đề gọi lại API này định kỳ vài giây một lần. Đơn giản hơn, và với quy mô một phòng thi vài chục người thì đủ mượt. Điểm hay là API này **dùng lại** đúng dữ liệu đã có (`AtRiskStatus`, `Status` của `ExamSubmission`) — không phải tính riêng gì thêm cho màn theo dõi."

### 4.5 "Mời một thí sinh ra khỏi phòng khi đang thi thì bài của họ ra sao?"

**Trỏ vào:** `RoomServiceImpl.java` quanh dòng 533 (điều kiện `phaseAt(...) == RoomPhase.IN_PROGRESS`) trong hàm mời ra — đọc để thấy nó gọi thẳng sang cơ chế nộp bài (tương tự autoSubmit ở mục 2.5) thay vì chỉ đổi trạng thái thành viên.

**Giải thích:**
> "Mời ra không chỉ là xoá tên khỏi danh sách — nếu người đó đang thi dở, bài của họ bị **thu ngay lập tức** bằng đúng cơ chế nộp bài dùng ở nơi khác trong hệ thống, không phải viết lại logic chấm riêng cho trường hợp này."

### 4.6 "Nhân bản phòng để làm gì, nó copy những gì?"

**Trỏ vào:** `RoomServiceImpl.java:321` (`duplicateRoom`).

**Giải thích ngắn:**
> "Một lớp dạy định kỳ (vd 'lớp N4 tối thứ 3-5') không phải mở phòng mới, mời lại từng người mỗi buổi. Nhân bản giữ nguyên đề, cách vào phòng, sức chứa; tuỳ chọn giữ luôn danh sách thí sinh cũ. Phòng mới sinh mã mới, về trạng thái Nháp, và không dùng chung dữ liệu bài làm với phòng gốc — hai buổi thi độc lập nhau."

### 4.7 "Thông báo 'phòng sắp bắt đầu' gửi lúc nào, ai gửi?"

**Trỏ vào:** `RoomServiceImpl.java:668` (`notifyScheduledRooms`), job gọi nó: `ExamSessionScheduler.java:65-69`.

**Giải thích:**
> "Job này quét các phòng có hẹn giờ, so `StartTime` với giờ hiện tại để quyết định gửi thông báo 'sắp bắt đầu' (trước một khoảng cấu hình) hay 'đã bắt đầu' — dùng đúng `phaseAt` ở mục 4.1 để biết phòng đang ở pha nào, tránh gửi trùng bằng cờ đã gửi lưu trên `Room`."

---

## 5. Khi bị hỏi những câu không có sẵn câu trả lời

Người hướng dẫn thường hỏi kiểu "nếu X xảy ra thì sao" ngoài kịch bản. Cách xử lý:

1. **Đừng đoán bừa.** Nói "để em mở code kiểm tra" rồi thật sự mở — không ai trách vì tra code, chỉ bị trừ điểm vì bịa.
2. **Tìm bằng triệu chứng, không tìm bằng tên hàm.** Ví dụ hỏi "nộp bài hai lần liên tiếp có sao không" — vào `SubmissionServiceImpl`, tìm chữ `submit`, đọc điều kiện đầu hàm (thường có `if (!session.isInProgress())` chặn ngay từ đầu).
3. **Trỏ đúng lớp trách nhiệm.** Câu hỏi về giao diện → tìm trong `admin-ui/src/pages`; câu hỏi về luật nghiệp vụ → `service/impl`; câu hỏi về ràng buộc dữ liệu → `db/changelog`.
4. **Nếu thật sự không biết** — nói thẳng "chỗ này em chưa kiểm chứng, để em xem lại sau buổi" còn hơn suy diễn sai trước lớp.

## 6. Tự kiểm tra trước khi trình bày

Tự hỏi mình, không nhìn tài liệu, chỉ mở code:
- [ ] `ExpiresAt` được ghi vào lúc nào, ai tính, có tính lại không?
- [ ] Hai tab cùng bấm bắt đầu thi — hai lớp phòng vệ là gì?
- [ ] `localStorage` dùng để làm gì trong lúc làm bài, khác `sessionStorage` ở điểm nào?
- [ ] `AtRiskStatus` được bật bởi ai, tắt bởi ai?
- [ ] Phòng thi lưu "đang ở pha nào" vào cột nào? (bẫy: câu trả lời là *không có cột nào*)
- [ ] Hai người tranh ghế cuối trong phòng — khoá gì chặn?
- [ ] Vào muộn có được cộng thêm giờ làm bài không?

Trả lời trôi chảy cả 7 câu này, kèm chỉ đúng dòng code, là đủ tự tin cho phần B.
