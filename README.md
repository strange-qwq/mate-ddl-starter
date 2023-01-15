# 根据实体自动更新数据库

### 支持的数据库

- postgresql

### 依赖

- [mybatis-plus](https://github.com/baomidou/mybatis-plus)

### 使用

1. [引入依赖](https://search.maven.org/artifact/io.github.strange-qwq/mate-ddl-spring-boot-starter)
2. 在 `application.yml` 中加入配置：
   ```
   mate-ddl:
      enable: true
      type: update
      entity:
         # your entity package
         - com.example.demo.entity
   ```
3. 启动项目

### 配置项

|       配置项       | 说明          |  默认值   |
|:---------------:|-------------|:------:|
|     enable      | 是否启用        | false  |
|     throws      | 操作失败时是否阻止启动 | false  |
|      type       | 更新类型        | insert |
| defaultJsonType | JSON 字段默认类型 | jsonb  |
|     entity      | 实体包路径       |   -    |

### 配置说明

* #### throws：
  `true`：操作失败时抛出异常，阻止项目启动

  `false`：操作失败时不抛出异常，继续启动项目

* #### type：
  `insert`：仅创建不存在的表

  `update`：创建不存在的表并修改表中与实体类不同的字段，不影响原有数据(TODO)

  `delete_update`：创建不存在的表，若表存在且字段不同则直接删除该表并重建

  `delete`：每次启动项目都删除所有表并重建

* #### defaultJsonType：
  可选值：`json`，`jsonb`

* #### entity：
  实体包路径，可以配置多个

* #### @Field:
  用于标记实体类中的字段，可配置的属性有：
    * 字段名字：配置 @TableField 注解的 value 属性即可
    * 字段长度：`size`，可选，字符串类型（`char`、`varchar`）默认为255，其余类型均默认为0
    * 小数位数：`decimal`，可选，默认为`0`
    * 字段注释：`comment`，可选，默认为字段名
    * 默认赋值：`default`，可选，默认为`null`
    * 字段类型：`type`，可选，见枚举类: `com.qwq.tools.mdsbs.data.TypeEnum`
    * 字段类型：`customType`，可选，若 type 的枚举中不包含该类型，可手动指定，不为空字符串时会自动覆盖 type 字段
    * 是否可空：`hasNull`，可选（`TypeEnum.YES`：可空，`TypeEnum.NO`：不可空）
    * 是否主键：`primary`，可选（`TypeEnum.YES`：是，`TypeEnum.NO`：否）

  注：
    * 读取实体类字段时会同时读取 `@TableField` 注解，若 `exist` 为 `false` 则会忽略该字段
    * 读取实体类字段时会同时读取 `@TableId` 注解，若存在该注解，则会覆盖 `@Field.primary` 字段，使其成为主键