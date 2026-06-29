package com.skillbridge.service;

import com.skillbridge.model.AuditLog;
import com.skillbridge.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计日志服务。
 * <p>
 * 记录关键操作（登录、活动增删改、导出等），支持按操作人/操作类型查询。
 * <p>
 * 日志写入采用 REQUIRES_NEW 传播行为，独立事务，避免主业务回滚导致日志丢失。
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 记录审计日志。
     *
     * @param operator      操作人工号
     * @param operatorRole  操作人角色：teacher/student/system
     * @param action        操作类型
     * @param targetType    操作对象类型（可空）
     * @param targetId      操作对象 ID（可空）
     * @param detail        操作详情（可空）
     * @param ip            操作来源 IP（可空）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String operator, String operatorRole, String action,
                       String targetType, String targetId, String detail, String ip) {
        try {
            AuditLog entry = new AuditLog();
            entry.setOperator(operator == null ? "unknown" : operator);
            entry.setOperatorRole(operatorRole == null ? "system" : operatorRole);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(detail);
            entry.setIp(ip);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // 审计日志写入失败不应影响主业务流程
            log.warn("审计日志写入失败：operator={}, action={}, error={}",
                    operator, action, e.getMessage());
        }
    }

    /**
     * 分页查询审计日志（按时间倒序）。
     */
    public Page<AuditLog> getLogs(int page, int size) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    /**
     * 按操作人分页查询。
     */
    public Page<AuditLog> getLogsByOperator(String operator, int page, int size) {
        return auditLogRepository.findByOperatorOrderByCreatedAtDesc(operator,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    /**
     * 按操作类型分页查询。
     */
    public Page<AuditLog> getLogsByAction(String action, int page, int size) {
        return auditLogRepository.findByActionOrderByCreatedAtDesc(action,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }
}
