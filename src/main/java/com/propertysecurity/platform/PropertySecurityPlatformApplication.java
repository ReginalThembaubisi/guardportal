package com.propertysecurity.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PropertySecurityPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertySecurityPlatformApplication.class, args);
    }
}
