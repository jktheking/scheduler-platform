import com.google.protobuf.gradle.*

plugins {
  `java-library`
  alias(libs.plugins.protobuf)
}

dependencies {
  api(libs.grpc.protobuf)
  api(libs.grpc.stub)
  runtimeOnly(libs.grpc.netty)
  api(libs.protobuf.java)

  // Java 9+ no longer bundles javax.annotation.*; gRPC generated stubs still reference it.
  // Use compileOnly so it doesn't leak into consumers.
  compileOnly(libs.javax.annotation.api)
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
  }

  plugins {
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
    }
  }

  generateProtoTasks {
    all().configureEach {
      plugins {
        id("grpc")
      }
    }
  }
}
