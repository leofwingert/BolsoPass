package com.bolsopass.facial.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio injetável: os testes trocam por um relógio fixo (retenção, validade do evento). */
@Configuration
public class ClockConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
