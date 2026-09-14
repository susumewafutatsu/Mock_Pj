package com.example.demo.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;

/** Khoá job nền giữa nhiều máy chủ. */
@Component
@RequiredArgsConstructor
public class SchedulerLock {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLock.class);

    /** Tên node trong log và trong bảng — "pid@host" của JVM là đủ phân biệt. */
    private static final String NODE = ManagementFactory.getRuntimeMXBean().getName();

    private final JdbcTemplate jdbc;

    /** Chạy {@code task} nếu giành được khoá {@code name}; ngược lại bỏ qua lượt này. */
    public boolean runExclusively(String name, Duration ttl, Runnable task) {
        if (!tryAcquire(name, ttl)) {
            log.debug("Job {} đang chạy ở node khác, bỏ qua lượt này", name);
            return false;
        }
        task.run();
        return true;
    }

    boolean tryAcquire(String name, Duration ttl) {
        LocalDateTime now = LocalDateTime.now();
        Timestamp until = Timestamp.valueOf(now.plus(ttl));
        Timestamp nowTs = Timestamp.valueOf(now);

        int updated = jdbc.update(
                "UPDATE SchedulerLocks SET LockedUntil = ?, LockedAt = ?, LockedBy = ? "
                        + "WHERE Name = ? AND LockedUntil <= ?",
                until, nowTs, NODE, name, nowTs);
        if (updated == 1) {
            return true;
        }

        // Chưa có dòng nào cho job này (lần chạy đầu tiên): chèn vào.
        try {
            jdbc.update("INSERT INTO SchedulerLocks (Name, LockedUntil, LockedAt, LockedBy) VALUES (?, ?, ?, ?)",
                    name, until, nowTs, NODE);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
