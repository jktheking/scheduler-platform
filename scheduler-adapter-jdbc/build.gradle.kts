plugins { `java-library` }

dependencies {
  api(project(":scheduler-service"))
  implementation("org.springframework:spring-jdbc:6.1.14")
}
