package com.bolsopass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bolsopass.catalog.CatalogService;
import com.bolsopass.catalog.Event;
import com.bolsopass.iam.Role;
import com.bolsopass.iam.User;
import com.bolsopass.iam.UserRepository;
import com.bolsopass.inventory.ReservationService;
import com.bolsopass.inventory.Seat;
import com.bolsopass.inventory.SeatRepository;
import com.bolsopass.orders.CheckoutService;
import com.bolsopass.orders.CheckoutService.Holder;
import com.bolsopass.orders.CheckoutService.PayRequest;
import com.bolsopass.orders.Order;
import com.bolsopass.orders.OrderViews.OrderView;
import com.bolsopass.organizer.CreateEventRequest;
import com.bolsopass.organizer.CreateEventRequest.BlockedSeat;
import com.bolsopass.organizer.CreateEventRequest.LotInput;
import com.bolsopass.organizer.CreateEventRequest.SectorInput;
import com.bolsopass.organizer.OrganizerService;
import com.bolsopass.organizer.OrganizerViews;
import com.bolsopass.organizer.SeatSalesService;
import com.bolsopass.shared.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@SpringBootTest
class OrganizerIntegrationTest {
  @Autowired OrganizerService organizer;
  @Autowired CatalogService catalog;
  @Autowired ReservationService reservations;
  @Autowired CheckoutService checkout;
  @Autowired SeatRepository seats;
  @Autowired UserRepository users;
  @Autowired SeatSalesService sales;

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private String loginAs(Role role) {
    String id = "u-" + UUID.randomUUID();
    users.save(new User(id, "Pessoa Teste", id + "@test.io", "x", Set.of(role)));
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(id).claim("roles", List.of(role.name())).build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role.name()))));
    return id;
  }

  private CreateEventRequest request(List<LotInput> lots, int capacity, List<BlockedSeat> blocked) {
    return new CreateEventRequest(
        "Show de Teste", Event.Category.ROCK, Instant.now().plus(Duration.ofDays(20)), "Arena Teste",
        "Porto Alegre, RS", null, "Teste", "Livre", false,
        List.of(new SectorInput("Plateia", capacity, 10, "Portão 1", null, lots)), blocked, true);
  }

  @Test
  void somaDosLotesPrecisaFecharComOsAssentosVendaveis() {
    loginAs(Role.ROLE_ORGANIZER);
    var bad = request(List.of(new LotInput("Lote 1", 10_000, 5, 4, 1)), 10, List.of());
    assertThatThrownBy(() -> organizer.create(bad)).hasMessageContaining("soma dos lotes");

    // 10 assentos, 2 obstruídos => 8 vendáveis.
    var ok = request(List.of(new LotInput("Lote 1", 10_000, 8, 4, 1)), 10,
        List.of(new BlockedSeat(0, 1, Seat.Kind.OBSTRUCAO), new BlockedSeat(0, 2, Seat.Kind.OBSTRUCAO)));
    String id = organizer.create(ok);
    assertThat(catalog.find(id).getStatus()).isEqualTo(Event.Status.EM_VENDAS);
    assertThat(seats.findByEventIdOrderBySectorIdAscNumberAsc(id)).hasSize(10);
  }

  @Test
  void viradaAutomaticaDeLoteAoEsgotarACota() {
    loginAs(Role.ROLE_ORGANIZER);
    String eventId = organizer.create(request(
        List.of(new LotInput("Lote 1", 10_000, 1, 4, 1), new LotInput("Lote 2", 20_000, 1, 4, 1)), 2, List.of()));

    String client = loginAs(Role.ROLE_CLIENT);
    String seat = seats.findByEventIdOrderBySectorIdAscNumberAsc(eventId).get(0).getId();
    var r = reservations.reserve(client, eventId, List.of(seat));
    OrderView paid = checkout.pay(client,
        new PayRequest(r.id(), Order.PaymentMethod.PIX, 1, List.of(new Holder(seat, "Ana Souza", "52998224725")), null),
        UUID.randomUUID().toString());
    assertThat(paid.items().get(0).priceCents()).isEqualTo(10_000);

    // Lote 1 esgotou: o próximo comprador paga o preço do Lote 2.
    assertThat(catalog.detail(eventId).lots().get(0).priceCents()).isEqualTo(20_000);

    SecurityContextHolder.getContext().setAuthentication(null);
    String owner = catalog.find(eventId).getOrganizerId();
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(owner).build();
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))));
    OrganizerViews.EventDetail detail = organizer.detail(eventId);
    assertThat(detail.logs()).anyMatch(l -> l.title().equals("Virada automática de lote"));
    assertThat(detail.sectors().get(0).lots()).extracting(OrganizerViews.LotSummary::status)
        .containsExactly("ESGOTADO", "ATIVO");
  }

  @Test
  void organizadorNaoMexeNoEventoDeOutro() {
    loginAs(Role.ROLE_ORGANIZER);
    String eventId = organizer.create(request(List.of(new LotInput("Lote 1", 10_000, 4, 4, 1)), 4, List.of()));

    loginAs(Role.ROLE_ORGANIZER);
    assertThatThrownBy(() -> organizer.pause(eventId))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).status())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(organizer.list()).noneMatch(row -> row.event().id().equals(eventId));

    // Admin enxerga e pode pausar qualquer evento.
    loginAs(Role.ROLE_ADMIN);
    organizer.pause(eventId);
    assertThat(catalog.find(eventId).getStatus()).isEqualTo(Event.Status.PAUSADO);
  }

  @Test
  void organizadorVeParaQuemVendeuCadaAssento() {
    loginAs(Role.ROLE_ADMIN);
    var sectorId = organizer.detail("evt-synthwave").sectors().get(0).id();
    var map = sales.sectorSeats("evt-synthwave", sectorId);
    var sold = map.seats().stream().filter(s -> s.status().equals("VENDIDO")).toList();
    assertThat(sold).isNotEmpty();

    // Todo assento vendido tem pedido, comprador e titular com CPF.
    for (var seat : sold) {
      var sale = sales.seatSale("evt-synthwave", seat.id());
      assertThat(sale.orderCode()).startsWith("PLS-");
      assertThat(sale.buyer().phone()).isNotBlank();
      assertThat(sale.holder().cpf()).matches("\\d{11}");
    }
    assertThat(organizer.detail("evt-synthwave").logs())
        .anyMatch(l -> l.title().equals("Consulta de dados do comprador"));

    // Assento livre não tem venda; outro organizador não vê os dados.
    var free = map.seats().stream().filter(s -> s.status().equals("DISPONIVEL")).findFirst().orElseThrow();
    assertThatThrownBy(() -> sales.seatSale("evt-synthwave", free.id())).isInstanceOf(ApiException.class);
    loginAs(Role.ROLE_ORGANIZER);
    assertThatThrownBy(() -> sales.seatSale("evt-synthwave", sold.get(0).id()))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).status())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void painelCalculaNumerosDoBanco() {
    String admin = loginAs(Role.ROLE_ADMIN);
    var dash = organizer.dashboard();
    assertThat(admin).isNotBlank();
    assertThat(dash.grossRevenueCents()).isPositive();
    assertThat(dash.weeklyRevenue()).hasSize(7);
    assertThat(dash.health().oversellingIncidents()).isZero();
  }
}
