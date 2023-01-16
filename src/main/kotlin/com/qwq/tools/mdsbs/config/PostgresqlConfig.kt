package com.qwq.tools.mdsbs.config

import com.baomidou.mybatisplus.annotation.*
import com.qwq.tools.mdsbs.annotation.Field
import com.qwq.tools.mdsbs.data.*
import org.postgresql.Driver
import org.slf4j.LoggerFactory
import org.springframework.boot.env.OriginTrackedMapPropertySource
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import java.io.File
import java.net.JarURLConnection
import java.net.URLDecoder
import kotlin.reflect.jvm.kotlinProperty
import kotlin.text.Charsets.UTF_8

/**
 * pgsql 数据库表自动更新器
 * @author QWQ
 * @date 2022.12.08 16:25
 */
class PostgresqlConfig: ApplicationContextInitializer<ConfigurableApplicationContext> {

    private lateinit var url: String

    private lateinit var username: String

    private lateinit var password: String

    private lateinit var property: MateDDLConfigProperty

    private lateinit var dataSource: SimpleDriverDataSource

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val env = applicationContext.environment
        val sources = env.propertySources.find { it is OriginTrackedMapPropertySource }
        // 读取实体类包名（如果有更好的读取方式，欢迎PR）
        val entityPackage = ArrayList<String>()
        var index = 0
        while (true) {
            val key = sources?.getProperty("mate-ddl.entity[${index}]")?.toString() ?: break
            entityPackage.add(key)
            index++
        }
        property = MateDDLConfigProperty(
            env.getProperty("mate-ddl.enable", Boolean::class.java, false),
            env.getProperty("mate-ddl.throws", Boolean::class.java, false),
            env.getProperty("mate-ddl.default-json-type", "jsonb"),
            env.getProperty("mate-ddl.type", "insert"),
            entityPackage,
        )
        if(!property.enable) {
            log.info("postgresql mate ddl is disabled")
            return
        } else if(property.entity.isEmpty()) {
            log.info("postgresql mate ddl is enabled, but no entity package is set")
            return
        }
        log.info("postgresql mate ddl init")
        kotlin.runCatching {
            url = applicationContext.environment.getProperty("spring.datasource.url").orEmpty()
            username = applicationContext.environment.getProperty("spring.datasource.username").orEmpty()
            password = applicationContext.environment.getProperty("spring.datasource.password").orEmpty()
            exec()
        }.onFailure {
            log.error("postgresql mate ddl init failed", it)
            it.printStackTrace()
            if(property.throws) applicationContext.close()
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
                        log.info("postgresql mate ddl read entity from file: ${URLDecoder.decode(url.path, UTF_8)}")
                        val files = File(URLDecoder.decode(url.path, UTF_8)).listFiles() ?: throw Exception("包名${pkg}不存在")
                        files.forEach { file ->
                            if(file.isFile && file.name.endsWith(".class")) {
                                val name = file.name.substring(0, file.name.lastIndexOf("."))
                                val clazz = Class.forName("$pkg.$name")
                                classSet.add(clazz)
                            }
                        }
                    } else if(protocol == "jar") {
                        log.info("postgresql mate ddl read entity from jar")
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
                val tableNameAnnotation = kotlin.runCatching { clazz.getAnnotation(TableName::class.java) }.getOrNull()
                if(tableNameAnnotation == null) {
                    log.warn("实体类${clazz.name}未添加TableName注解，将跳过解析并标记为已删除")
                    return@f
                }
                var tableName = kotlin.runCatching { tableNameAnnotation.value }.getOrElse { "" }
                if(tableName.isBlank()) tableName = clazz.simpleName
                val fieldData = clazz.declaredFields.mapNotNull { field ->
                    val tableIdAnn = kotlin.runCatching { field.getAnnotation(TableId::class.java) }.getOrNull()
                    val tableFieldAnn = kotlin.runCatching { field.getAnnotation(TableField::class.java) }.getOrNull()
                    val fieldName = tableFieldAnn?.value?.replace("\"", "").orEmpty().ifBlank { field.name }
                    val isExist = tableFieldAnn?.exist ?: true
                    if(!isExist) return@mapNotNull null
                    val fieldAnn = kotlin.runCatching { field.getAnnotation(Field::class.java) }.getOrNull()
                    var size = fieldAnn?.size ?: 0
                    var type =
                        if(fieldAnn?.customType.isNullOrBlank()) fieldAnn?.type?.value.orEmpty()
                        else fieldAnn?.customType.orEmpty()
                    var hasNull = fieldAnn?.hasNull ?: TypeEnum.DEFAULT
                    var primary: TypeEnum = TypeEnum.NO
                    if(fieldAnn?.primary == TypeEnum.YES || tableIdAnn != null) primary = TypeEnum.YES
                    val fieldType = field.kotlinProperty?.returnType?.toString()?.substringAfterLast(".") ?: field.type.simpleName
                    if(type.isBlank()) type = when(fieldType.replace("?", "")) {
                        "String" -> TypeEnum.VARCHAR.value
                        "Boolean" -> TypeEnum.BOOL.value
                        "Byte" -> TypeEnum.INT2.value
                        "Short", "Int" -> TypeEnum.INT4.value
                        "Long" -> TypeEnum.INT8.value
                        "Float" -> TypeEnum.FLOAT4.value
                        "Double" -> TypeEnum.FLOAT8.value
                        "Date", "LocalDate", "LocalDateTime", "LocalTime" -> TypeEnum.TIMESTAMP.value
                        else -> kotlin.runCatching {
                            TypeEnum.valueOf(property.defaultJsonType.uppercase()).value
                        }.getOrElse {
                            throw IllegalArgumentException("非法的 JSON 字段默认类型：${property.defaultJsonType}")
                        }
                    }
                    if(hasNull == TypeEnum.DEFAULT) hasNull = if(fieldType.contains("?")) TypeEnum.YES else TypeEnum.NO
                    if(type == TypeEnum.CHAR.value && size == 0) size = 255
                    if(type == TypeEnum.VARCHAR.value && size == 0) size = 255
                    FieldData(
                        fieldName,
                        type,
                        size,
                        fieldAnn?.decimal ?: 0,
                        hasNull == TypeEnum.YES,
                        primary == TypeEnum.YES,
                        fieldAnn?.default,
                        fieldAnn?.comment.orEmpty().ifBlank { fieldName }
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