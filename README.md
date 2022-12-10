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

|  配置项   | 说明          |  默认值   |
|:------:|-------------|:------:|
| enable | 是否启用        | false  |
| throws | 操作失败时是否阻止启动 | false  |
|  type  | 更新类型        | insert |
| entity | 实体包路径       |   -    |

### 配置说明

* #### type：
  `insert`：仅创建不存在的表

  `update`：创建不存在的表并修改表中与实体类不同的字段，不影响原有数据(TODO)

  `delete_update`：创建不存在的表，若表存在且字段不同则直接删除该表并重建

  `delete`：每次启动项目都删除所有表并重建

* #### throws：
  `true`：操作失败时抛出异常，阻止项目启动

  `false`：操作失败时不抛出异常，继续启动项目

* #### entity：
  实体包路径，可以配置多个

* #### @Field:
  用于标记实体类中的字段
    * 字段名字：配置 @TableField 注解的 value 属性即可
    * 字段长度：`size`，默认为0
    * 小数位数：`decimal`，默认为0
    * 字段注释：`comment`，默认为字段名
    * 默认赋值：`default`，默认为空字符串
    * 字段类型：`type`，不传则自动推断，可选值见 `com.qwq.tools.mdsbs.data.TypeEnum`
    * 是否可空：`hasNull`，不传则自动推断（可选值：`TypeEnum.DEFAULT`：自动推断，`TypeEnum.YES`：可空，`TypeEnum.NO`：不可空）
    * 是否主键：`primary`，配置 @TableId 注解或该值为 `TypeEnum.YES` 时将其注册为主键（可选值：`TypeEnum.DEFAULT`
      ：自动推断，`TypeEnum.YES`：是，`TypeEnum.NO`：否）