plugins { `java-library` }

dependencies {
  api(project(":scheduler-service"))
  implementation("org.apache.kafka:kafka-clients:3.7.1")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}
