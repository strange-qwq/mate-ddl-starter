package com.qwq.tools.mdsbs.annotation

import com.qwq.tools.mdsbs.data.TypeEnum
import org.springframework.stereotype.Component

/**
 * 字段注解
 * @author Mar
 * @date 2022.12.10 18:48
 *
 * @property size 字段长度
 * @property decimal 小数点
 * @property comment 字段注释（默认为字段名）
 * @property default 默认值（默认为空字符串）
 * @property type 字段类型（默认自动推断）
 * @property customType 字段类型（若 type 的枚举中不包含该类型，可手动指定，不为空时会自动覆盖 type 字段）
 * @property hasNull 是否可空（TypeEnum.DEFAULT：自动推断，TypeEnum.YES：可空，TypeEnum.NO：不可空）
 * @property primary 是否主键（TypeEnum.DEFAULT：自动推断，TypeEnum.YES：主键，TypeEnum.NO：否）
 */
@Component
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Field(
    val size: Int = 0,
    val decimal: Int = 0,
    val comment: String = "",
    val default: String = "",
    val type: TypeEnum = TypeEnum.DEFAULT,
    val customType: String = "",
    val hasNull: TypeEnum = TypeEnum.DEFAULT,
    val primary: TypeEnum = TypeEnum.DEFAULT
)
