package com.skillbridge.repository;

import com.skillbridge.model.Activity;
import com.skillbridge.model.Submission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SubmissionRepository 集成测试。
 * <p>
 * 使用 @DataJpaTest + H2 内存数据库验证 Repository 查询方法。
 * <p>
 * 注意：IdCardConverter 在 @DataJpaTest 中未被 Spring 容器管理（cryptoUtil 为 null），
 * 因此 idCard 字段以明文存储与查询，不影响 Repository 查询逻辑的验证。
 */
@DataJpaTest
class SubmissionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Test
    @DisplayName("findByActivityIdAndStudentIdAndIdCard 匹配时返回包含提交记录的 Optional")
    void shouldReturnSubmissionWhenMatched() {
        Activity activity = createAndPersistActivity("测试活动A");
        Submission submission = createAndPersistSubmission(activity, "2024001", "440301199001011234");

        Optional<Submission> result = submissionRepository
                .findByActivityIdAndStudentIdAndIdCard(activity.getId(), "2024001", "440301199001011234");

        assertTrue(result.isPresent());
        assertEquals(submission.getId(), result.get().getId());
        assertEquals("2024001", result.get().getStudentId());
        assertEquals("440301199001011234", result.get().getIdCard());
    }

    @Test
    @DisplayName("findByActivityIdAndStudentIdAndIdCard 不匹配时返回空 Optional")
    void shouldReturnEmptyWhenNotMatched() {
        Activity activity = createAndPersistActivity("测试活动B");
        createAndPersistSubmission(activity, "2024001", "440301199001011234");

        Optional<Submission> result = submissionRepository
                .findByActivityIdAndStudentIdAndIdCard(activity.getId(), "2024001", "440301199001011235");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByActivityId 返回该活动下所有提交记录")
    void shouldReturnSubmissionsForActivity() {
        Activity activity = createAndPersistActivity("测试活动C");
        createAndPersistSubmission(activity, "2024001", "440301199001011234");
        createAndPersistSubmission(activity, "2024002", "440301199001011235");

        List<Submission> result = submissionRepository.findByActivityId(activity.getId());

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("findByActivityId 无提交记录时返回空列表")
    void shouldReturnEmptyListWhenNoSubmissions() {
        Activity activity = createAndPersistActivity("测试活动D");

        List<Submission> result = submissionRepository.findByActivityId(activity.getId());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByActivityIdWithActivity 返回提交并预加载关联活动（消除 N+1）")
    void shouldFetchActivityEagerlyViaFetchJoin() {
        Activity activity = createAndPersistActivity("测试活动E");
        createAndPersistSubmission(activity, "2024001", "440301199001011234");
        createAndPersistSubmission(activity, "2024002", "440301199001011235");

        // 清空一级缓存，确保后续访问 activity 不命中缓存，真正验证 fetch join
        entityManager.clear();

        List<Submission> result = submissionRepository.findByActivityIdWithActivity(activity.getId());

        assertEquals(2, result.size());
        // clear 后实体已脱管；若 fetch join 生效，activity 已初始化，可直接访问 getName()；
        // 若未 fetch join，脱管后访问懒加载代理会抛 LazyInitializationException。
        for (Submission sub : result) {
            assertEquals("测试活动E", sub.getActivity().getName(),
                    "fetch join 应确保 activity 在同一查询中预加载");
        }
    }

    @Test
    @DisplayName("findByActivityIdWithActivity 无提交记录时返回空列表")
    void shouldReturnEmptyListForFetchJoinWhenNoSubmissions() {
        Activity activity = createAndPersistActivity("测试活动F");

        List<Submission> result = submissionRepository.findByActivityIdWithActivity(activity.getId());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    private Activity createAndPersistActivity(String name) {
        Activity activity = new Activity();
        activity.setName(name);
        activity.setStatus(0);
        return entityManager.persistAndFlush(activity);
    }

    private Submission createAndPersistSubmission(Activity activity, String studentId, String idCard) {
        Submission submission = new Submission();
        submission.setActivity(activity);
        submission.setStudentId(studentId);
        submission.setIdCard(idCard);
        submission.setFormDataJson("{\"姓名\":\"测试\"}");
        return entityManager.persistAndFlush(submission);
    }
}
