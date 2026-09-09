# Scaffold Khung Dự Án: Nền tảng Ôn tập & Thi Trực Tuyến

## Tổng Quan

Dựa trên cấu trúc đã có, tôi sẽ **bổ sung và hoàn thiện các file còn thiếu** để nhóm 3 người có thể phân chia công việc rõ ràng theo module. Hiện tại project đã có skeleton cơ bản, cần bổ sung:

- **Backend**: Điền nội dung thực cho các file stub rỗng, thêm các service/dto/controller còn thiếu theo từng module
- **Frontend**: Tổ chức lại cấu trúc thư mục React (pages, components, services, hooks) theo role

---

## Cấu Trúc Hiện Tại (Đã Có)

### Backend (`Back_end/Mock_Pj/`)
```
src/main/java/com/example/demo/
├── config/         OpenApiConfig, RedisConfig, SecurityConfig (stub rỗng)
├── controller/     Admin, Auth, Student, Teacher (stub rỗng)
├── domain/
│   ├── enums/      AuthProvider, NotificationChannel, QuestionType, Role, SubmissionStatus
│   └── model/      15 entities (Answer, ClassEntity, Exam, Question, User, ...)
├── dto/
│   ├── request/    ExamCreateRequest, SubmitExamRequest (stub rỗng)
│   └── response/   AiAnalysisResponse, ExamResultResponse (stub rỗng)
├── repository/     6 repos (stub rỗng)
└── service/        ExamService (stub rỗng)
```

### Frontend (`Front_end/Mock_Project/`)
```
admin-ui/src/       App.jsx, main.jsx, index.css (UI admin đã có)
giaovien-so-diem-chung.jsx  (UI giáo viên đã có)
hocsinh-so-diem-chung.jsx   (UI học sinh đã có)
```

---

## Proposed Changes

### BACKEND — Phân chia theo Module cho 3 thành viên

---

#### 🔴 Module 1 — Auth & User Management (Thành viên A)

##### [MODIFY] [SecurityConfig.java](file:///d:/Mockporject/Back_end/Mock_Pj/src/main/java/com/example/demo/config/SecurityConfig.java)
- Cấu hình JWT filter chain, OAuth2 login với Google, CORS, endpoint permissions theo Role

##### [MODIFY] [AuthController.java](file:///d:/Mockporject/Back_end/Mock_Pj/src/main/java/com/example/demo/controller/AuthController.java)
- `POST /api/auth/login` — đăng nhập email/password
- `GET /api/auth/oauth2/callback` — callback Google SSO
- `POST /api/auth/refresh` — refresh JWT token
- `POST /api/auth/logout`

##### [NEW] `service/AuthService.java` — Logic xác thực, tạo JWT
##### [NEW] `service/UserService.java` — CRUD User, phân quyền
##### [NEW] `dto/request/LoginRequest.java`
##### [NEW] `dto/request/RegisterRequest.java`
##### [NEW] `dto/response/AuthResponse.java` — chứa accessToken, refreshToken, userInfo
##### [NEW] `dto/response/UserResponse.java`
##### [NEW] `security/JwtTokenProvider.java` — tạo/verify JWT
##### [NEW] `security/JwtAuthenticationFilter.java` — Spring Security filter
##### [NEW] `security/CustomOAuth2UserService.java` — xử lý Google OAuth2

---

#### 🟡 Module 2 — Question Bank & AI Generation (Thành viên B)

##### [MODIFY] [TeacherController.java](file:///d:/Mockporject/Back_end/Mock_Pj/src/main/java/com/example/demo/controller/TeacherController.java)
- `POST /api/teacher/questions` — tạo câu hỏi thủ công
- `GET /api/teacher/questions` — danh sách câu hỏi theo ngân hàng
- `PUT /api/teacher/questions/{id}`
- `DELETE /api/teacher/questions/{id}`
- `POST /api/teacher/ai/generate` — upload tài liệu, trigger AI sinh câu hỏi
- `POST /api/teacher/exams` — tạo đề thi
- `GET /api/teacher/exams/{examId}/results` — xem kết quả thi

##### [NEW] `service/QuestionService.java` — CRUD câu hỏi, lọc theo độ khó
##### [NEW] `service/AiQuestionService.java` — gọi Gemini API, parse JSON câu hỏi
##### [NEW] `service/DocumentParserService.java` — đọc PDF/Word (Apache POI)
##### [NEW] `dto/request/QuestionCreateRequest.java`
##### [NEW] `dto/request/AiGenerateRequest.java` — chứa subjectId, difficulty, count
##### [NEW] `dto/response/QuestionResponse.java`
##### [NEW] `dto/response/QuestionBankResponse.java`
##### [NEW] `repository/QuestionRepository.java`
##### [NEW] `repository/AnswerRepository.java`
##### [NEW] `repository/SubjectRepository.java`

---

#### 🟢 Module 3 — Exam, Submission & Analytics (Thành viên C)

##### [MODIFY] [StudentController.java](file:///d:/Mockporject/Back_end/Mock_Pj/src/main/java/com/example/demo/controller/StudentController.java)
- `GET /api/student/exams` — danh sách kỳ thi
- `GET /api/student/exams/{examId}/start` — bắt đầu thi (lấy đề)
- `POST /api/student/exams/{examId}/submit` — nộp bài
- `GET /api/student/results` — xem lịch sử kết quả

##### [MODIFY] [AdminController.java](file:///d:/Mockporject/Back_end/Mock_Pj/src/main/java/com/example/demo/controller/AdminController.java)
- `GET /api/admin/stats` — thống kê tổng quan
- `GET /api/admin/users` — quản lý users
- `POST /api/admin/classes` — tạo lớp học

##### [NEW] `service/ExamService.java` (điền nội dung) — tạo/lấy đề, adaptive logic
##### [NEW] `service/SubmissionService.java` — chấm điểm, lưu kết quả
##### [NEW] `service/AnalyticsService.java` — thống kê điểm, phát hiện học sinh yếu
##### [NEW] `dto/request/ExamCreateRequest.java` (điền nội dung)
##### [NEW] `dto/request/SubmitExamRequest.java` (điền nội dung)
##### [NEW] `dto/response/ExamResponse.java`
##### [NEW] `dto/response/ExamResultResponse.java` (điền nội dung)
##### [NEW] `dto/response/AnalyticsResponse.java`
##### [NEW] `repository/SubmissionDetailRepository.java`

---

#### ⚙️ Config & Cross-cutting

##### [NEW] `config/JwtConfig.java` — properties JWT
##### [MODIFY] [RedisConfig.java](file:///d:/Mockporject/Back_end/Mock_Pj/src/main/java/com/example/demo/config/RedisConfig.java) — cấu hình RedisTemplate, TTL
##### [MODIFY] [OpenApiConfig.java](file:///d:/Mockporject/Back_end/Mock_Pj/src/main/java/com/example/demo/config/OpenApiConfig.java) — Swagger với JWT Bearer auth
##### [NEW] `exception/GlobalExceptionHandler.java` — @ControllerAdvice xử lý lỗi tập trung
##### [NEW] `exception/ResourceNotFoundException.java`
##### [NEW] `exception/UnauthorizedException.java`
##### [MODIFY] [application.yml](file:///d:/Mockporject/Back_end/Mock_Pj/src/main/resources/application.yml) — thêm cấu hình DB, Redis, Gemini API key, OAuth2

---

### FRONTEND — Tổ Chức Lại Cấu Trúc React

Hiện tại frontend có 3 file JSX lớn (admin, giáo viên, học sinh). Tôi sẽ tổ chức thành cấu trúc thư mục chuẩn **feature-based** để nhóm 3 người dễ phân công:

#### Cấu Trúc Mới `Front_end/Mock_Project/admin-ui/src/`
```
src/
├── pages/
│   ├── auth/           LoginPage, CallbackPage
│   ├── admin/          Dashboard, UserManagement, Stats
│   ├── teacher/        QuestionBank, ExamManager, AiGenerate, ResultView
│   └── student/        ExamList, ExamRoom, ResultHistory
├── components/
│   ├── common/         Navbar, Sidebar, LoadingSpinner, ErrorBoundary
│   ├── question/       QuestionCard, QuestionForm, AnswerOption
│   ├── exam/           ExamTimer, ExamCard, ProgressBar
│   └── analytics/      ScoreChart, WeakStudentAlert, StatCard
├── services/           api.js (axios instance), auth.js, exam.js, question.js
├── hooks/              useAuth.js, useExamTimer.js, useAdaptive.js
├── context/            AuthContext.jsx
├── utils/              constants.js, helpers.js
└── App.jsx, main.jsx, index.css
```

##### [NEW] `src/services/api.js` — Axios instance với interceptor token
##### [NEW] `src/services/authService.js`
##### [NEW] `src/services/examService.js`
##### [NEW] `src/services/questionService.js`
##### [NEW] `src/context/AuthContext.jsx` — global auth state
##### [NEW] `src/hooks/useAuth.js`
##### [NEW] `src/hooks/useExamTimer.js`
##### [NEW] `src/pages/auth/LoginPage.jsx`
##### [NEW] `src/pages/admin/Dashboard.jsx` — wrap lại admin-so-diem-chung.jsx
##### [NEW] `src/pages/teacher/QuestionBank.jsx`
##### [NEW] `src/pages/teacher/AiGenerate.jsx`
##### [NEW] `src/pages/student/ExamRoom.jsx`
##### [NEW] `src/components/common/Sidebar.jsx`
##### [NEW] `src/components/common/ProtectedRoute.jsx`
##### [MODIFY] `src/App.jsx` — thêm React Router với routes theo role

---

## Verification Plan

### Automated Tests
- Build kiểm tra: `mvn compile -q` (Backend)
- Lint kiểm tra: `npm run lint` (Frontend, nếu có)

### Manual Verification
- Mỗi file mới có comment/Javadoc ghi rõ: **owner**, **module**, **todo items**
- Swagger UI (`/swagger-ui.html`) hiển thị đúng tất cả endpoint
- Không có import/dependency lỗi trong các stub file

---

## Phân Công Nhóm Gợi Ý

| Thành viên | Module Backend | Module Frontend |
|---|---|---|
| **A** | Auth & Security | `pages/auth/`, `context/AuthContext`, `hooks/useAuth` |
| **B** | Question Bank & AI | `pages/teacher/`, `components/question/` |
| **C** | Exam & Analytics | `pages/student/`, `pages/admin/`, `components/analytics/` |
