package com.bolsopass.orders;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = @Index(name = "ix_orders_user", columnList = "userId"))
public class Order {
  public enum PaymentMethod {
    CREDIT_CARD,
    PIX
  }

  @Embeddable
  public static class Item {
    private String seatId;
    private String seatLabel;
    private String sectorId;
    private String sectorName;
    private long priceCents;
    private String holderName;
    private String holderCpf;
    private String ticketId;

    protected Item() {}

    public Item(String seatId, String seatLabel, String sectorId, String sectorName, long priceCents) {
      this.seatId = seatId;
      this.seatLabel = seatLabel;
      this.sectorId = sectorId;
      this.sectorName = sectorName;
      this.priceCents = priceCents;
    }

    void assignHolder(String name, String cpf) {
      this.holderName = name;
      this.holderCpf = cpf;
    }

    void assignTicket(String ticketId) {
      this.ticketId = ticketId;
    }

    public String getSeatId() {
      return seatId;
    }

    public String getSeatLabel() {
      return seatLabel;
    }

    public String getSectorId() {
      return sectorId;
    }

    public String getSectorName() {
      return sectorName;
    }

    public long getPriceCents() {
      return priceCents;
    }

    public String getHolderName() {
      return holderName;
    }

    public String getHolderCpf() {
      return holderCpf;
    }

    public String getTicketId() {
      return ticketId;
    }
  }

  @Id private String id;

  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String userId;

  @Column(nullable = false)
  private String eventId;

  private String reservationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status = OrderStatus.CRIADO;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
  @OrderColumn(name = "position")
  private List<Item> items = new ArrayList<>();

  private long subtotalCents;
  private long feeCents;
  private long totalCents;

  @Enumerated(EnumType.STRING)
  private PaymentMethod paymentMethod;

  private String cardLast4;
  private int installments;
  private String declineReason;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant paidAt;

  protected Order() {}

  public Order(String id, String code, String userId, String eventId, Instant createdAt) {
    this.id = id;
    this.code = code;
    this.userId = userId;
    this.eventId = eventId;
    this.createdAt = createdAt;
  }

  /** Única porta de mudança de status: sempre passa pela FSM. */
  public void apply(OrderStatus.Event event) {
    this.status = status.apply(event);
  }

  public void attachReservation(String reservationId, List<Item> items, double feeRate) {
    this.reservationId = reservationId;
    this.items = new ArrayList<>(items);
    this.subtotalCents = items.stream().mapToLong(Item::getPriceCents).sum();
    this.feeCents = Math.round(subtotalCents * feeRate);
    this.totalCents = subtotalCents + feeCents;
  }

  public void assignHolder(String seatId, String name, String cpf) {
    items.stream().filter(i -> i.seatId.equals(seatId)).findFirst().ifPresent(i -> i.assignHolder(name, cpf));
  }

  public void assignTicket(String seatId, String ticketId) {
    items.stream().filter(i -> i.seatId.equals(seatId)).findFirst().ifPresent(i -> i.assignTicket(ticketId));
  }

  public void paymentAttempt(PaymentMethod method, String last4, int installments) {
    this.paymentMethod = method;
    this.cardLast4 = last4;
    this.installments = installments;
    this.declineReason = null;
  }

  public void approved(Instant at) {
    apply(OrderStatus.Event.PAGAMENTO_APROVADO);
    this.paidAt = at;
  }

  public void declined(String reason) {
    apply(OrderStatus.Event.PAGAMENTO_RECUSADO);
    this.declineReason = reason;
  }

  public String getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getUserId() {
    return userId;
  }

  public String getEventId() {
    return eventId;
  }

  public String getReservationId() {
    return reservationId;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public List<Item> getItems() {
    return items;
  }

  public long getSubtotalCents() {
    return subtotalCents;
  }

  public long getFeeCents() {
    return feeCents;
  }

  public long getTotalCents() {
    return totalCents;
  }

  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public String getCardLast4() {
    return cardLast4;
  }

  public int getInstallments() {
    return installments;
  }

  public String getDeclineReason() {
    return declineReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getPaidAt() {
    return paidAt;
  }
}
