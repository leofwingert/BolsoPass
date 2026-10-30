package com.bolsopass.realtime;

import com.bolsopass.orders.OrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Protocolo do front: {"action":"subscribe"|"unsubscribe","topic":"..."}.
 * Tópicos de assentos são públicos; "orders/{id}" exige token do dono do pedido.
 */
@Component
public class RealtimeSocketHandler extends TextWebSocketHandler {
  private static final String USER_ATTR = "userId";

  private final RealtimeHub hub;
  private final JwtDecoder jwtDecoder;
  private final OrderRepository orders;
  private final ObjectMapper json;

  public RealtimeSocketHandler(
      RealtimeHub hub, JwtDecoder jwtDecoder, OrderRepository orders, ObjectMapper json) {
    this.hub = hub;
    this.jwtDecoder = jwtDecoder;
    this.orders = orders;
    this.json = json;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    URI uri = session.getUri();
    if (uri == null) return;
    String token =
        UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("access_token");
    if (token == null) return;
    try {
      session.getAttributes().put(USER_ATTR, jwtDecoder.decode(token).getSubject());
    } catch (JwtException e) {
      // Token inválido: segue como anônimo (só tópicos públicos).
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonNode msg = json.readTree(message.getPayload());
    String action = msg.path("action").asText();
    String topic = msg.path("topic").asText();
    if (topic.isBlank()) return;
    if ("unsubscribe".equals(action)) {
      hub.unsubscribe(topic, session);
    } else if ("subscribe".equals(action) && allowed(session, topic)) {
      hub.subscribe(topic, session);
    }
  }

  private boolean allowed(WebSocketSession session, String topic) {
    if (topic.startsWith("events/") && topic.endsWith("/seats")) return true;
    if (topic.startsWith("orders/")) {
      Object userId = session.getAttributes().get(USER_ATTR);
      String orderId = topic.substring("orders/".length());
      return userId != null
          && orders.findById(orderId).map(o -> o.getUserId().equals(userId)).orElse(false);
    }
    return false;
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    hub.unsubscribeAll(session);
  }
}
