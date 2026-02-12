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
 
  // Testing (versions managed via Spring Boot BOM).
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  // Explicitly include Boot's test support artifacts used by @WebMvcTest/@MockBean
  // to avoid missing-class issues if a build/customization prunes transitive deps.
  testImplementation("org.springframework.boot:spring-boot-test")
  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")

}

 