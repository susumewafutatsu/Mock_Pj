package com.example.demo.repository;

import com.example.demo.domain.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    List<Notification> findByUserUserId(String userId);

    /** Thông báo mới nhất của một người, cho ô chuông. */
    List<Notification> findByUser_UserIdOrderByNotificationIdDesc(String userId, Pageable pageable);

    long countByUser_UserIdAndIsReadFalse(String userId);

    /** Hôm nay đã nhắc loại này cho người này chưa — chống nhắc trùng của job hằng ngày. */
    boolean existsByUser_UserIdAndKindAndCreatedAtGreaterThanEqual(
            String userId, String kind, LocalDateTime since);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.user.userId = :userId and n.isRead = false")
    int markAllRead(@Param("userId") String userId);
}
