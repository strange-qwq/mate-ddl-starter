package com.qwq.tools.mdsbs.data

/**
 * 表结构实体
 * @author Mar
 * @date 2022.12.09 15:07
 *
 * @property name 表名
 * @property fieldData 表字段
 */
data class TableData(val name: String, val fieldData: List<FieldData>)