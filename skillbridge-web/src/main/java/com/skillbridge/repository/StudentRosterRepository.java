package com.skillbridge.repository;

import com.skillbridge.model.StudentRosterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRosterRepository extends JpaRepository<StudentRosterEntry, Long> {

    /** 查询活动的所有名单条目（按学号排序） */
    List<StudentRosterEntry> findByActivityIdOrderByStudentIdAsc(Long activityId);

    /** 按活动 + 学号 + 身份证号查找（用于白名单校验） */
    Optional<StudentRosterEntry> findByActivityIdAndStudentIdAndIdCard(Long activityId, String studentId, String idCard);

    /** 统计活动名单人数 */
    long countByActivityId(Long activityId);

    /** 删除活动的所有名单条目 */
    void deleteByActivityId(Long activityId);

    /** 批量统计多个活动 ID 的名单人数 */
    @Query("SELECT r.activityId, COUNT(r) FROM StudentRosterEntry r WHERE r.activityId IN :ids GROUP BY r.activityId")
    List<Object[]> countByActivityIds(@Param("ids") List<Long> ids);
}
