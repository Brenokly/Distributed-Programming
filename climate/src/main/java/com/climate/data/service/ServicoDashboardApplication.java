package com.climate.data.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServicoDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicoDashboardApplication.class, args);
        System.out.println("--- Microsserviço de Dashboard (RabbitMQ + WebFlux) INICIADO na porta 8080 ---");
    }
}
