plugins { `java-library` }

dependencies {
 api(project(":scheduler-common"))
 runtimeOnly(libs.postgresql)
}
