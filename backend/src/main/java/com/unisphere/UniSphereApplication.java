package com.unisphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UniSphereApplication {

    public static void main(String[] args) {
        SpringApplication.run(UniSphereApplication.class, args);
    }
}
