package com.skillbridge.repository;

import com.skillbridge.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** 分页查询所有审计日志（按时间倒序） */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 按操作人分页查询 */
    Page<AuditLog> findByOperatorOrderByCreatedAtDesc(String operator, Pageable pageable);

    /** 按操作类型分页查询 */
    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
}
