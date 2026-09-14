package com.example.demo.repository;

import com.example.demo.domain.enums.RoomStatus;
import com.example.demo.domain.model.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Integer> {

    boolean existsByCode(String code);

    /** Vào phòng bằng mã — đường chính của thí sinh. */
    Optional<Room> findByCode(String code);

    /** Phòng của một người ra đề, mới nhất lên trước. */
    @Query("""
            select r from Room r
            left join fetch r.level l
            left join fetch l.subject
            where r.owner.userId = :ownerId
            order by r.createdAt desc
            """)
    List<Room> findByOwner(@Param("ownerId") String ownerId);

    /** Nạp một tập phòng kèm chủ phòng / trình độ / môn học trong một câu. */
    @Query("""
            select r from Room r
            left join fetch r.owner
            left join fetch r.level l
            left join fetch l.subject
            where r.roomId in :roomIds
            """)
    List<Room> findAllByIdWithDetails(@Param("roomIds") Collection<Integer> roomIds);

    /** Phòng đang mở cho người lạ tìm thấy và xin vào. */
    @Query("""
            select r from Room r
            left join fetch r.owner
            left join fetch r.level l
            left join fetch l.subject
            where r.status = :status and r.joinPolicy = com.example.demo.domain.enums.JoinPolicy.OPEN
            order by r.createdAt desc
            """)
    List<Room> findOpenRooms(@Param("status") RoomStatus status);

    /** Khoá dòng phòng để tuần tự hoá việc cấp ghế. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.roomId = :roomId")
    Optional<Room> findByIdForUpdate(@Param("roomId") Integer roomId);

    long countByOwner_UserId(String ownerId);

    /** Phòng hẹn giờ còn thiếu thông báo nhắc / bắt đầu / kết thúc. */
    @Query("""
            select r from Room r
            where r.status in :statuses
              and r.startTime is not null
              and r.endNotifiedAt is null
            """)
    List<Room> findScheduledNeedingNotice(@Param("statuses") Collection<RoomStatus> statuses);
}
