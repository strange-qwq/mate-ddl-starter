package com.qwq.tools.mdsbs.config

/**
 * 配置
 * @author QWQ
 * @date 2022.12.09 12:26
 */
data class MateDDLConfigProperty(
    /**
     * 是否启用
     */
    var enable: Boolean,
    /**
     * 操作失败时是否阻止启动（即抛出异常）
     */
    var throws: Boolean,
    /**
     * JSON 字段默认类型
     * - json
     * - jsonb
     */
    var defaultJsonType: String,
    /**
     * 注册类型
     * - insert：仅创建不存在的表
     * - update：创建不存在的表并修改表中与实体类不同的字段，不影响原有数据
     * - delete_update：创建不存在的表，若表存在且字段不同则直接删除该表并重建
     * - delete：每次启动项目都删除所有表并重建
     */
    var type: String,
    /**
     * 表实体类所在的包
     */
    var entity: List<String>
)