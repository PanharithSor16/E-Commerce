package com.org.ecommerce;

import org.springframework.boot.SpringApplication;

/** Runs the application locally against a throwaway SQL Server container: ./mvnw spring-boot:test-run */
public class TestEcommerceApplication {

    public static void main(String[] args) {
        SpringApplication.from(EcommerceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
