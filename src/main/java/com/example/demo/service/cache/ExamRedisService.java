package com.example.demo.service.cache;

import com.example.demo.dto.response.ExamQuestionView;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Toàn bộ chỗ chạm vào Redis của chức năng kì thi nằm ở đây.
 *
 * Gom về một lớp vì hai lý do: tên key chỉ được định nghĩa một chỗ (đổi tiền tố
 * là đổi toàn hệ thống), và quy tắc "Redis lỗi thì đi đường MySQL" chỉ cần viết
 * một lần chứ không rải try/catch khắp service.
 *
 * Quy ước trả về khi Redis không dùng được — quan trọng, vì đây chính là cách
 * hệ thống vẫn thi được lúc Redis chết:
 *   - Cache đọc  -> {@link Optional#empty()}, phía gọi tự dựng lại từ DB.
 *   - Khoá       -> {@link LockState#UNAVAILABLE}, phía gọi quay về khoá dòng DB.
 *   - Presence   -> {@link Optional#empty()}, phía gọi quay lại so LastActiveAt.
 *   - Nhịp sống  -> báo "hãy ghi thẳng xuống DB", đúng như hồi chưa có Redis.
 *
 * Các nhóm key đang dùng:
 *   exam:paper:{examId}                  cache đề thi đã snapshot (TTL ngắn)
 *   exam:lock:start:{examId}:{studentId} khoá lúc tạo phiên (TTL vài giây)
 *   exam:alive:{submissionId}            presence heartbeat (TTL = ngưỡng im lặng)
 *   exam:dbflush:{submissionId}          van tiết lưu ghi LastActiveAt xuống DB
 */
@Service
@RequiredArgsConstructor
public class ExamRedisService {

    private static final Logger log = LoggerFactory.getLogger(ExamRedisService.class);

    private static final String PAPER_KEY = "exam:paper:%d";
    private static final String START_LOCK_KEY = "exam:lock:start:%d:%s";
    private static final String ALIVE_KEY = "exam:alive:%d";
    private static final String DB_FLUSH_KEY = "exam:dbflush:%d";

    private final RedisTemplate<String, Object> examRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Đề thi được cache bao lâu. Ngắn thôi: giáo viên sửa đề là cache bị xoá
     * ngay (xem {@link #evictPaper}), TTL này chỉ là lưới an toàn cho trường hợp
     * có ai đó sửa dữ liệu thẳng dưới DB.
     */
    @Value("${exam.redis.paper-ttl-seconds:600}")
    private long paperTtlSeconds;

    /** Khoá tạo phiên giữ trong bao lâu. Chỉ cần dài hơn một transaction ngắn. */
    @Value("${exam.redis.start-lock-seconds:10}")
    private long startLockSeconds;

    /**
     * Bao lâu mới ghi LastActiveAt xuống MySQL một lần.
     *
     * Đây là lý do chính khiến heartbeat cần Redis: 500 học sinh nhịp 15 giây là
     * hơn 30 UPDATE mỗi giây bắn thẳng vào bảng ExamSubmissions chỉ để ghi một
     * cột thời gian. Redis nhận toàn bộ nhịp đó, MySQL chỉ nhận một bản ghi mỗi
     * chu kỳ này. Phải nhỏ hơn hẳn at-risk-after-seconds, nếu không cột
     * LastActiveAt lạc hậu tới mức job quét tưởng nhầm là học sinh đã rớt mạng.
     */
    @Value("${exam.redis.last-active-flush-seconds:30}")
    private long lastActiveFlushSeconds;

    // ── Cache đề thi ────────────────────────────────────────────────────────

    /**
     * Bản đề đã snapshot của một kì thi: nội dung câu hỏi + các lựa chọn, giống
     * hệt nhau với mọi học sinh nên cache dùng chung được.
     *
     * Phần riêng của từng em (đã chọn đáp án nào) KHÔNG nằm trong đây — nó được
     * ghép vào sau khi đọc cache. Cache dùng chung mà lẫn dữ liệu cá nhân thì
     * học sinh này sẽ nhìn thấy bài của học sinh khác.
     */
    public Optional<List<ExamQuestionView>> getPaper(Integer examId) {
        try {
            Object cached = examRedisTemplate.opsForValue().get(paperKey(examId));
            if (cached instanceof CachedExamPaper paper && paper.getQuestions() != null) {
                return Optional.of(paper.getQuestions());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis loi khi doc cache de thi examId={}, doc thang tu DB", examId, e);
            return Optional.empty();
        }
    }

    public void putPaper(Integer examId, List<ExamQuestionView> paper) {
        try {
            examRedisTemplate.opsForValue().set(paperKey(examId),
                    new CachedExamPaper(paper), Duration.ofSeconds(paperTtlSeconds));
        } catch (Exception e) {
            log.warn("Redis loi khi ghi cache de thi examId={}, bo qua cache", examId, e);
        }
    }

    /**
     * Xoá cache đề thi. Gọi ở MỌI chỗ giáo viên đổi cấu trúc đề — thêm câu, gỡ
     * câu, làm mới snapshot, sửa hoặc xoá đề. Quên một chỗ là học sinh vào thi
     * còn thấy đề cũ cho tới khi TTL hết.
     */
    public void evictPaper(Integer examId) {
        try {
            examRedisTemplate.delete(paperKey(examId));
        } catch (Exception e) {
            // Không nghiêm trọng: TTL sẽ dọn hộ trong vài phút.
            log.warn("Redis loi khi xoa cache de thi examId={}", examId, e);
        }
    }

    // ── Khoá lúc tạo phiên ──────────────────────────────────────────────────

    /**
     * Kết quả xin khoá.
     *
     * UNAVAILABLE được tách riêng khỏi ACQUIRED một cách có chủ đích: nếu Redis
     * chết mà cứ coi như đã có khoá thì hai request song song cùng chạy tiếp và
     * cùng INSERT — phía gọi cần biết để quay về khoá DB như trước.
     */
    public enum LockState { ACQUIRED, BUSY, UNAVAILABLE }

    /**
     * Giành quyền tạo phiên cho đúng một cặp (đề, học sinh).
     *
     * Trước đây chỗ này khoá bằng SELECT ... FOR UPDATE trên dòng đề thi, tức là
     * toàn bộ học sinh cùng bấm "Bắt đầu" phải xếp hàng qua một dòng DB duy nhất
     * — đúng lúc vào phòng thi là lúc đông nhất. Khoá Redis hẹp hơn hẳn: hai
     * request của CÙNG một em mới đụng nhau, các em khác vào song song.
     */
    public LockState acquireStartLock(Integer examId, String studentId) {
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(startLockKey(examId, studentId), "1",
                            Duration.ofSeconds(startLockSeconds));
            return Boolean.TRUE.equals(acquired) ? LockState.ACQUIRED : LockState.BUSY;
        } catch (Exception e) {
            log.warn("Redis loi khi lay khoa tao phien examId={} studentId={}, "
                    + "quay ve khoa dong de thi duoi DB", examId, studentId, e);
            return LockState.UNAVAILABLE;
        }
    }

    public void releaseStartLock(Integer examId, String studentId) {
        try {
            stringRedisTemplate.delete(startLockKey(examId, studentId));
        } catch (Exception e) {
            log.warn("Redis loi khi tra khoa tao phien examId={} studentId={}, "
                    + "khoa se tu het han sau {}s", examId, studentId, startLockSeconds, e);
        }
    }

    // ── Presence: học sinh còn online hay không ─────────────────────────────

    /**
     * Ghi nhận học sinh vừa có hoạt động. Gọi ở heartbeat, lúc lưu đáp án và lúc
     * vào / vào lại phòng thi.
     *
     * Key hết hạn sau {@code silenceSeconds} nên bản thân việc key biến mất đã
     * là tín hiệu mất kết nối — không cần đi so sánh mốc thời gian.
     *
     * @return true nếu đã tới lúc ghi LastActiveAt xuống MySQL. Phía gọi phải
     *         tôn trọng giá trị này, đó là chỗ tiết kiệm ghi DB.
     */
    public boolean touchAlive(Integer submissionId, long silenceSeconds) {
        try {
            stringRedisTemplate.opsForValue().set(aliveKey(submissionId), "1",
                    Duration.ofSeconds(silenceSeconds));
            // Van tiết lưu: chỉ request nào đặt được key mới (SET NX) mới ghi DB.
            // Các nhịp còn lại trong cùng chu kỳ thấy key đã có nên bỏ qua.
            Boolean firstInWindow = stringRedisTemplate.opsForValue()
                    .setIfAbsent(dbFlushKey(submissionId), "1",
                            Duration.ofSeconds(lastActiveFlushSeconds));
            return Boolean.TRUE.equals(firstInWindow);
        } catch (Exception e) {
            log.warn("Redis loi khi ghi nhip song submissionId={}, ghi thang xuong DB",
                    submissionId, e);
            // Không có Redis thì quay về hành vi cũ: nhịp nào cũng ghi DB.
            return true;
        }
    }

    /**
     * Tra presence cho cả lô phiên trong một vòng. Job quét chạy mỗi 30 giây và
     * có thể phải hỏi hàng trăm phiên; hỏi từng cái là hàng trăm lượt đi về
     * mạng, pipeline gom lại còn một.
     *
     * @return tập submissionId còn sống, hoặc {@link Optional#empty()} nếu Redis
     *         không dùng được (phía gọi phải tự xoay bằng LastActiveAt)
     */
    public Optional<Set<Integer>> findAlive(List<Integer> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Optional.of(Collections.emptySet());
        }
        try {
            List<String> keys = submissionIds.stream().map(this::aliveKey).toList();
            List<Object> raw = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (String key : keys) {
                    connection.keyCommands().exists(key.getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
            Set<Integer> alive = new HashSet<>();
            for (int i = 0; i < submissionIds.size() && i < raw.size(); i++) {
                if (Boolean.TRUE.equals(raw.get(i))) {
                    alive.add(submissionIds.get(i));
                }
            }
            return Optional.of(alive);
        } catch (Exception e) {
            log.warn("Redis loi khi tra nhip song theo lo ({} phien)", submissionIds.size(), e);
            return Optional.empty();
        }
    }

    /** Dọn key của một phiên đã chốt. Không còn ai cần nhịp sống của bài đã nộp. */
    public void clearSession(Integer submissionId) {
        try {
            stringRedisTemplate.delete(List.of(aliveKey(submissionId), dbFlushKey(submissionId)));
        } catch (Exception e) {
            log.warn("Redis loi khi don key phien submissionId={}", submissionId, e);
        }
    }

    // ── Key ─────────────────────────────────────────────────────────────────

    private String paperKey(Integer examId) {
        return String.format(PAPER_KEY, examId);
    }

    private String startLockKey(Integer examId, String studentId) {
        return String.format(START_LOCK_KEY, examId, studentId);
    }

    private String aliveKey(Integer submissionId) {
        return String.format(ALIVE_KEY, submissionId);
    }

    private String dbFlushKey(Integer submissionId) {
        return String.format(DB_FLUSH_KEY, submissionId);
    }
}
