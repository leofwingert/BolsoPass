package com.bolsopass.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Envia mensagens aos navegadores inscritos em cada tópico.
 * Formato: {"topic": "...", "payload": {...}} (o mesmo que o front espera em realtime.ts).
 */
@Component
public class RealtimeHub {
  private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);

  private final Map<String, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();
  private final ObjectMapper json;

  public RealtimeHub(ObjectMapper json) {
    this.json = json;
  }

  public static String eventSeats(String eventId) {
    return "events/" + eventId + "/seats";
  }

  public static String order(String orderId) {
    return "orders/" + orderId;
  }

  void subscribe(String topic, WebSocketSession session) {
    subscribers.computeIfAbsent(topic, t -> ConcurrentHashMap.newKeySet()).add(session);
  }

  void unsubscribe(String topic, WebSocketSession session) {
    Set<WebSocketSession> set = subscribers.get(topic);
    if (set != null) set.remove(session);
  }

  void unsubscribeAll(WebSocketSession session) {
    subscribers.values().forEach(s -> s.remove(session));
  }

  public int subscriberCount(String topic) {
    Set<WebSocketSession> set = subscribers.get(topic);
    return set == null ? 0 : set.size();
  }

  public void publish(String topic, Map<String, Object> payload) {
    Set<WebSocketSession> set = subscribers.get(topic);
    if (set == null || set.isEmpty()) return;
    String text;
    try {
      text = json.writeValueAsString(Map.of("topic", topic, "payload", payload));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    for (WebSocketSession s : set) {
      try {
        if (s.isOpen()) s.sendMessage(new TextMessage(text));
      } catch (IOException | IllegalStateException e) {
        log.debug("Falha ao enviar para sessão {}: {}", s.getId(), e.getMessage());
      }
    }
  }
}
