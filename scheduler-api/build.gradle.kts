plugins {
 alias(libs.plugins.spring.boot)
 alias(libs.plugins.spring.dep.mgmt)
 java
}

dependencies {

 implementation(project(":scheduler-service"))
 implementation(project(":scheduler-dao"))
 implementation(project(":scheduler-adapter-inmemory"))
 implementation(project(":scheduler-adapter-kafka"))
 implementation(project(":scheduler-adapter-jdbc"))
 implementation(project(":scheduler-domain"))
 implementation(project(":scheduler-meter"))

 implementation(libs.spring.boot.starter.web)
 implementation(libs.spring.boot.starter.validation)
 implementation(libs.spring.boot.starter.actuator)

 implementation(libs.spring.boot.starter.jdbc)
 // Jackson comes transitively from spring-boot-starter-web

 // OTel API comes transitively from :scheduler-meter (centralized).

 implementation(libs.springdoc.openapi.ui)
 runtimeOnly(libs.postgresql)

}

 