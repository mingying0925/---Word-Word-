package com.skillbridge.repository;

import com.skillbridge.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    // 根据活动ID和学号、身份证查询，用于验证重复提交
    Optional<Submission> findByActivityIdAndStudentIdAndIdCard(Long activityId, String studentId, String idCard);

    List<Submission> findByActivityId(Long activityId);

    /**
     * 按活动 ID 查询所有提交，并 JOIN FETCH 关联的活动，避免批量导出时逐条懒加载 activity（N+1）。
     */
    @Query("SELECT s FROM Submission s JOIN FETCH s.activity WHERE s.activity.id = :activityId")
    List<Submission> findByActivityIdWithActivity(@Param("activityId") Long activityId);

    /** 统计指定时间之后的提交数（用于今日新增） */
    long countBySubmitTimeAfter(LocalDateTime time);

    /** 按活动 ID 统计提交数 */
    long countByActivityId(Long activityId);

    /** 批量统计多个活动 ID 的提交数 */
    @Query("SELECT s.activity.id, COUNT(s) FROM Submission s WHERE s.activity.id IN :ids GROUP BY s.activity.id")
    List<Object[]> countByActivityIds(@Param("ids") List<Long> ids);
}
