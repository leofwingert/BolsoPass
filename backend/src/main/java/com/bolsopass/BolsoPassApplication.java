package com.bolsopass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BolsoPass API.
 *
 * <p>Por enquanto roda como um único serviço, com um pacote por microsserviço planejado no
 * C4 (iam, catalog, inventory, orders, ticketing). Os pacotes só conversam por serviços e
 * eventos de outbox, para que a separação em contêineres seja mecânica.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class BolsoPassApplication {
  public static void main(String[] args) {
    SpringApplication.run(BolsoPassApplication.class, args);
  }
}
