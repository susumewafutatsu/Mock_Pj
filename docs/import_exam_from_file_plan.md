# Kế hoạch: Import PDF/Word để tự sinh đề thi

## 1. Mục tiêu

Giáo viên upload 1 file PDF hoặc Word chứa nội dung câu hỏi (đề đã có sẵn dạng
văn bản), hệ thống tự trích xuất và sinh ra câu hỏi + đáp án, thay vì phải gõ
tay từng câu trong Ngân hàng câu hỏi (`QuestionBank.jsx`) rồi mới gắn vào đề.

## 2. Hiện trạng liên quan

- Câu hỏi được soạn trong ngân hàng (`Question`/`Answer`), đề thi chỉ **chọn và
  đóng băng bản sao** vào `ExamQuestion`/`ExamQuestionAnswer`
  (`domain/model/ExamQuestion.java`, `ExamQuestionAnswer.java`). Không có chỗ
  nào nhập nội dung câu hỏi trực tiếp khi tạo đề.
- Tạo câu hỏi thủ công: FE `pages/teacher/QuestionBank.jsx:209` gọi
  `questionService.createQuestion(bankId, payload)`.
- Gắn câu hỏi có sẵn vào đề: `controller/TeacherExamQuestionController.java:24`
  (`POST /api/teacher/exams/{examId}/questions`).
- Chưa có: tích hợp AI/LLM, upload PDF/Word, thư viện đọc PDF (PDFBox/iText)
  hay Word (Apache POI). Chỉ có upload audio (`MediaController.java`) làm mẫu
  tham khảo cho cách lưu file.

## 3. Luồng đề xuất

```
Upload file (PDF/Word)
   → Trích xuất text (PDFBox / Apache POI)
   → Gọi LLM sinh câu hỏi + đáp án dạng JSON có cấu trúc
   → Giáo viên xem trước, sửa/xoá từng câu (KHÔNG lưu thẳng)
   → Xác nhận → lưu vào ngân hàng câu hỏi (Question/Answer)
   → Giáo viên chọn câu vừa tạo để gắn vào đề (dùng lại luồng hiện có)
```

Điểm quan trọng: **luôn có bước review của giáo viên** trước khi câu hỏi được
lưu chính thức — AI trích sai (đặc biệt với đề JLPT có ký tự tiếng Nhật, bảng,
hình ảnh) là rủi ro cao nếu lưu thẳng không kiểm tra.

## 4. Việc cần làm — Backend

1. **Thêm dependency** vào `pom.xml`: Apache PDFBox (đọc PDF), Apache POI
   (đọc `.docx`).
2. **Endpoint upload**: `POST /api/teacher/question-imports` (multipart),
   tái dùng pattern lưu file tạm như `MediaController.java`. Giới hạn định
   dạng (`.pdf`, `.docx`) và dung lượng file.
3. **Service trích xuất text**: `ImportedDocumentParser` — đọc PDF/Word ra
   plain text, giữ cấu trúc đoạn/câu tối thiểu để LLM dễ nhận diện câu hỏi.
4. **Tích hợp LLM** (thành phần mới, project hiện chưa có):
   - Chọn nhà cung cấp (OpenAI, Anthropic, hoặc self-host), thêm config
     key/endpoint vào `application.yml` (dùng biến môi trường, không hardcode
     key).
   - Prompt yêu cầu model trả về JSON đúng schema: câu hỏi, loại
     (`questionType`), các đáp án, đáp án đúng, độ khó, giải thích — khớp với
     field của `Question`/`Answer` hiện tại để map trực tiếp.
   - Validate JSON trả về (schema check) trước khi đưa cho FE hiển thị; nếu
     model trả sai định dạng thì báo lỗi rõ ràng, không cố "đoán".
5. **Endpoint xem trước & xác nhận**:
   - `GET /api/teacher/question-imports/{id}/preview` — trả danh sách câu hỏi
     do AI sinh, ở trạng thái nháp (chưa vào DB chính).
   - `POST /api/teacher/question-imports/{id}/confirm` — nhận danh sách câu đã
     được giáo viên sửa, lưu vào bảng `Question`/`Answer` như tạo thủ công.
6. **Giới hạn & bảo mật**: giới hạn số trang/dung lượng file, timeout khi gọi
   LLM, xoá file tạm sau khi xử lý xong, kiểm tra quyền (chỉ giáo viên sở hữu
   ngân hàng câu hỏi mới được import vào đó).

## 5. Việc cần làm — Frontend

1. Trang/khu vực mới trong `QuestionBank.jsx` (hoặc trang riêng
   `ImportQuestions.jsx`): nút "Import từ file" mở dialog chọn PDF/Word.
2. Sau khi upload, hiển thị màn hình **preview dạng danh sách câu hỏi** — mỗi
   câu cho sửa nội dung, đáp án, đánh dấu đáp án đúng, hoặc xoá câu không cần.
   Tái dùng UI form câu hỏi đã có trong `QuestionBank.jsx` nếu có thể.
3. Trạng thái loading rõ ràng khi đang parse file / đang chờ LLM (có thể mất
   vài giây đến vài chục giây).
4. Nút "Lưu vào ngân hàng" gọi endpoint confirm ở bước 4.6 backend.

## 6. Rủi ro cần lưu ý

- **Chi phí & độ trễ gọi LLM**: mỗi lần import tốn tiền + thời gian, cần giới
  hạn tần suất (rate limit theo giáo viên) để tránh lạm dụng.
- **Tiếng Nhật trong câu hỏi JLPT**: cần đảm bảo font/encoding khi trích xuất
  từ PDF không bị vỡ ký tự (một số PDF nhúng font custom gây lỗi khi PDFBox
  đọc text).
- **Câu hỏi nghe (audio)**: không nằm trong phạm vi trích xuất từ PDF/Word,
  vẫn phải thêm thủ công như hiện tại.

## 7. Ngoài phạm vi (xử lý sau)

- **Hình ảnh**: câu hỏi có hình ảnh/bảng, và PDF dạng scan ảnh (không có text
  layer, cần OCR như Tesseract) — không làm ở giai đoạn này. Phạm vi hiện tại
  chỉ xử lý PDF/Word có text thuần, trích được bằng PDFBox/Apache POI.

## 8. Đề xuất triển khai theo giai đoạn

1. **Giai đoạn 1**: Upload + trích xuất text (PDF text-based, Word) + hiển thị
   text thô để giáo viên tự cắt câu hỏi thủ công (chưa cần AI) — kiểm chứng
   phần đọc file hoạt động ổn định.
2. **Giai đoạn 2**: Thêm LLM sinh câu hỏi có cấu trúc từ text, có preview/sửa
   trước khi lưu.
