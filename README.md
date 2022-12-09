# 根据实体自动更新数据库

### 支持的数据库

- postgresql

### 依赖

- [mybatis-plus](https://github.com/baomidou/mybatis-plus)

### 使用

1. 引入依赖
    - maven
    ```
    爷忘了
    ```
    - gradle
    ```
    implementation("io.github.strange-qwq:mate-ddl-boot-starter:0.1")
    ```
2. 在 `application.yml` 中加入配置：
   ```
   mate-ddl:
      enable: true
      type: update
      entityPackage:
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

  `update`：创建不存在的表并修改表中与实体类不同的字段，不影响原有数据

  `delete_update`：创建不存在的表，若表存在且字段不同则直接删除该表并重建(TODO)

  `delete`：每次启动项目都删除所有表并重建(TODO)