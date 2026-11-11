package com.bolsopass.facial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Microsserviço de check-in por reconhecimento facial (AWS Rekognition).
 *
 * <p>Camadas: {@code presentation} (HTTP) → {@code application} (casos de uso) → {@code domain}
 * (regras e portas) ← {@code infrastructure} (JPA, Rekognition, backend de ingressos, segurança).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FacialCheckinApplication {
  public static void main(String[] args) {
    SpringApplication.run(FacialCheckinApplication.class, args);
  }
}
