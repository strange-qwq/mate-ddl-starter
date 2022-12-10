package com.qwq.tools.mdsbs.data

/**
 * 字段类型枚举
 * @author Mar
 * @date 2022.12.10 19:11
 */
enum class TypeEnum(val value: String) {

    // 默认/自动推断
    DEFAULT(""),

    CHAR("char"),

    STRING("varchar"),

    INT("int4"),

    LONG("int8"),

    FLOAT("float4"),

    DOUBLE("float8"),

    BOOLEAN("bool"),

    DATE("timestamp"),

    LOCALDATE("timestamp"),

    LOCALDATETIME("timestamp"),

    ANY("json"),

    YES("1"),

    NO("0")

}