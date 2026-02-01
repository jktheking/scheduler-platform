package com.acme.scheduler.alert;

import com.acme.scheduler.meter.Otel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class AlertApplication {
 public static void main(String[] args) {
 Otel.initNoop();
 SpringApplication.run(AlertApplication.class, args);
 }

 @RestController
 static class HelloController {
 @GetMapping("/hello")
 public String hello() {
 return "scheduler-alert-server ok";
 }
 }
}
