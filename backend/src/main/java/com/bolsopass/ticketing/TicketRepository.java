package com.bolsopass.ticketing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, String> {
  List<Ticket> findByUserIdOrderByIssuedAtDesc(String userId);

  List<Ticket> findByEventId(String eventId);

  /** Trava a linha do ingresso: duas catracas (QR e facial) não liberam o mesmo ingresso. */
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select t from Ticket t where t.id = :id")
  java.util.Optional<Ticket> lockById(@org.springframework.data.repository.query.Param("id") String id);

  /** Ingressos em que a pessoa é a titular (CPF vinculado), mesmo comprados por outra conta. */
  List<Ticket> findByHolderCpfOrderByIssuedAtDesc(String holderCpf);
}
