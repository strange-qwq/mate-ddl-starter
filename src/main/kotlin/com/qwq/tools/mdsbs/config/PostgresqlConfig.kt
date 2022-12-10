package com.qwq.tools.mdsbs.config

import com.baomidou.mybatisplus.annotation.*
import com.qwq.tools.mdsbs.annotation.Field
import com.qwq.tools.mdsbs.data.*
import org.postgresql.Driver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import java.io.File
import java.net.JarURLConnection
import javax.annotation.Resource
import kotlin.reflect.jvm.kotlinProperty

/**
 * pgsql 数据库表自动更新器
 * @author Mar
 * @date 2022.12.08 16:25
 */
@Configuration
@EnableConfigurationProperties(MateDDLConfigProperty::class)
class PostgresqlConfig {

    @Value("\${spring.datasource.url}")
    private lateinit var url: String

    @Value("\${spring.datasource.username}")
    private lateinit var username: String

    @Value("\${spring.datasource.password}")
    private lateinit var password: String

    @Resource
    private lateinit var property: MateDDLConfigProperty

    private lateinit var dataSource: SimpleDriverDataSource

    private val log = LoggerFactory.getLogger(this::class.java)

    @Bean
    fun init() {
        if(!property.enable) {
            log.info("postgresql mate ddl is disabled")
            return
        } else if(property.entity.isEmpty()) {
            log.info("postgresql mate ddl is enabled, but no entity package is set")
            return
        }
        log.info("postgresql mate ddl init")
        kotlin.runCatching {
            exec()
        }.onFailure {
            log.error("postgresql mate ddl init failed", it)
            if(property.throws) throw it
        }
    }

    /**
     * 执行
     */
    private fun exec() {
        dataSource = SimpleDriverDataSource(Driver(), url, username, password)
        val tables = getTables()
        val classLoader = Thread.currentThread().contextClassLoader
        val classSet = HashSet<Class<*>>()
        val entities = HashMap<String, TableData>()
        // 读取包下的类
        property.entity.forEach { pkg ->
            val path = pkg.replace('.', '/')
            val urls = classLoader.getResources(path)
            while(urls.hasMoreElements()) {
                urls.nextElement()?.let { url ->
                    val protocol = url.protocol
                    if(protocol == "file") {
                        val files = File(url.path).listFiles() ?: throw Exception("包名${pkg}不存在")
                        files.forEach { file ->
                            if(file.isFile && file.name.endsWith(".class")) {
                                val name = file.name.substring(0, file.name.lastIndexOf("."))
                                val clazz = Class.forName("$pkg.$name")
                                classSet.add(clazz)
                            }
                        }
                    } else if(protocol == "jar") {
                        val jar = url.openConnection() as JarURLConnection
                        val jarFile = jar.jarFile
                        val entries = jarFile.entries()
                        while(entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val name = entry.name
                            if(name.endsWith(".class")) {
                                val clazz = Class.forName(name.replace('/', '.').substring(0, name.lastIndexOf(".")))
                                classSet.add(clazz)
                            }
                        }
                    }
                }
            }
            classSet.forEach f@{ clazz ->
                val tableNameAnnotation = kotlin.runCatching { clazz.getAnnotation(TableName::class.java) }.getOrElse {
                    log.warn("实体类${clazz.name}未添加TableName注解，将跳过解析并标记为已删除")
                    return@f
                }
                val tableName = tableNameAnnotation.value
                val fieldData = clazz.declaredFields.mapNotNull { field ->
                    val tableFieldAnn = kotlin.runCatching { field.getAnnotation(TableField::class.java) }.getOrNull()
                    val fieldName = tableFieldAnn?.value?.replace("\"", "").orEmpty().ifBlank { field.name }
                    val isExist = tableFieldAnn?.exist ?: true
                    if(!isExist) return@mapNotNull null
                    val fieldAnn = kotlin.runCatching { field.getAnnotation(Field::class.java) }.getOrNull()
                    val size = fieldAnn?.size ?: 0
                    val decimal = fieldAnn?.decimal ?: 0
                    val comment = fieldAnn?.comment.orEmpty().ifBlank { fieldName }
                    val default = fieldAnn?.default.orEmpty()
                    var type = fieldAnn?.type ?: TypeEnum.DEFAULT
                    var hasNull = fieldAnn?.hasNull ?: TypeEnum.DEFAULT
                    var primary = fieldAnn?.primary ?: TypeEnum.DEFAULT
                    if(type == TypeEnum.DEFAULT || hasNull == TypeEnum.DEFAULT) {
                        val value = field.kotlinProperty?.returnType?.toString()?.substringAfterLast(".") ?: field.type.simpleName
                        type = kotlin.runCatching {
                            TypeEnum.valueOf(value.uppercase().replace("?", ""))
                        }.getOrElse { TypeEnum.ANY }
                        hasNull = if(value.contains("?")) TypeEnum.YES else TypeEnum.NO
                    }
                    if(primary == TypeEnum.DEFAULT) {
                        val isPrimary = kotlin.runCatching { field.getAnnotation(TableId::class.java) }.getOrNull() != null
                        primary = if(isPrimary) TypeEnum.YES else TypeEnum.NO
                    }
                    FieldData(
                        fieldName,
                        type.value,
                        size,
                        decimal,
                        hasNull == TypeEnum.YES,
                        primary == TypeEnum.YES,
                        default,
                        comment
                    )
                }
                val tableData = TableData(tableName, fieldData)
                entities[tableName] = tableData
            }
        }
        if(property.type != "delete") entities.keys.subtract(tables.map { it.name }.toSet()).forEach {
            log.info("表${it}不存在，将自动创建")
            execute(createTable(entities[it]!!))
        }
        when(property.type) {
            "update"        -> tables.forEach { table ->
                if(entities.keys.contains(table.name)) {
                    val entity = entities[table.name]!!
                    entity.fieldData.forEach { field ->
                        val tableField = table.fieldData.find { it.name == field.name }
                        if(tableField == null) {
                            log.info("表${table.name}缺少字段${field.name}，开始自动添加")
                            updateTable(table, entity)?.let { execute(it) }
                        } else if(field.type != tableField.type) {
                            log.info("表${table.name}字段${field.name}类型${tableField.type}与实体类${field.type}不一致，开始自动修改")
                            updateTable(table, entity)?.let { execute(it) }
                        }
                    }
                } else {
                    log.info("表${table.name}的实体已被删除，请手动前往数据库删除该表")
                }
            }
            "delete_update" -> tables.forEach { table ->
                if(entities.keys.contains(table.name)) {
                    val entity = entities[table.name]!!
                    entity.fieldData.forEach { field ->
                        val tableField = table.fieldData.find { it.name == field.name }
                        if(tableField == null) {
                            log.info("表${table.name}缺少字段${field.name}，开始删除重建")
                            execute(deleteTable(entity))
                            execute(createTable(entity))
                        } else if(field.type != tableField.type) {
                            log.info("表${table.name}字段${field.name}类型${tableField.type}与实体类${field.type}不一致，开始删除重建")
                            execute(deleteTable(entity))
                            execute(createTable(entity))
                        }
                    }
                } else {
                    log.info("表${table.name}的实体已被删除，请手动前往数据库删除该表")
                }
            }
            "delete"        -> entities.values.forEach { table ->
                log.info("表${table.name}开始删除重建")
                execute(deleteTable(table))
                execute(createTable(table))
            }
        }
    }

    /**
     * 获取表结构
     */
    private fun getTables(): List<TableData> {
        val result = ArrayList<TableData>()
        val metaData = dataSource.connection.metaData
        val tables = metaData.getTables(null, "public", "%", arrayOf("TABLE"))
        while(tables.next()) {
            val tableName = tables.getString("TABLE_NAME")
            var columns = metaData.getColumns(null, "public", tableName, "%")
            val fieldDataList = ArrayList<FieldData>()
            while(columns.next()) {
                val columnName = columns.getString("COLUMN_NAME")
                val columnType = columns.getString("TYPE_NAME")
                val columnSize = columns.getInt("COLUMN_SIZE")
                val columnDecimal = columns.getInt("DECIMAL_DIGITS")
                val columnNullable = columns.getInt("NULLABLE")
                val columnDefault = columns.getString("COLUMN_DEF")
                val columnComment = columns.getString("REMARKS")
                val fieldData = FieldData(
                    columnName,
                    columnType,
                    columnSize,
                    columnDecimal,
                    columnNullable == 0,
                    false,
                    columnDefault,
                    columnComment
                )
                fieldDataList.add(fieldData)
            }
            columns = metaData.getPrimaryKeys(null, "public", tableName)
            while(columns.next()) {
                val columnName = columns.getString("COLUMN_NAME")
                fieldDataList.find { it.name == columnName }?.primary = true
            }
            result.add(TableData(tableName, fieldDataList))
        }
        return result
    }

    /**
     * 创建表
     * @param tableData 表结构实体
     */
    private fun createTable(tableData: TableData): String {
        val createSql = StringBuilder("CREATE TABLE \"${tableData.name}\" (\n")
        val commentSql = StringBuilder()
        tableData.fieldData.forEach { field ->
            createSql.append("\t\"${field.name}\" ${field.type}")
            spliceFieldProperties(field, createSql)
            createSql.append(",\n")
            if(!field.comment.isNullOrBlank()) {
                commentSql.append("COMMENT ON COLUMN \"${tableData.name}\".\"${field.name}\" IS '${field.comment}';\n")
            }
        }
        createSql.append("\tPRIMARY KEY (")
        createSql.append(tableData.fieldData.filter { it.primary }.joinToString { "\"${it.name}\"" })
        createSql.append(")\n);\n")
        if(commentSql.isNotBlank()) createSql.append(commentSql)
        return createSql.toString()
    }

    /**
     * 更新表
     * @param tableData 原表结构实体
     * @param entity 新的表结构实体
     */
    private fun updateTable(tableData: TableData, entity: TableData): String? {
        val updateSql = StringBuilder()
        entity.fieldData.forEach { field ->
            val tableField = tableData.fieldData.find { it.name == field.name }
            if(tableField == null) {
                // 缺少字段
                updateSql.append("\tADD COLUMN \"${field.name}\" ${field.type}")
                spliceFieldProperties(field, updateSql)
                updateSql.append(",\n")
            } else if(field.type != tableField.type) {
                // 类型不一致
                if(tableField.type == "json" || tableField.type == "text") {
                    log.warn("字段${field.name}的类型为json与text，无法自动修改，已跳过")
                    return@forEach
                }
                updateSql.append("\tALTER COLUMN \"${field.name}\" TYPE ${field.type}")
                if(field.size > 0) updateSql.append("(${field.size})")
                updateSql.append(" USING \"${field.name}\"::${field.type}")
                if(field.size > 0) updateSql.append("(${field.size})")
                updateSql.append(",\n")
                // 是否可空不一致
                if(field.hasNull != tableField.hasNull) {
                    updateSql.append("\tALTER COLUMN \"${field.name}\" ")
                    if(field.hasNull) updateSql.append("DROP") else updateSql.append("SET")
                    updateSql.append(" NOT NULL,\n")
                }
                // 默认值不一致
                if(field.default != tableField.default) {
                    updateSql.append("\tALTER COLUMN \"${field.name}\" ")
                    if(field.default.isNullOrBlank()) updateSql.append("DROP") else updateSql.append("SET")
                    updateSql.append(" DEFAULT ${field.default},\n")
                }
            }
        }
        val primaryKeys = entity.fieldData.filter { it.primary }.map { it.name }
        val tablePrimaryKeys = tableData.fieldData.filter { it.primary }.map { it.name }
        if(primaryKeys != tablePrimaryKeys) {
            // 主键不一致
            updateSql.append("\tDROP CONSTRAINT \"${tableData.name}_pkey\",\n")
            updateSql.append("\tADD CONSTRAINT \"${tableData.name}_pkey\" PRIMARY KEY (${primaryKeys.joinToString{ "\"${it}\"" }}),")
        }
        return if(updateSql.isBlank()) null else {
            "ALTER TABLE \"${tableData.name}\" \n " + updateSql.replace(Regex(",\\s?\$"), "") + ";"
        }
    }

    /**
     * 删除表
     * @param tableData 表结构实体
     */
    private fun deleteTable(tableData: TableData): String {
        return "DROP TABLE \"${tableData.name}\";"
    }

    /**
     * 拼接字段属性
     * @param fieldData 字段结构实体
     * @param sql SQL 生成器
     */
    private fun spliceFieldProperties(fieldData: FieldData, sql: StringBuilder) {
        if(fieldData.size > 0) sql.append("(${fieldData.size})")
        if(!fieldData.hasNull || fieldData.primary) {
            sql.append(" NOT NULL")
        }
        if(!fieldData.default.isNullOrBlank()) sql.append(" DEFAULT ${fieldData.default}")
    }

    /**
     * 执行 SQL 语句
     * @param sql SQL
     */
    private fun execute(sql: String, doThrow: Boolean = true) {
        kotlin.runCatching {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    log.info("执行 SQL 语句：\n${sql}")
                    statement.execute(sql)
                }
            }
        }.onFailure {
            if(doThrow) throw it
            else log.error("执行 SQL 语句失败", it)
        }
    }

}