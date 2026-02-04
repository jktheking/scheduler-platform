plugins { `java-library` }

dependencies {
  api(project(":scheduler-service"))

  testImplementation(platform("org.junit:junit-bom:5.11.3"))
  testImplementation("org.junit.jupiter:junit-jupiter")
}
