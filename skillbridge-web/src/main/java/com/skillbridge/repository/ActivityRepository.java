package com.skillbridge.repository;

import com.skillbridge.model.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    /**
     * 查询所有非草稿状态的活动（status != 2），用于活动列表展示。
     * 草稿活动仅在教师确认字段流程中可见，不进入正式活动列表。
     */
    List<Activity> findByStatusNot(Integer status);

    /**
     * 分页查询所有非草稿状态的活动，按 Pageable 排序。
     */
    Page<Activity> findByStatusNot(Integer status, Pageable pageable);

    /**
     * 按名称模糊搜索非草稿活动（status != 2）。
     */
    Page<Activity> findByNameContainingAndStatusNot(String name, Integer status, Pageable pageable);

    /**
     * 按指定状态分页查询（用于状态筛选，状态值 0=报名中 或 1=已截止）。
     */
    Page<Activity> findByStatus(Integer status, Pageable pageable);

    /**
     * 按名称模糊搜索 + 指定状态分页查询。
     */
    Page<Activity> findByNameContainingAndStatus(String name, Integer status, Pageable pageable);

    /** 统计指定状态的活动数 */
    long countByStatus(Integer status);

    /** 统计非指定状态的活动数（用于排除草稿） */
    long countByStatusNot(Integer status);

    /** 统计指定时间之后创建的活动数（用于今日新增） */
    long countByCreatedAtAfter(LocalDateTime time);

    /** 查询指定状态且截止时间在指定范围内的活动（用于即将截止提醒） */
    List<Activity> findByStatusAndDeadlineBetween(Integer status, LocalDateTime start, LocalDateTime end);

    /** 查询指定状态且截止时间在指定时间之后的活动（用于报名中活动列表） */
    List<Activity> findByStatusAndDeadlineAfter(Integer status, LocalDateTime time);

    /* ===================== 按归属教师查询 ===================== */

    /** 分页查询某教师的非草稿活动 */
    Page<Activity> findByOwnerIdAndStatusNot(String ownerId, Integer status, Pageable pageable);

    /** 按名称模糊搜索某教师的非草稿活动 */
    Page<Activity> findByOwnerIdAndNameContainingAndStatusNot(String ownerId, String name, Integer status, Pageable pageable);

    /** 按状态查询某教师的活动 */
    Page<Activity> findByOwnerIdAndStatus(String ownerId, Integer status, Pageable pageable);

    /** 按名称模糊搜索 + 指定状态查询某教师的活动 */
    Page<Activity> findByOwnerIdAndNameContainingAndStatus(String ownerId, String name, Integer status, Pageable pageable);

    /** 统计某教师的活动数 */
    long countByOwnerId(String ownerId);

    /** 统计某教师指定状态的活动数 */
    long countByOwnerIdAndStatus(String ownerId, Integer status);
}
