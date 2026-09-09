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

    /**
     * Nạp một tập phòng kèm chủ phòng / trình độ / môn học trong một câu.
     *
     * Màn hình "phòng của tôi" bên thí sinh cần mấy tên đó cho từng phòng; để
     * lazy thì mỗi phòng lại thêm ba truy vấn.
     *
     * Người gọi phải tự chặn danh sách rỗng: {@code in ()} không hợp lệ trên
     * một số DB.
     */
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

    /**
     * Khoá dòng phòng để tuần tự hoá việc cấp ghế.
     *
     * Đây là chốt chặn chính của "ai nhanh thì vào". Không có khoá này thì hai
     * request Tham gia gần như cùng lúc đều đọc được cùng một sĩ số cũ, cùng
     * kết luận "còn chỗ" và cùng chen vào — phòng 50 chỗ nhận 52 người.
     *
     * Khoá đặt trên PHÒNG chứ không trên bảng thành viên, nên hai phòng khác
     * nhau vẫn nhận người song song; chỉ những ai tranh nhau cùng một phòng
     * mới phải xếp hàng, và đó đúng là thứ cần xếp hàng.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.roomId = :roomId")
    Optional<Room> findByIdForUpdate(@Param("roomId") Integer roomId);
}
