plugins { `java-library` }

dependencies{ 
 implementation(libs.slf4j.api)
 api(project(":scheduler-common"))

 testImplementation(platform(libs.junit.bom))
 testImplementation(libs.junit.jupiter)

 // Gradle 9+ may require the JUnit Platform launcher on the test runtime classpath.
 testRuntimeOnly(libs.junit.platform.launcher)
}
