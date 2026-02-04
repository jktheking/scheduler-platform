plugins { `java-library` }

dependencies {
 api(project(":scheduler-common"))

 testImplementation(platform("org.junit:junit-bom:5.11.3"))
 testImplementation("org.junit.jupiter:junit-jupiter")

 // Gradle 9+ may require the JUnit Platform launcher on the test runtime classpath.
 testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
