package com.skillbridge.service;

import com.skillbridge.model.Activity;
import com.skillbridge.repository.ActivityRepository;
import com.skillbridge.repository.StudentRosterRepository;
import com.skillbridge.repository.SubmissionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仪表盘统计服务。
 * <p>
 * 聚合活动与提交数据，提供首页/仪表盘所需的统计指标。
 */
@Service
public class DashboardService {

    private static final int STATUS_DRAFT = 2;
    private static final int STATUS_ACTIVE = 0;
    private static final int STATUS_CLOSED = 1;

    private final ActivityRepository activityRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentRosterRepository rosterRepository;

    public DashboardService(ActivityRepository activityRepository,
                            SubmissionRepository submissionRepository,
                            StudentRosterRepository rosterRepository) {
        this.activityRepository = activityRepository;
        this.submissionRepository = submissionRepository;
        this.rosterRepository = rosterRepository;
    }

    /**
     * 获取仪表盘统计数据。
     * <p>
     * 返回指标：
     * <ul>
     *   <li>totalActivities：正式活动总数（排除草稿）</li>
     *   <li>activeActivities：报名中的活动数</li>
     *   <li>closedActivities：已截止的活动数</li>
     *   <li>todayNewActivities：今日新建活动数</li>
     *   <li>totalSubmissions：提交记录总数</li>
     *   <li>todayNewSubmissions：今日新增提交数</li>
     *   <li>todoItems：待办事项列表（即将截止、未导入名单等）</li>
     * </ul>
     */
    public Map<String, Object> getDashboardStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalActivities", activityRepository.countByStatusNot(STATUS_DRAFT));
        stats.put("activeActivities", activityRepository.countByStatus(STATUS_ACTIVE));
        stats.put("closedActivities", activityRepository.countByStatus(STATUS_CLOSED));
        stats.put("todayNewActivities", activityRepository.countByCreatedAtAfter(todayStart));
        stats.put("totalSubmissions", submissionRepository.count());
        stats.put("todayNewSubmissions", submissionRepository.countBySubmitTimeAfter(todayStart));
        stats.put("todoItems", computeTodoItems(now));
        return stats;
    }

    /**
     * 计算待办事项：
     * <ul>
     *   <li>紧急：报名中且 24 小时内截止的活动</li>
     *   <li>提醒：报名中但未导入学生名单的活动</li>
     * </ul>
     * 最多返回 10 条，避免仪表盘过长。
     */
    private List<TodoItem> computeTodoItems(LocalDateTime now) {
        List<TodoItem> items = new ArrayList<>();
        LocalDateTime urgentThreshold = now.plusHours(24);

        // 1. 即将截止的活动（24 小时内）
        List<Activity> urgentActivities = activityRepository
                .findByStatusAndDeadlineBetween(STATUS_ACTIVE, now, urgentThreshold);
        for (Activity a : urgentActivities) {
            long hoursLeft = java.time.Duration.between(now, a.getDeadline()).toHours();
            String timeDesc = hoursLeft <= 0 ? "即将截止"
                    : hoursLeft < 1 ? "不足 1 小时"
                    : "剩余 " + hoursLeft + " 小时";
            items.add(new TodoItem(
                    "活动「" + a.getName() + "」" + timeDesc + "后截止",
                    "/teacher/activity/" + a.getId() + "/submissions",
                    true
            ));
        }

        // 2. 报名中但未导入名单的活动（仅前 5 个，按截止时间升序）
        List<Activity> activeActivities = activityRepository
                .findByStatusAndDeadlineAfter(STATUS_ACTIVE, now);
        if (!activeActivities.isEmpty()) {
            List<Long> ids = activeActivities.stream().map(Activity::getId).collect(Collectors.toList());
            Map<Long, Long> rosterCounts = new HashMap<>();
            for (Object[] row : rosterRepository.countByActivityIds(ids)) {
                rosterCounts.put((Long) row[0], (Long) row[1]);
            }
            int rosterMissingCount = 0;
            for (Activity a : activeActivities) {
                if (rosterMissingCount >= 5) break;
                if (rosterCounts.getOrDefault(a.getId(), 0L) == 0) {
                    rosterMissingCount++;
                    items.add(new TodoItem(
                            "活动「" + a.getName() + "」尚未导入学生名单",
                            "/teacher/activity/" + a.getId() + "/roster",
                            false
                    ));
                }
            }
        }

        // 限制总条数
        if (items.size() > 10) {
            items = items.subList(0, 10);
        }
        return items;
    }

    /** 待办事项条目 */
    public static class TodoItem {
        private final String text;
        private final String link;
        private final boolean urgent;

        public TodoItem(String text, String link, boolean urgent) {
            this.text = text;
            this.link = link;
            this.urgent = urgent;
        }

        public String getText() { return text; }
        public String getLink() { return link; }
        public boolean isUrgent() { return urgent; }
    }
}
