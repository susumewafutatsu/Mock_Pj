package com.example.demo.util;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Cắt thời gian về đúng độ chính xác mà cột DATETIME lưu được. */
public final class DbTime {

    private DbTime() {
    }

    /** Bỏ phần giây lẻ để giá trị ghi xuống DB không bị làm tròn lên. */
    public static LocalDateTime atSecond(LocalDateTime moment) {
        return moment == null ? null : moment.truncatedTo(ChronoUnit.SECONDS);
    }

    /** Giờ hiện tại, đã cắt về giây. */
    public static LocalDateTime now() {
        return atSecond(LocalDateTime.now());
    }
}
