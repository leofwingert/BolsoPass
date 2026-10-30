package com.bolsopass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bolsopass.iam.AuthService;
import com.bolsopass.orders.OrderRepository;
import com.bolsopass.orders.OrderStatus;
import com.bolsopass.realtime.RealtimeHub;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** WebSocket /ws de verdade: tópicos públicos de assentos e tópico de pedido só para o dono. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeIntegrationTest {
  @LocalServerPort int port;
  @Autowired RealtimeHub hub;
  @Autowired AuthService auth;
  @Autowired OrderRepository orders;

  private final BlockingQueue<String> received = new LinkedBlockingQueue<>();

  private WebSocketSession connect(String token) throws Exception {
    String url = "ws://localhost:" + port + "/ws" + (token == null ? "" : "?access_token=" + token);
    return new StandardWebSocketClient()
        .execute(new TextWebSocketHandler() {
          @Override
          protected void handleTextMessage(WebSocketSession s, TextMessage m) {
            received.add(m.getPayload());
          }
        }, url)
        .get(5, TimeUnit.SECONDS);
  }

  private static void send(WebSocketSession s, String action, String topic) throws Exception {
    s.sendMessage(new TextMessage("{\"action\":\"" + action + "\",\"topic\":\"" + topic + "\"}"));
  }

  @Test
  void assentosSaoPublicosEAsMensagensChegam() throws Exception {
    String topic = RealtimeHub.eventSeats("evt-neon");
    WebSocketSession ws = connect(null);
    send(ws, "subscribe", topic);
    await().atMost(Duration.ofSeconds(5)).until(() -> hub.subscriberCount(topic) > 0);

    hub.publish(topic, Map.of("type", "SEAT_SOLD", "eventId", "evt-neon", "seatIds", new String[] {"evt-neon:A1"}));
    String msg = received.poll(5, TimeUnit.SECONDS);
    assertThat(msg).contains("\"topic\":\"" + topic + "\"").contains("SEAT_SOLD");

    send(ws, "unsubscribe", topic);
    await().atMost(Duration.ofSeconds(5)).until(() -> hub.subscriberCount(topic) == 0);
    ws.close();
  }

  @Test
  void pedidoSoParaODono() throws Exception {
    String orderId = orders.findByUserIdAndStatusOrderByCreatedAtDesc("u-sarah", OrderStatus.PAGO).get(0).getId();
    String topic = RealtimeHub.order(orderId);

    // Anônimo e token inválido não assinam o pedido de outra pessoa.
    WebSocketSession anon = connect(null);
    send(anon, "subscribe", topic);
    WebSocketSession forged = connect("token.falso");
    send(forged, "subscribe", topic);
    send(forged, "subscribe", "outro/topico");
    send(forged, "subscribe", "");
    Thread.sleep(300);
    assertThat(hub.subscriberCount(topic)).isZero();

    // A dona do pedido assina; ao fechar a conexão, sai de todos os tópicos.
    String token = auth.login("sarah@bolsopass.io", "bolsopass").accessToken();
    WebSocketSession owner = connect(token);
    send(owner, "subscribe", topic);
    await().atMost(Duration.ofSeconds(5)).until(() -> hub.subscriberCount(topic) == 1);
    owner.close();
    await().atMost(Duration.ofSeconds(5)).until(() -> hub.subscriberCount(topic) == 0);
    anon.close();
    forged.close();
  }
}
