package com.bolsopass.orders;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, String> {
  /** Trava a linha do pedido: pagamento e expiração nunca rodam ao mesmo tempo. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id = :id")
  Optional<Order> lockById(String id);

  List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, OrderStatus status);

  List<Order> findByEventIdInAndStatus(Collection<String> eventIds, OrderStatus status);

  long countByEventIdInAndStatusAndCreatedAtAfter(
      Collection<String> eventIds, OrderStatus status, java.time.Instant after);

  List<Order> findByUserIdAndEventIdAndStatusIn(
      String userId, String eventId, Collection<OrderStatus> statuses);
}
