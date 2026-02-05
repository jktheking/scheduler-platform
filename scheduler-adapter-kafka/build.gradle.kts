plugins { `java-library` }

dependencies {
  api(project(":scheduler-service"))
  implementation(libs.kafka.clients)
  implementation(libs.jackson.databind)
}
