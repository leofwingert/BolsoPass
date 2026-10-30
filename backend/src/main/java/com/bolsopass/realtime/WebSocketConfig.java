package com.bolsopass.realtime;

import com.bolsopass.shared.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
  private final RealtimeSocketHandler handler;
  private final AppProperties props;

  public WebSocketConfig(RealtimeSocketHandler handler, AppProperties props) {
    this.handler = handler;
    this.props = props;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(new ThreadSafeSessions(handler), "/ws")
        .setAllowedOrigins(props.corsOrigins().toArray(String[]::new));
  }

  /** Várias threads podem publicar na mesma sessão: o decorator serializa o envio. */
  static class ThreadSafeSessions extends WebSocketHandlerDecorator {
    ThreadSafeSessions(RealtimeSocketHandler delegate) {
      super(delegate);
    }

    private static WebSocketSession wrap(WebSocketSession s) {
      return s instanceof ConcurrentWebSocketSessionDecorator
          ? s
          : (WebSocketSession)
              s.getAttributes()
                  .computeIfAbsent(
                      "safe", k -> new ConcurrentWebSocketSessionDecorator(s, 5_000, 512 * 1024));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
      super.afterConnectionEstablished(wrap(session));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
      super.handleMessage(wrap(session), message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
      super.afterConnectionClosed(wrap(session), status);
    }
  }
}
