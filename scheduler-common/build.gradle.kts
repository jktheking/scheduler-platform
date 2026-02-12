plugins { `java-library` }

dependencies {
 api(libs.jackson.datatype.jsr310)
  implementation(libs.slf4j.api)

  testImplementation("org.springframework.boot:spring-boot-starter-test")
}
