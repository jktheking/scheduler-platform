package com.acme.scheduler.master;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MasterApplication {
  public static void main(String[] args) {
    SpringApplication.run(MasterApplication.class, args);
  }

  @RestController
  static class HelloController {
    @GetMapping("/hello")
    public String hello() {
      return "scheduler-master ok";
    }
  }
}
