package com.qwq.tools.mdsbs.config

import com.baomidou.mybatisplus.annotation.*
import org.postgresql.Driver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import java.io.File
import java.net.JarURLConnection
import javax.annotation.PostConstruct
import kotlin.reflect.jvm.kotlinProperty

/**
 * pgsql 数据库表自动更新器
 * @author QWQ
 * @date 2022.12.08 16:25
 */
@Configuration
class PostgresqlConfig {

    /**
     * Entity 包名
     */
    private val packages = setOf(
        "com.soiiy.beta.prod.data.module",
        "com.soiiy.beta.prod.data.entity",
        "com.soiiy.beta.prod.data.entity.item"
    )

    /**
     * 数据库中长度为 25 的字段
     */
    private val size25Field = listOf(
        "type",
        "state",
        "status"
    )

    /**
     * 数据库中长度为 50 的字段
     */
    private val size50Fields = listOf(
        "id",
        "pid",
        "project",
        "subject",
        "trade_user",
        "trade_user_raw",
        "trade_no",
        "trade_out",
        "code",
        "order",
        "refund"
    )

    /**
     * 数据库中存 text 的字段
     */
    private val sizeTextFields = listOf(
        "data",
        "preview",
        "content"
    )

    @Value("\${spring.datasource.url}")
    private lateinit var url: String

    @Value("\${spring.datasource.username}")
    private lateinit var username: String

    @Value("\${spring.datasource.password}")
    private lateinit var password: String

    private lateinit var dataSource: SimpleDriverDataSource

    private val log = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    private fun init() {
        dataSource = SimpleDriverDataSource(Driver(), url, username, password)
        val tables = getTables()
        val classLoader = Thread.currentThread().contextClassLoader
        val classSet = HashSet<Class<*>>()
        val entities = HashMap<String, Table>()
        packages.forEach { pkg ->
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
                val fields = clazz.declaredFields.mapNotNull {
                    val isExist = kotlin.runCatching { it.getAnnotation(TableField::class.java) }.getOrNull()?.exist ?: true
                    if(!isExist) return@mapNotNull null
                    val isPrimary = kotlin.runCatching { it.getAnnotation(TableId::class.java) }.getOrNull() != null
                    val type = it.kotlinProperty?.returnType?.toString()?.substringAfterLast(".") ?: it.type.simpleName
                    val hasNull = type.contains("?")
                    var size = 0
                    if(size25Field.contains(it.name)) size = 25
                    else if(size50Fields.contains(it.name)) size = 50
                    when(type.replace("?", "")) {
                        "String"                             -> {
                            val t = if(sizeTextFields.contains(it.name)) "text" else "varchar"
                            Field(it.name, t, size, hasNull, isPrimary, null)
                        }
                        "Int"                                -> Field(it.name, "int4", 0, hasNull, isPrimary, null)
                        "Long"                               -> Field(it.name, "int8", 0, hasNull, isPrimary, null)
                        "Float"                              -> Field(it.name, "float4", 0, hasNull, isPrimary, null)
                        "Double"                             -> Field(it.name, "float8", 0, hasNull, isPrimary, null)
                        "Boolean"                            -> Field(it.name, "bool", 0, hasNull, isPrimary, null)
                        "Date", "LocalDate", "LocalDateTime" -> Field(it.name, "timestamp", 6, hasNull, isPrimary, null)
                        else                                 -> Field(it.name, "json", 0, hasNull, isPrimary, null)
                    }
                }
                val table = Table(tableName, fields)
                entities[tableName] = table
            }
        }
        entities.keys.subtract(tables.map { it.name }.toSet()).forEach {
            log.info("表${it}不存在，将自动创建")
            execute(createTable(entities[it]!!))
        }
        tables.forEach { table ->
            if(entities.keys.contains(table.name)) {
                val entity = entities[table.name]!!
                entity.fields.forEach { field ->
                    val tableField = table.fields.find { it.name == field.name }
                    if(tableField == null) {
                        log.info("表${table.name}缺少字段${field.name}，开始自动添加")
                        updateTable(table, entity)?.let { execute(it) }
                    } else if(field.type != tableField.type) {
                        log.info("表${table.name}字段${field.name}类型${tableField.type}与实体类${field.type}不一致，开始自动修改")
                        updateTable(table, entity)?.let { execute(it) }
                    }
                }
                // log.info("表${table.name}已存在实体，检查字段...")
            } else {
                log.info("表${table.name}的实体已被删除，请手动前往数据库删除该表")
            }
        }
    }

    /**
     * 获取表结构
     */
    private fun getTables(): List<Table> {
        val result = ArrayList<Table>()
        val metaData = dataSource.connection.metaData
        val tables = metaData.getTables(null, "public", "%", arrayOf("TABLE"))
        while(tables.next()) {
            val tableName = tables.getString("TABLE_NAME")
            var columns = metaData.getColumns(null, "public", tableName, "%")
            val fields = ArrayList<Field>()
            while(columns.next()) {
                val columnName = columns.getString("COLUMN_NAME")
                val columnType = columns.getString("TYPE_NAME")
                val columnSize = columns.getInt("COLUMN_SIZE")
                val columnNullable = columns.getInt("NULLABLE")
                val columnDefault = columns.getString("COLUMN_DEF")
                val field = Field(columnName, columnType, columnSize, columnNullable == 0, false, columnDefault)
                fields.add(field)
            }
            columns = metaData.getPrimaryKeys(null, "public", tableName)
            while(columns.next()) {
                val columnName = columns.getString("COLUMN_NAME")
                fields.find { it.name == columnName }?.isPrimary = true
            }
            result.add(Table(tableName, fields))
        }
        return result
    }

    /**
     * 创建表
     * @param table 表结构实体
     */
    private fun createTable(table: Table): String {
        val createSql = StringBuilder("CREATE TABLE \"${table.name}\" (\n")
        table.fields.forEach { field ->
            createSql.append("\t\"${field.name}\" ${field.type}")
            spliceFieldProperties(field, createSql)
            createSql.append(",\n")
        }
        createSql.append("\tPRIMARY KEY (")
        createSql.append(table.fields.filter { it.isPrimary }.joinToString(prefix = "\"", postfix = "\"") { it.name })
        createSql.append(")\n);\nALTER TABLE \"${table.name}\" OWNER TO \"postgres\";")
        return createSql.toString()
    }

    /**
     * 更新表结构
     * @param table 原表结构实体
     * @param entity 新的表结构实体
     */
    private fun updateTable(table: Table, entity: Table): String? {
        val updateSql = StringBuilder()
        entity.fields.forEach { field ->
            val tableField = table.fields.find { it.name == field.name }
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
        val primaryKeys = entity.fields.filter { it.isPrimary }.map { it.name }
        val tablePrimaryKeys = table.fields.filter { it.isPrimary }.map { it.name }
        if(primaryKeys != tablePrimaryKeys) {
            // 主键不一致
            updateSql.append("\tDROP CONSTRAINT \"${table.name}_pkey\",\n")
            updateSql.append("\tADD CONSTRAINT \"${table.name}_pkey\" PRIMARY KEY (${primaryKeys.joinToString(prefix = "\"", postfix = "\"")}),")
        }
        return if(updateSql.isBlank()) null else {
            "ALTER TABLE \"${table.name}\" \n " + updateSql.replace(Regex(",\\s?\$"), "") + ";"
        }
    }

    /**
     * 拼接字段属性
     * @param field 字段结构实体
     * @param sql SQL 生成器
     */
    private fun spliceFieldProperties(field: Field, sql: StringBuilder) {
        if(field.size > 0) sql.append("(${field.size})")
        if(!field.hasNull) {
            sql.append(" NOT NULL")
            if(!field.default.isNullOrBlank()) sql.append(" DEFAULT ${field.default}")
        }
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

    /**
     * 表结构实体
     * @property name 表名
     * @property fields 表字段
     */
    private data class Table(val name: String, val fields: List<Field>)

    /**
     * 字段结构实体
     * @property name 字段名
     * @property type 字段类型
     * @property size 字段长度
     * @property hasNull 是否可空
     * @property isPrimary 是否是主键
     * @property default 默认值
     */
    private data class Field (
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

}