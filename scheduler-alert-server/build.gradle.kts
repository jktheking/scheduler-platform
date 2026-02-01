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

 testImplementation(platform("org.junit:junit-bom:5.11.3"))
 testImplementation("org.junit.jupiter:junit-jupiter")
}
