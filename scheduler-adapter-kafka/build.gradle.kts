plugins { `java-library` }

dependencies {
  api(project(":scheduler-service"))
  implementation(libs.kafka.clients)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.datatype.jsr310)
   implementation(libs.slf4j.api)
}
