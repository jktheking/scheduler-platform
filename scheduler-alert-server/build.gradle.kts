plugins {
 alias(libs.plugins.spring.boot)
 alias(libs.plugins.spring.dep.mgmt)
 java
}

springBoot {
  // There are two @SpringBootApplication entry points in this module.
  // Pick the server application as the default runnable main.
  mainClass.set("com.acme.scheduler.alert.AlertServerApplication")
}

dependencies {

 implementation(project(":scheduler-service"))
 implementation(project(":scheduler-meter"))
 implementation(project(":scheduler-common"))

 implementation(libs.kafka.clients)

 implementation(libs.spring.boot.starter.web)
 implementation(libs.spring.boot.starter.actuator)

}
