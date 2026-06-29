package com.skillbridge.repository;

import com.skillbridge.model.ExportTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExportTaskRepository extends JpaRepository<ExportTask, Long> {

    /** 按操作人查询任务（按创建时间倒序） */
    List<ExportTask> findByOperatorOrderByCreatedAtDesc(String operator);
}
