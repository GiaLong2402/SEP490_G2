package com.hbsisms;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HbsismsApplication {

    public static void main(String[] args) {
        // The app works in UTC throughout. Also stops the JDBC driver from sending the host's zone
        // (e.g. Windows' legacy "Asia/Saigon"), which newer PostgreSQL tzdata rejects at connect time.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(HbsismsApplication.class, args);
    }
}
