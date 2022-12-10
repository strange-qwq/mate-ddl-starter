package com.qwq.tools.mdsbs.data

/**
 * 字段结构实体
 * @author Mar
 * @date 2022.12.09 15:06
 *
 * @property name 字段名
 * @property type 字段类型
 * @property size 字段长度
 * @property decimal 小数位数
 * @property hasNull 是否可空
 * @property primary 是否是主键
 * @property default 默认值
 * @property comment 字段注释
 */
data class FieldData (
    var name: String,
    var type: String,
    var size: Int,
    var decimal: Int,
    var hasNull: Boolean,
    var primary: Boolean,
    var default: String?,
    var comment: String?
) {
    init {
        name = name.replace(Regex("[A-Z]")) { "_${it.value.lowercase()}" }
    }
}