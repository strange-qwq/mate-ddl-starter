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

    VARCHAR("varchar"),

    INT2("int2"),

    INT4("int4"),

    INT8("int8"),

    FLOAT2("float2"),

    FLOAT4("float4"),

    FLOAT8("float8"),

    BOOL("bool"),

    TIMESTAMP("timestamp"),

    JSON("json"),

    JSONB("jsonb"),

    YES("1"),

    NO("0")

}