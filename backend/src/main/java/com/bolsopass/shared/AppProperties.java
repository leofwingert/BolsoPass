package com.bolsopass.shared;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bolsopass")
public record AppProperties(
    Duration reservationTtl, double serviceFeeRate, Jwt jwt, List<String> corsOrigins) {

  public record Jwt(Duration accessTtl, Duration refreshTtl) {}
}
