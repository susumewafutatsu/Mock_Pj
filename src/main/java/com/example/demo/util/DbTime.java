package com.example.demo.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Cắt thời gian về đúng độ chính xác mà cột DATETIME lưu được.
 *
 * Vì sao cần: MySQL {@code DATETIME} không có phần giây lẻ, và khi nhận một
 * giá trị có giây lẻ nó LÀM TRÒN chứ không cắt bỏ. 09:00:15.837 vào DB thành
 * 09:00:16 — tức là một mốc thời gian nằm ở TƯƠNG LAI so với lúc ghi.
 *
 * Với những mốc mang nghĩa "đến hạn ngay bây giờ" thì nửa giây tương lai đó
 * đủ để làm hỏng hành vi: người học bấm thêm một bộ thẻ rồi mở ngay hàng đợi
 * và thấy trống rỗng, vì mọi thẻ vừa tạo đều "chưa tới hạn". Lỗi này đã xảy ra
 * thật khi chạy thử và nó không hề lộ ra ở test đơn vị — nơi không có MySQL.
 *
 * Cắt về giây ở phía Java trước khi ghi giải quyết triệt để: giá trị không còn
 * phần lẻ thì DB không có gì để làm tròn. Cách này đúng với mọi độ chính xác
 * của cột, kể cả khi sau này đổi sang DATETIME(3).
 */
public final class DbTime {

    private DbTime() {
    }

    /**
     * Bỏ phần giây lẻ để giá trị ghi xuống DB không bị làm tròn lên.
     *
     * Dùng cho mọi mốc thời gian được đem đi SO SÁNH với thời điểm hiện tại
     * (hạn ôn thẻ, hạn ôn câu sai). Những cột chỉ để hiển thị thì không cần.
     */
    public static LocalDateTime atSecond(LocalDateTime moment) {
        return moment == null ? null : moment.truncatedTo(ChronoUnit.SECONDS);
    }

    /** Giờ hiện tại, đã cắt về giây. */
    public static LocalDateTime now() {
        return atSecond(LocalDateTime.now());
    }
}
