package com.qwq.tools.mdsbs.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 配置
 * @author Mar
 * @date 2022.12.09 12:26
 */
@ConfigurationProperties("mybatis.config")
class MybatisPlusConfigProperty {

    var enable: Boolean = true

    var entityPackage: List<String> = emptyList()

}