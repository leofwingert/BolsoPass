package com.bolsopass.organizer;

import com.bolsopass.audit.AuditLog;
import com.bolsopass.catalog.CatalogService.EventDetailView;
import com.bolsopass.catalog.CatalogService.EventSummaryView;
import java.time.Instant;
import java.util.List;

/** Formato JSON das telas do organizador (src/api/types.ts do front). */
public final class OrganizerViews {
  private OrganizerViews() {}

  public record DayRevenue(String day, long cents) {}

  public record Critical(String eventId, String title, String venue, long daysLeft, int occupancyPct) {}

  public record LotTurn(
      String eventId, String eventTitle, String sectorName, String lotName, int remaining,
      long currentPriceCents, long nextPriceCents) {}

  /** Saúde da venda: tudo medido no banco, nada estimado. */
  public record Health(
      long locksLastMinute,
      long activeLocks,
      long oversellingIncidents,
      long expiredReservationsToday,
      int checkoutConversionPct,
      long outboxPending) {}

  public record Dashboard(
      long eventsActive,
      long eventsNewThisMonth,
      long ticketsToday,
      int ticketsTodayGrowthPct,
      long revenueTodayCents,
      long grossRevenueCents,
      long netRevenueCents,
      int platformFeePct,
      List<DayRevenue> weeklyRevenue,
      Critical critical,
      LotTurn lotTurn,
      Health health) {}

  public record EventRow(
      EventSummaryView event, long revenueCents, long ticketsSold, long avgTicketCents, String note) {}

  public record LotSummary(String name, long priceCents, int quantity, int sold, String status) {}

  public record SectorSummary(
      String id, String name, int capacity, long sold, long locked, boolean closed, List<LotSummary> lots,
      String gate, long checkedIn) {}

  public record LogView(String id, Instant at, AuditLog.Kind kind, String title, String description, String actor) {}

  public record EventDetail(
      EventDetailView event,
      long revenueCents,
      long netRevenueCents,
      long ticketsIssued,
      long activeLocks,
      long expiredToday,
      List<Long> salesLast6h,
      List<SectorSummary> sectors,
      List<LogView> logs) {}
}
