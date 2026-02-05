plugins {
 alias(libs.plugins.spring.boot)
 alias(libs.plugins.spring.dep.mgmt)
 java
}

dependencies {

 implementation(project(":scheduler-service"))
 implementation(project(":scheduler-dao"))
 implementation(project(":scheduler-remote"))
 implementation(project(":scheduler-meter"))
 implementation(project(":scheduler-common"))

 implementation(libs.kafka.clients)
 implementation(libs.spring.boot.starter.jdbc)

 runtimeOnly(libs.postgresql)

 implementation(libs.spring.boot.starter.web)
 implementation(libs.spring.boot.starter.actuator)

}
