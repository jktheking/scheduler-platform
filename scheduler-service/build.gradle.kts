plugins { `java-library` }

dependencies {
 api(project(":scheduler-meter"))
 api(project(":scheduler-common"))
 api(project(":scheduler-spi"))
 api(project(":scheduler-domain"))
 api(libs.jackson.databind)
 implementation(libs.slf4j.api)
 
 testImplementation("org.springframework.boot:spring-boot-starter-test")

}