plugins {
 alias(libs.plugins.spring.boot)
 alias(libs.plugins.spring.dep.mgmt)
 java
}

dependencies {

 implementation(project(":scheduler-service"))
 implementation(project(":scheduler-meter"))

 implementation(libs.spring.boot.starter.web)
 implementation(libs.spring.boot.starter.actuator)

}
