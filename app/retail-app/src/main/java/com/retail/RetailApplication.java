package com.retail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class RetailApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetailApplication.class, args);
    }

    @GetMapping("/")
    public String home() {
        return "Retail Platform - Version: "
                + getVersion()
                + " - Environment: "
                + getEnvironment();
    }

    @GetMapping("/health")
    public String health() {

        String healthy = System.getenv()
                .getOrDefault("APP_HEALTHY", "true");

        if (!healthy.equalsIgnoreCase("true")) {
            throw new RuntimeException("Application health check failed");
        }

        return "UP";
    }

    @GetMapping("/payment")
    public String payment() {
        return "PAYMENT FIXED - Payment processing is working correctly";
    }

    @GetMapping("/version")
    public String version() {
        return getVersion();
    }

    private String getVersion() {
        return System.getenv()
                .getOrDefault("APP_VERSION", "4.2.0");
    }

    private String getEnvironment() {
        return System.getenv()
                .getOrDefault("APP_ENV", "UAT");
    }
}