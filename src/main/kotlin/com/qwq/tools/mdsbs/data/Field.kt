package com.qwq.tools.mdsbs.data

/**
 * 字段结构实体
 * @author Mar
 * @date 2022.12.09 15:06
 *
 * @property name 字段名
 * @property type 字段类型
 * @property size 字段长度
 * @property hasNull 是否可空
 * @property isPrimary 是否是主键
 * @property default 默认值
 */
data class Field (
    var name: String,
    var type: String,
    var size: Int,
    var hasNull: Boolean,
    var isPrimary: Boolean,
    var default: String?
) {
    init {
        name = name.replace(Regex("[A-Z]")) { "_${it.value.lowercase()}" }
    }
}