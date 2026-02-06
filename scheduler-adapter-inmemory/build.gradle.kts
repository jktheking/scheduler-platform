plugins { `java-library` }

dependencies {
  api(project(":scheduler-service"))
   implementation(libs.slf4j.api)

}
