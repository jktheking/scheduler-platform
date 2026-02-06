plugins { `java-library` }

dependencies {
  api(project(":scheduler-service"))
  api(project(":scheduler-common"))
  implementation(libs.spring.jdbc)
   implementation(libs.slf4j.api)
}
