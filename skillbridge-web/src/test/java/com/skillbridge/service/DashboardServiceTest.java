package com.skillbridge.service;

import com.skillbridge.model.Activity;
import com.skillbridge.repository.ActivityRepository;
import com.skillbridge.repository.StudentRosterRepository;
import com.skillbridge.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DashboardServiceTest {

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private StudentRosterRepository rosterRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Nested
    @DisplayName("getDashboardStats")
    class GetDashboardStats {

        @Test
        @DisplayName("返回所有统计指标")
        void shouldReturnAllStats() {
            when(activityRepository.countByStatusNot(2)).thenReturn(10L);
            when(activityRepository.countByStatus(0)).thenReturn(5L);
            when(activityRepository.countByStatus(1)).thenReturn(3L);
            when(activityRepository.countByCreatedAtAfter(any())).thenReturn(2L);
            when(submissionRepository.count()).thenReturn(100L);
            when(submissionRepository.countBySubmitTimeAfter(any())).thenReturn(8L);
            when(activityRepository.findByStatusAndDeadlineBetween(eq(0), any(), any()))
                    .thenReturn(List.of());
            when(activityRepository.findByStatusAndDeadlineAfter(eq(0), any()))
                    .thenReturn(List.of());

            Map<String, Object> stats = dashboardService.getDashboardStats();

            assertEquals(10L, stats.get("totalActivities"));
            assertEquals(5L, stats.get("activeActivities"));
            assertEquals(3L, stats.get("closedActivities"));
            assertEquals(2L, stats.get("todayNewActivities"));
            assertEquals(100L, stats.get("totalSubmissions"));
            assertEquals(8L, stats.get("todayNewSubmissions"));
            assertNotNull(stats.get("todoItems"));
            assertInstanceOf(List.class, stats.get("todoItems"));
        }
    }

    @Nested
    @DisplayName("computeTodoItems")
    class ComputeTodoItems {

        @Test
        @DisplayName("即将截止的活动生成紧急提醒")
        void shouldCreateUrgentItemsForSoonDeadline() {
            Activity a = new Activity();
            a.setId(1L);
            a.setName("期中申报");
            a.setDeadline(LocalDateTime.now().plusHours(3));
            when(activityRepository.findByStatusAndDeadlineBetween(eq(0), any(), any()))
                    .thenReturn(List.of(a));
            when(activityRepository.findByStatusAndDeadlineAfter(eq(0), any()))
                    .thenReturn(List.of());
            List<Object[]> emptyRosterCounts = List.of();
            when(rosterRepository.countByActivityIds(any())).thenReturn(emptyRosterCounts);

            Map<String, Object> stats = dashboardService.getDashboardStats();
            @SuppressWarnings("unchecked")
            List<DashboardService.TodoItem> items = (List<DashboardService.TodoItem>) stats.get("todoItems");

            assertEquals(1, items.size());
            assertTrue(items.get(0).isUrgent());
            assertTrue(items.get(0).getText().contains("期中申报"));
            assertEquals("/teacher/activity/1/submissions", items.get(0).getLink());
        }

        @Test
        @DisplayName("未导入名单的活动生成提醒")
        void shouldCreateReminderForMissingRoster() {
            Activity a = new Activity();
            a.setId(2L);
            a.setName("期末申报");
            a.setDeadline(LocalDateTime.now().plusDays(7));
            when(activityRepository.findByStatusAndDeadlineBetween(eq(0), any(), any()))
                    .thenReturn(List.of());
            when(activityRepository.findByStatusAndDeadlineAfter(eq(0), any()))
                    .thenReturn(List.of(a));
            List<Object[]> rosterCounts = Collections.singletonList(new Object[]{2L, 0L});
            when(rosterRepository.countByActivityIds(any()))
                    .thenReturn(rosterCounts);

            Map<String, Object> stats = dashboardService.getDashboardStats();
            @SuppressWarnings("unchecked")
            List<DashboardService.TodoItem> items = (List<DashboardService.TodoItem>) stats.get("todoItems");

            assertEquals(1, items.size());
            assertFalse(items.get(0).isUrgent());
            assertTrue(items.get(0).getText().contains("期末申报"));
            assertTrue(items.get(0).getText().contains("未导入学生名单"));
            assertEquals("/teacher/activity/2/roster", items.get(0).getLink());
        }

        @Test
        @DisplayName("已有名单的活动不生成提醒")
        void shouldSkipActivitiesWithRoster() {
            Activity a = new Activity();
            a.setId(3L);
            a.setName("三月申报");
            a.setDeadline(LocalDateTime.now().plusDays(7));
            when(activityRepository.findByStatusAndDeadlineBetween(eq(0), any(), any()))
                    .thenReturn(List.of());
            when(activityRepository.findByStatusAndDeadlineAfter(eq(0), any()))
                    .thenReturn(List.of(a));
            List<Object[]> rosterCounts = Collections.singletonList(new Object[]{3L, 50L});
            when(rosterRepository.countByActivityIds(any()))
                    .thenReturn(rosterCounts);

            Map<String, Object> stats = dashboardService.getDashboardStats();
            @SuppressWarnings("unchecked")
            List<DashboardService.TodoItem> items = (List<DashboardService.TodoItem>) stats.get("todoItems");

            assertEquals(0, items.size());
        }

        @Test
        @DisplayName("超过10条时截断")
        void shouldLimitToMax10Items() {
            List<Activity> urgentActivities = java.util.stream.LongStream.range(1, 13)
                    .mapToObj(id -> {
                        Activity a = new Activity();
                        a.setId(id);
                        a.setName("活动" + id);
                        a.setDeadline(LocalDateTime.now().plusHours(1));
                        return a;
                    })
                    .collect(java.util.stream.Collectors.toList());
            when(activityRepository.findByStatusAndDeadlineBetween(eq(0), any(), any()))
                    .thenReturn(urgentActivities);
            when(activityRepository.findByStatusAndDeadlineAfter(eq(0), any()))
                    .thenReturn(List.of());

            Map<String, Object> stats = dashboardService.getDashboardStats();
            @SuppressWarnings("unchecked")
            List<DashboardService.TodoItem> items = (List<DashboardService.TodoItem>) stats.get("todoItems");

            assertEquals(10, items.size());
        }
    }

    @Nested
    @DisplayName("TodoItem")
    class TodoItemTest {

        @Test
        @DisplayName("构造和访问器正常")
        void shouldConstructAndAccess() {
            DashboardService.TodoItem item = new DashboardService.TodoItem("test", "/link", true);
            assertEquals("test", item.getText());
            assertEquals("/link", item.getLink());
            assertTrue(item.isUrgent());
        }
    }
}
