plugins {
    signing
    `maven-publish`
    id("org.springframework.boot") version "2.7.6"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.7.21"
    kotlin("plugin.spring") version "1.7.21"
}

group = "io.github.strange-qwq"
version = "0.1"

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

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("mate-ddl-boot-starter")
                description.set("根据实体自动更新数据库")
                url.set("https://github.com/strange-qwq/mate-ddl-starter")
                developers {
                    developer {
                        name.set("QWQ")
                        email.set("1456158721@qq.com")
                    }
                }
                scm {
                    url.set("https://github.com/strange-qwq/mate-ddl-starter")
                    connection.set("scm:git:https://github.com/strange-qwq/mate-ddl-starter.git")
                    developerConnection.set("scm:git:https://github.com/strange-qwq/mate-ddl-starter.git")
                }
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "mate-ddl-boot-starter"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = sonatypeUsername
                password = sonatypePassword
            }
        }
    }

}

signing {
    sign(publishing.publications)
}

