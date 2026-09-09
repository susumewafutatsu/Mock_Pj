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

    /**
     * Đề đó có nằm trong ít nhất một phòng mà người này đang là thành viên
     * đang hoạt động, và phòng đó còn cho làm bài không.
     *
     * Đây là câu truy vấn thay thế cho {@code existsById_ClassIdAndId_StudentId}
     * cũ, và nó phải kiểm ba thứ chứ không phải một: người đó còn ACTIVE, phòng
     * còn ở trạng thái cho làm bài, và đề thật sự thuộc phòng đó. Thiếu vế
     * trạng thái phòng thì người bị mời ra vẫn thi được, còn thiếu vế ACTIVE
     * thì người đã rời phòng cũng vậy.
     */
    @Query("""
            select count(re) > 0
            from RoomExam re
            join RoomMember m on m.id.roomId = re.id.roomId
            where re.id.examId = :examId
              and m.id.userId = :userId
              and m.status = com.example.demo.domain.enums.MemberStatus.ACTIVE
              and re.room.status in (
                    com.example.demo.domain.enums.RoomStatus.OPEN,
                    com.example.demo.domain.enums.RoomStatus.RUNNING)
            """)
    boolean canUserTakeExam(@Param("examId") Integer examId, @Param("userId") String userId);

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

    /**
     * Ánh xạ đề → phòng cho một tập đề, dùng khi dựng danh sách đề của thí sinh.
     *
     * @return từng dòng là [examId, roomId]
     */
    @Query("""
            select re.id.examId, re.id.roomId
            from RoomExam re
            where re.id.roomId in :roomIds
            """)
    List<Object[]> findExamRoomPairs(@Param("roomIds") Collection<Integer> roomIds);
}
