package com.supportflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "SupportFlow AI")
@SpringBootApplication
public class SupportFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportFlowApplication.class, args);
    }
}
