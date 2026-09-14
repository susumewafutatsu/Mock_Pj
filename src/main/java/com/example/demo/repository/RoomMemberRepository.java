package com.example.demo.repository;

import com.example.demo.domain.enums.MemberStatus;
import com.example.demo.domain.model.RoomMember;
import com.example.demo.domain.model.RoomMemberKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Thành viên phòng thi — thay cho ClassStudentRepository cũ. */
@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberKey> {

    Optional<RoomMember> findById_RoomIdAndId_UserId(Integer roomId, String userId);

    boolean existsById_RoomIdAndId_UserIdAndStatus(Integer roomId, String userId, MemberStatus status);

    /** Các phòng một người đang tham gia. Chỉ lấy id vì chỗ dùng là lọc danh sách đề. */
    @Query("""
            select m.id.roomId from RoomMember m
            where m.id.userId = :userId and m.status = com.example.demo.domain.enums.MemberStatus.ACTIVE
            """)
    List<Integer> findActiveRoomIdsByUserId(@Param("userId") String userId);

    /** Danh sách người đang ở trong phòng, theo thứ tự ghế. */
    @Query("""
            select m from RoomMember m
            join fetch m.user
            where m.id.roomId = :roomId
              and m.status = com.example.demo.domain.enums.MemberStatus.ACTIVE
            order by m.seatNo asc
            """)
    List<RoomMember> findActiveMembers(@Param("roomId") Integer roomId);

    /** Người có mặt trong buổi thi của phòng, cho bảng xếp hạng. */
    @Query("""
            select m from RoomMember m
            join fetch m.user
            where m.id.roomId = :roomId
              and m.status <> com.example.demo.domain.enums.MemberStatus.KICKED
            order by m.seatNo asc
            """)
    List<RoomMember> findSeatedMembers(@Param("roomId") Integer roomId);

    /** Số ghế đã cấp trong phòng — ĐẾM CẢ NGƯỜI ĐÃ RỜI ĐI. */
    @Query("select count(m) from RoomMember m where m.id.roomId = :roomId")
    long countSeatsIssued(@Param("roomId") Integer roomId);

    /** Số ghế lớn nhất đã cấp, đọc bằng KHOÁ. */
    @Query(value = """
            SELECT COALESCE(MAX(SeatNo), 0) FROM RoomMembers
            WHERE RoomID = :roomId
            FOR UPDATE
            """, nativeQuery = true)
    int findMaxSeatForUpdate(@Param("roomId") Integer roomId);

    /** Sĩ số hiện tại của phòng. */
    @Query("""
            select count(m) from RoomMember m
            where m.id.roomId = :roomId
              and m.status = com.example.demo.domain.enums.MemberStatus.ACTIVE
            """)
    long countActive(@Param("roomId") Integer roomId);

    /** Sĩ số của cả một tập phòng trong một câu truy vấn. */
    @Query("""
            select m.id.roomId as roomId, count(m) as total
            from RoomMember m
            where m.id.roomId in :roomIds
              and m.status = com.example.demo.domain.enums.MemberStatus.ACTIVE
            group by m.id.roomId
            """)
    List<RoomHeadcount> countActiveByRoomIdIn(@Param("roomIds") Collection<Integer> roomIds);

    /** Số người đang mở trang phòng: [roomId, số người]. */
    @Query("""
            select m.id.roomId, count(m) from RoomMember m
            where m.id.roomId in :roomIds
              and m.status = com.example.demo.domain.enums.MemberStatus.ACTIVE
              and m.lastSeenAt >= :since
            group by m.id.roomId
            """)
    List<Object[]> countOnlineByRoomIdIn(@Param("roomIds") Collection<Integer> roomIds,
                                         @Param("since") java.time.LocalDateTime since);

    /** Một dòng của {@link #countActiveByRoomIdIn}. */
    interface RoomHeadcount {
        Integer getRoomId();
        long getTotal();
    }
}
