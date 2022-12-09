plugins {
    signing
    `maven-publish`
    id("org.springframework.boot") version "2.7.6"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.7.21"
    kotlin("plugin.spring") version "1.7.21"
}

group = "com.github.strange-qwq"
version = "0.0.9"

repositories {
    maven {
        isAllowInsecureProtocol = true
        url = uri("https://maven.aliyun.com/nexus/content/groups/public")
    }
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.postgresql:postgresql")
    implementation("com.baomidou:mybatis-plus-boot-starter:3.5.2")

    implementation("org.springframework.boot:spring-boot-starter")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

val sonatypeUsername: String by project
val sonatypePassword: String by project

publishing {
    repositories {
        maven {
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            credentials {
                username = sonatypeUsername
                password = sonatypePassword
            }
        }
    }

    signing {
        // 设置对生成文件进行签名
        sign(publishing.publications)
    }
}

