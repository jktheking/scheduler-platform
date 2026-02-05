plugins { `java-library` }

dependencies {
  api(project(":scheduler-service"))
  implementation(libs.spring.jdbc)
}
