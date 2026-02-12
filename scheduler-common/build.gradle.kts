plugins { `java-library` }

dependencies {
 api(libs.jackson.datatype.jsr310)
  implementation(libs.slf4j.api)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}
