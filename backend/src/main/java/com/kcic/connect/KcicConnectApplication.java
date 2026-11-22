package com.kcic.connect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class KcicConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(KcicConnectApplication.class, args);
    }
}
