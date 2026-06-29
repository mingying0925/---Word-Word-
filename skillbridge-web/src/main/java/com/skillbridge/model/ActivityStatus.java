package com.skillbridge.model;

/**
 * 活动状态枚举。
 * <p>
 * 数据库 {@code activities.status} 列存储 int 值，此处提供类型安全的常量与转换方法，
 * 替代散落在 Service 层的魔法数字（0/1/2）。
 */
public enum ActivityStatus {

    /** 报名中（学生可提交） */
    ACTIVE(0),
    /** 已截止（学生不可提交，教师仍可查看/导出） */
    CLOSED(1),
    /** 草稿（创建活动待确认字段阶段） */
    DRAFT(2);

    private final int code;

    ActivityStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 判断给定 int 值是否匹配本状态 */
    public boolean matches(Integer status) {
        return status != null && status == code;
    }

    /** 由 int 值解析为枚举，未知值返回 null */
    public static ActivityStatus fromCode(Integer status) {
        if (status == null) {
            return null;
        }
        for (ActivityStatus s : values()) {
            if (s.code == status) {
                return s;
            }
        }
        return null;
    }
}
