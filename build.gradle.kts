plugins {
 alias(libs.plugins.spring.boot) apply false
 alias(libs.plugins.spring.dep.mgmt) apply false
 alias(libs.plugins.protobuf) apply false
}

allprojects {
 group = "com.acme.scheduler"
 version = "0.0.1-SNAPSHOT"

 repositories {
 mavenCentral()
 }
}

subprojects {
// Eclipse/ STS support
 apply(plugin = "eclipse")

 plugins.withId("java") {
 extensions.configure<JavaPluginExtension> {
 toolchain {
 languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
 }
 }
 tasks.withType<JavaCompile>().configureEach {
 options.encoding = "UTF-8"
 options.release.set(libs.versions.java.get().toInt())
 }
 tasks.withType<Test>().configureEach {
 useJUnitPlatform()
 }
 }
}
