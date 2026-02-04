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

 implementation("org.springframework.boot:spring-boot-starter-jdbc")
 implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
 // OTel API comes transitively from :scheduler-meter (centralized).

 implementation(libs.springdoc.openapi.ui)
 runtimeOnly(libs.postgresql)

 testImplementation(platform("org.junit:junit-bom:5.11.3"))
 testImplementation("org.junit.jupiter:junit-jupiter")
}

 