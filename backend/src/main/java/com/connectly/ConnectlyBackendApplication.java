package com.connectly;

import com.connectly.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties(JwtProperties.class)
public class ConnectlyBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnectlyBackendApplication.class, args);
    }
}
