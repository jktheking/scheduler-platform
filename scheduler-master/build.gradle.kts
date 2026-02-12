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

 // Optional (disabled by default): ETCD leader election for MASTER role HA.
 // Keeping it as a normal dependency ensures compilation in offline environments.
implementation(libs.jetcd.core)

 // OTel API comes transitively from :scheduler-meter


 runtimeOnly(libs.postgresql)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)

}
