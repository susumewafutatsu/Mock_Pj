package com.example.demo.repository;

import com.example.demo.domain.model.RoomExam;
import com.example.demo.domain.model.RoomExamKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RoomExamRepository extends JpaRepository<RoomExam, RoomExamKey> {

    List<RoomExam> findById_RoomIdOrderByOrderNoAsc(Integer roomId);

    boolean existsById_RoomIdAndId_ExamId(Integer roomId, Integer examId);

    long countById_RoomId(Integer roomId);

    /** Các phòng chứa đề này mà người đó đang là thành viên ACTIVE, bất kể pha. */
    @Query("""
            select r from RoomExam re
            join re.room r
            join RoomMember m on m.id.roomId = re.id.roomId
            where re.id.examId = :examId
              and m.id.userId = :userId
              and m.status = com.example.demo.domain.enums.MemberStatus.ACTIVE
            """)
    List<com.example.demo.domain.model.Room> findRoomsForActiveMember(@Param("examId") Integer examId,
                                                                      @Param("userId") String userId);

    /** Thời lượng đề dài nhất của một phòng, phút. null nếu phòng chưa có đề. */
    @Query("""
            select max(re.exam.durationMinutes) from RoomExam re
            where re.id.roomId = :roomId
            """)
    Integer findLongestDurationMinutes(@Param("roomId") Integer roomId);

    /** Như trên cho cả một tập phòng: từng dòng là [roomId, phút]. */
    @Query("""
            select re.id.roomId, max(re.exam.durationMinutes) from RoomExam re
            where re.id.roomId in :roomIds
            group by re.id.roomId
            """)
    List<Object[]> findLongestDurationByRoomIdIn(@Param("roomIds") Collection<Integer> roomIds);

    /** Đề của một phòng kèm nội dung đề, theo thứ tự hiển thị. */
    @Query("""
            select re from RoomExam re
            join fetch re.exam
            where re.id.roomId = :roomId
            order by re.orderNo asc
            """)
    List<RoomExam> findWithExamByRoomId(@Param("roomId") Integer roomId);

    /** Đề của một tập phòng kèm nội dung đề. */
    @Query("""
            select re from RoomExam re
            join fetch re.exam
            where re.id.roomId in :roomIds
            order by re.orderNo asc
            """)
    List<RoomExam> findWithExamByRoomIdIn(@Param("roomIds") Collection<Integer> roomIds);

    /** Những phòng đang dùng một đề — dùng ở màn hình quản lý đề của người ra đề. */
    @Query("select re.id.roomId from RoomExam re where re.id.examId = :examId")
    List<Integer> findRoomIdsByExamId(@Param("examId") Integer examId);

    /** Số đề của cả một tập phòng trong một câu truy vấn. */
    @Query("""
            select re.id.roomId as roomId, count(re) as total
            from RoomExam re
            where re.id.roomId in :roomIds
            group by re.id.roomId
            """)
    List<RoomExamCount> countByRoomIdIn(@Param("roomIds") Collection<Integer> roomIds);

    /** Một dòng của {@link #countByRoomIdIn}. */
    interface RoomExamCount {
        Integer getRoomId();
        long getTotal();
    }

    /** Ánh xạ đề → phòng cho một tập đề, dùng khi dựng danh sách đề của thí sinh. */
    @Query("""
            select re.id.examId, re.id.roomId
            from RoomExam re
            where re.id.roomId in :roomIds
            """)
    List<Object[]> findExamRoomPairs(@Param("roomIds") Collection<Integer> roomIds);
}
