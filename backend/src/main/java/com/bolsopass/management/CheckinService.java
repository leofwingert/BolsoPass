package com.bolsopass.management;

import com.bolsopass.inventory.Seat;
import com.bolsopass.inventory.SeatRepository;
import com.bolsopass.outbox.OutboxWriter;
import com.bolsopass.ticketing.Ticket;
import com.bolsopass.ticketing.TicketRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro da entrada na portaria, igual para os dois métodos (QR e reconhecimento facial).
 *
 * <ul>
 *   <li>Fonte única da verdade de "ingresso já usado": a linha do ingresso é travada, então duas
 *       catracas (ou QR e rosto ao mesmo tempo) nunca liberam o mesmo ingresso.
 *   <li>A entrada e o evento {@code checkin.realizado} são gravados na <b>mesma transação</b>
 *       (Transactional Outbox): nunca há entrada sem evento, nem evento sem entrada.
 * </ul>
 */
@Service
public class CheckinService {
  public enum Method {
    QR,
    FACIAL
  }

  public enum Status {
    LIBERADO,
    NEGADO
  }

  /** Motivos de recusa (o facial acrescenta ROSTO_NAO_RECONHECIDO do lado dele). */
  public enum Reason {
    INGRESSO_INVALIDO,
    INGRESSO_JA_UTILIZADO
  }

  public record Result(
      Status status, Reason reason, String ticketId, String holderName, String seatLabel, String sectorName,
      Instant usedAt, String usedGate) {}

  private final TicketRepository tickets;
  private final SeatRepository seats;
  private final OutboxWriter outbox;

  public CheckinService(TicketRepository tickets, SeatRepository seats, OutboxWriter outbox) {
    this.tickets = tickets;
    this.seats = seats;
    this.outbox = outbox;
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public Result register(String eventId, String ticketId, Method method, String gate, String operatorId, Instant at) {
    Ticket t = ticketId == null ? null : tickets.lockById(ticketId).orElse(null);
    if (t == null || !t.getEventId().equals(eventId)) {
      return new Result(Status.NEGADO, Reason.INGRESSO_INVALIDO, ticketId, null, null, null, null, null);
    }
    if (!t.checkIn(at, gate == null ? "" : gate)) {
      Reason reason = t.getUsedAt() != null ? Reason.INGRESSO_JA_UTILIZADO : Reason.INGRESSO_INVALIDO;
      return new Result(Status.NEGADO, reason, t.getId(), t.getHolderName(), t.getSeatLabel(), t.getSectorName(),
          t.getUsedAt(), t.getUsedGate());
    }
    seats.findById(t.getSeatId()).ifPresent(Seat::markUsed);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("ticketId", t.getId());
    payload.put("eventId", eventId);
    payload.put("metodo", method.name());
    payload.put("timestamp", at.toString());
    payload.put("operadorId", operatorId);
    payload.put("portao", gate == null ? "" : gate);
    outbox.write(OutboxWriter.CHECKIN_REALIZADO, t.getId(), "CheckinRealizadoEvent", payload);

    return new Result(Status.LIBERADO, null, t.getId(), t.getHolderName(), t.getSeatLabel(), t.getSectorName(),
        t.getUsedAt(), t.getUsedGate());
  }
}
