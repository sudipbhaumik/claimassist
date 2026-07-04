package com.claimassist;

import com.claimassist.config.ClaimAssistProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ClaimAssistProperties.class)
public class ClaimAssistApplication {

  public static void main(String[] args) {
    SpringApplication.run(ClaimAssistApplication.class, args);
  }
}
