rootProject.name = "scheduler-platform"

include(
 "scheduler-common",
 "scheduler-domain",
 "scheduler-spi",
 "scheduler-remote",
 "scheduler-meter",
 "scheduler-service",
 "scheduler-dao",
 "scheduler-api",
 "scheduler-master",
 "scheduler-worker",
 "scheduler-alert-server",
 "scheduler-adapter-inmemory",
 "scheduler-adapter-kafka",
 "scheduler-adapter-jdbc")
