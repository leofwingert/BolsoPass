package com.bolsopass.catalog;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Sala de espera virtual (mockup "Detalhe do Evento & Sala de Espera").
 * Simplificada: cada comprador entra com uma posição e avança 1 posição por segundo.
 * Eventos com fila só aceitam reserva de quem já foi admitido.
 * TODO: mover para Redis (ZSET) quando houver mais de uma instância.
 */
@Component
public class VirtualQueue {
  static final int START_POSITION = 12;
  static final int TOTAL_IN_LINE = 12_450;

  private final Map<String, Long> joinedAt = new ConcurrentHashMap<>();
  private final Clock clock;

  public VirtualQueue() {
    this(Clock.systemUTC());
  }

  VirtualQueue(Clock clock) {
    this.clock = clock;
  }

  public record Status(
      int position, int total, int etaSeconds, int progressPct, String sessionId, boolean admitted) {}

  public Status join(String eventId, String visitorId) {
    long start = joinedAt.computeIfAbsent(key(eventId, visitorId), k -> clock.millis());
    int elapsed = (int) ((clock.millis() - start) / 1000);
    int position = Math.max(0, START_POSITION - elapsed);
    int progress = Math.round((START_POSITION - position) * 100f / START_POSITION);
    String session = "tk_" + Long.toString(start, 36);
    return new Status(position, TOTAL_IN_LINE, position * 5, progress, session, position == 0);
  }

  public boolean isAdmitted(String eventId, String userId) {
    Long start = joinedAt.get(key(eventId, userId));
    return start != null && clock.millis() - start >= START_POSITION * 1000L;
  }

  private static String key(String eventId, String visitorId) {
    return eventId + "|" + visitorId;
  }
}
