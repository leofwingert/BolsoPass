package com.bolsopass.organizer;

import com.bolsopass.catalog.Event;
import com.bolsopass.inventory.Seat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Corpo do wizard "Configuração Avançada de Ingressos" (mockup Admin). */
public record CreateEventRequest(
    @NotBlank @Size(max = 120) String title,
    @NotNull Event.Category category,
    @NotNull @Future Instant startsAt,
    @NotBlank String venue,
    @NotBlank String city,
    String address,
    @Size(max = 1000) String description,
    String ageRating,
    boolean hasVirtualQueue,
    @NotEmpty @Size(max = 8) List<@Valid SectorInput> sectors,
    List<@Valid BlockedSeat> blockedSeats,
    boolean publish) {

  public record SectorInput(
      @NotBlank String name,
      /** Assentos numerados do setor (o mapa é gerado assento a assento). */
      @Min(1) @Max(500) int capacity,
      @Min(1) @Max(30) int seatsPerRow,
      String gate,
      String description,
      @NotEmpty @Size(max = 6) List<@Valid LotInput> lots) {}

  public record LotInput(
      @NotBlank String name,
      @Min(100) long priceCents,
      @Min(1) int quantity,
      @Min(1) @Max(10) int limitPerCpf,
      @Min(1) @Max(12) int maxInstallments) {}

  /** Marcação do mini editor: índice do setor + número do assento. */
  public record BlockedSeat(@Min(0) int sector, @Min(1) int number, @NotNull Seat.Kind kind) {}
}
