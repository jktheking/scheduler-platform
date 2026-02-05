import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

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
  // Eclipse / STS support
  apply(plugin = "eclipse")

  // Make Gradle-generated Eclipse/STS classpath nicer:
  // - downloadSources/downloadJavadoc helps debugging inside Eclipse
  // - allows `./gradlew eclipse` to regenerate .classpath/.project per module
  extensions.configure<org.gradle.plugins.ide.eclipse.model.EclipseModel> {
    classpath {
      // Kotlin DSL: use the boolean properties, not Groovy-style fields
      isDownloadSources = true
      isDownloadJavadoc = true
    }
  }

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
