import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.api.tasks.compile.JavaCompile

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
    // Import Spring Boot BOM for consistent dependency versions in ALL Java modules
    dependencies {
      add(
        "implementation",
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
      )
      add(
        "testImplementation",
        platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
      )
      
      // Provide a consistent, Spring-managed test stack (JUnit Jupiter + Mockito + AssertJ + Spring Test, etc.)
      add("testImplementation", "org.springframework.boot:spring-boot-starter-test")

      // Gradle 9+ needs the JUnit Platform launcher on the *runtime* classpath when using useJUnitPlatform().
      // Some setups do not pull this transitively, so keep it explicit to avoid
      // "Failed to load JUnit Platform launcher" errors.
      add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
     
     add("implementation", enforcedPlatform(libs.grpc.bom))
     add("testImplementation", enforcedPlatform(libs.grpc.bom))
    
    }

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