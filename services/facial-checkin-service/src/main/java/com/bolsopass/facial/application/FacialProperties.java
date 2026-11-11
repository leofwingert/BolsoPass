package com.bolsopass.facial.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "facial")
public record FacialProperties(
    String consentTermVersion, float similarityThreshold, int retentionDays, Ticketing ticketing, Rekognition rekognition) {

  public record Ticketing(String baseUrl) {}

  public record Rekognition(String mode, String region, String collectionPrefix) {}
}
