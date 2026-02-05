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
 implementation(project(":scheduler-common"))


 implementation(libs.spring.boot.starter.web)
 implementation(libs.spring.boot.starter.actuator)
 implementation(libs.kafka.clients)

 implementation(libs.spring.boot.starter.jdbc)
 // Jackson comes transitively from spring-boot-starter-web

 // OTel API comes transitively from :scheduler-meter


 runtimeOnly(libs.postgresql)

}
