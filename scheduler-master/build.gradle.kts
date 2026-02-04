plugins {
 alias(libs.plugins.spring.boot)
 alias(libs.plugins.spring.dep.mgmt)
 java
}

dependencies {


 implementation(project(":scheduler-service"))
 implementation(project(":scheduler-domain"))
 implementation(project(":scheduler-dao"))
 implementation(project(":scheduler-remote"))
 implementation(project(":scheduler-meter"))


 implementation(libs.spring.boot.starter.web)
 implementation(libs.spring.boot.starter.actuator)
 implementation(libs.kafka.clients)

 implementation("org.springframework.boot:spring-boot-starter-jdbc")
 implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
 implementation("io.opentelemetry:opentelemetry-api:1.43.0")

 runtimeOnly(libs.postgresql)

 testImplementation(platform("org.junit:junit-bom:5.11.3"))
 testImplementation("org.junit.jupiter:junit-jupiter")
}
