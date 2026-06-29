package com.skillbridge.repository;

import com.skillbridge.model.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

    /** 按名称模糊搜索模板 */
    List<Template> findByNameContaining(String keyword);

    /** 按归属教师查询全部模板 */
    List<Template> findByOwnerId(String ownerId);

    /** 按归属教师 + 名称模糊搜索模板 */
    List<Template> findByOwnerIdAndNameContaining(String ownerId, String keyword);

}
