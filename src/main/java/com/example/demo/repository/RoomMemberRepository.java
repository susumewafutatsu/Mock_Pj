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

/**
 * Thành viên phòng thi — thay cho ClassStudentRepository cũ.
 *
 * Mọi truy vấn "ai đang ở trong phòng" đều phải lọc theo {@code ACTIVE}: dòng
 * của người đã rời đi vẫn nằm lại để giữ ghế và giữ lịch sử, nên đếm tất cả
 * dòng sẽ ra sĩ số sai.
 */
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

    /**
     * Số ghế đã cấp trong phòng — ĐẾM CẢ NGƯỜI ĐÃ RỜI ĐI.
     *
     * Cố ý không lọc theo ACTIVE: ghế đã cấp thì không bao giờ cấp lại, nên
     * đây mới là con số dùng để BÁO CÁO. Muốn biết sĩ số hiện tại thì dùng
     * {@link #countActive}.
     *
     * KHÔNG dùng câu này để cấp ghế mới — xem {@link #findMaxSeatForUpdate}.
     */
    @Query("select count(m) from RoomMember m where m.id.roomId = :roomId")
    long countSeatsIssued(@Param("roomId") Integer roomId);

    /**
     * Số ghế lớn nhất đã cấp, đọc bằng KHOÁ. Đây là câu duy nhất được dùng để
     * quyết định ghế kế tiếp.
     *
     * Vì sao phải là câu riêng thay vì dùng {@link #countSeatsIssued}: MySQL
     * InnoDB mặc định chạy ở mức REPEATABLE READ. Ảnh chụp dữ liệu của một
     * transaction được lập ở câu ĐỌC THƯỜNG đầu tiên của nó, và mọi câu đọc
     * thường sau đó — kể cả sau khi đã giành được khoá — vẫn nhìn vào ảnh chụp
     * cũ đó. Nên chuyện này đã xảy ra thật khi chạy thử:
     *
     *   T1: tra phòng theo mã (lập ảnh chụp) → khoá dòng phòng → đếm = 0 → cấp ghế 1 → commit
     *   T2: tra phòng theo mã (lập ảnh chụp, TRƯỚC khi T1 commit) → chờ khoá
     *       → có khoá → đếm = 0 (ảnh chụp cũ!) → cấp ghế 1 → ĐỤNG UNIQUE
     *
     * Khoá dòng phòng chặn đúng thứ tự, nhưng nó không làm ảnh chụp mới lại.
     * {@code FOR UPDATE} thì có: đây là một đọc-có-khoá, luôn nhìn thấy bản
     * mới nhất đã commit. Với phòng còn rỗng, nó còn giữ luôn khoảng trống của
     * chỉ mục nên transaction khác không chen được một dòng vào giữa.
     *
     * Dùng MAX chứ không COUNT vì ghế không bao giờ được cấp lại: MAX mới là
     * con số nói đúng "ghế kế tiếp là số mấy", kể cả nếu về sau có ai xoá cứng
     * một dòng thành viên.
     */
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

    /**
     * Sĩ số của cả một tập phòng trong một câu truy vấn.
     *
     * Trang "phòng của tôi" hiện 5–20 phòng cùng lúc; đếm từng phòng một là
     * ngần ấy lượt truy vấn cho một màn hình.
     *
     * Người gọi phải tự chặn danh sách rỗng.
     */
    @Query("""
            select m.id.roomId as roomId, count(m) as total
            from RoomMember m
            where m.id.roomId in :roomIds
              and m.status = com.example.demo.domain.enums.MemberStatus.ACTIVE
            group by m.id.roomId
            """)
    List<RoomHeadcount> countActiveByRoomIdIn(@Param("roomIds") Collection<Integer> roomIds);

    /** Một dòng của {@link #countActiveByRoomIdIn}. */
    interface RoomHeadcount {
        Integer getRoomId();
        long getTotal();
    }
}
