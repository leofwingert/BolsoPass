package com.bolsopass.seed;

import com.bolsopass.audit.AuditLog;
import com.bolsopass.audit.AuditLogRepository;
import com.bolsopass.catalog.Event;
import com.bolsopass.catalog.Event.Category;
import com.bolsopass.catalog.EventRepository;
import com.bolsopass.catalog.Sector;
import com.bolsopass.catalog.Sector.LotTier;
import com.bolsopass.iam.Role;
import com.bolsopass.iam.User;
import com.bolsopass.iam.UserRepository;
import com.bolsopass.inventory.Seat;
import com.bolsopass.inventory.SeatRepository;
import com.bolsopass.orders.Order;
import com.bolsopass.orders.OrderRepository;
import com.bolsopass.orders.OrderStatus;
import com.bolsopass.shared.AppProperties;
import com.bolsopass.ticketing.TicketService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados de demonstração baseados nos mockups (Mocks-User). Só roda com o banco vazio.
 * Contas: sarah@bolsopass.io / admin@bolsopass.io (senha "bolsopass"), operador OP-88219 (PIN 123456).
 */
@Component
public class DataSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  /** Assentos por setor (código -> {total, por fileira}). */
  private static final Map<String, int[]> LAYOUT =
      Map.of("A", new int[] {24, 12}, "V", new int[] {6, 6}, "B", new int[] {6, 6}, "T", new int[] {20, 10});

  private final UserRepository users;
  private final EventRepository events;
  private final SeatRepository seats;
  private final OrderRepository orders;
  private final TicketService tickets;
  private final PasswordEncoder encoder;
  private final AppProperties props;
  private final AuditLogRepository audit;

  public DataSeeder(
      UserRepository users,
      EventRepository events,
      SeatRepository seats,
      OrderRepository orders,
      TicketService tickets,
      PasswordEncoder encoder,
      AppProperties props,
      AuditLogRepository audit) {
    this.audit = audit;
    this.users = users;
    this.events = events;
    this.seats = seats;
    this.orders = orders;
    this.tickets = tickets;
    this.encoder = encoder;
    this.props = props;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.count() > 0) return;
    String pwd = encoder.encode("bolsopass");
    User sarah =
        users.save(
            new User("u-sarah", "Sarah Jensen Oliveira", "sarah@bolsopass.io", pwd, Set.of(Role.ROLE_CLIENT))
                .withContact("529.982.247-25", "(11) 98844-3210"));
    users.save(
        new User("u-admin", "Beatriz Lima", "admin@bolsopass.io", pwd, Set.of(Role.ROLE_ORGANIZER, Role.ROLE_ADMIN)));
    users.save(
        new User("u-operator", "Carlos Silva", "carlos.silva@bolsopass.io", encoder.encode("123456"),
                Set.of(Role.ROLE_OPERATOR))
            .withBadge("OP-88219"));

    Event synth =
        event("evt-synthwave", "Festival Synthwave 2026", Category.FESTIVAIS, 12, 18, Event.Status.EM_VENDAS)
            .at("Arena Pulse Cyber Dome", "São Paulo, SP", "Av. das Nações Unidas, 17.000 - São Paulo, SP")
            .details(
                "A maior imersão audiovisual retrofuturista da América Latina. 4 palcos simultâneos, som espacial 360° e controle de acesso com QR dinâmico.",
                "18+", 330, "Últimos ingressos", true)
            .schedule(days(14, 23), Instant.now().plus(Duration.ofHours(5)).plus(Duration.ofMinutes(22)));
    synth.addLineup("Palco Neon Horizon", "Gunship & The Midnight", "Show sinfônico com orquestra cibernética e projeção holográfica.");
    synth.addLineup("Palco Cyber Grid", "Carpenter Brut + Kavinsky", "Sets contínuos de darksynth com pirotecnia sincronizada.");
    synth.addLineup("Palco Sunset Drive", "FM-84 & Timecop1983", "Sessão nostálgica dos anos 80 durante o pôr do sol.");
    sectors(synth, 58_000, "Pista Premium Front Stage", 89_000L, "Camarote Synth Lounge Open Bar", true);

    Event neon =
        event("evt-neon", "Neon Odyssey World Tour", Category.ELETRONICA, 30, 21, Event.Status.EM_VENDAS)
            .at("Allianz Dome", "São Paulo, SP", "Av. Francisco Matarazzo, 1705 - São Paulo, SP")
            .details("Turnê mundial com palco central 360° e plateia ao redor.", "16+", 200, "Assentos 360° disponíveis", false);
    neon.addLineup("Palco Central 360°", "Neon Odyssey", "Show completo da turnê.");
    sectors(neon, 34_000, "Setor A • Gold Premium", null, "Camarote VIP Lounge", false);

    Event metal =
        event("evt-metal", "Cyber Metal Fest 2026", Category.ROCK, 14, 19, Event.Status.EM_VENDAS)
            .at("Arena da Baixada", "Curitiba, PR", "R. Buenos Aires, 1260 - Curitiba, PR")
            .details("Dois dias de metal industrial com pirotecnia e telões 8K.", "16+", 18, "Lote promocional 01", false);
    sectors(metal, 19_000, "Setor A • Gold Premium", null, "Camarote VIP Lounge", false);

    Event symphonic =
        event("evt-symphonic", "Pulse Symphonic Experience", Category.TEATRO, 37, 20, Event.Status.EM_VENDAS)
            .at("Qualistage", "Rio de Janeiro, RJ", "Av. Ayrton Senna, 3000 - Rio de Janeiro, RJ")
            .details("Orquestra ao vivo interpretando trilhas eletrônicas clássicas.", "Livre", 265, "Meia-entrada disponível", false);
    sectors(symphonic, 15_000, "Plateia Numerada", null, "Frisas VIP", false);

    Event retro =
        event("evt-retro", "Synth Retro Classic 2025", Category.ELETRONICA, -120, 17, Event.Status.ENCERRADO)
            .at("Arena Anhembi", "São Paulo, SP", "Av. Olavo Fontoura, 1209 - São Paulo, SP")
            .details("Evento encerrado. Check-in concluído.", "18+", 290, null, false);
    sectors(retro, 15_000, "Setor A • Gold Premium", null, "Camarote VIP Lounge", false);

    for (Event e : List.of(synth, neon, metal, symphonic, retro)) {
      events.save(e);
      seats.saveAll(buildSeats(e));
    }

    // Histórico inicial da auditoria, para a tela do evento não começar vazia.
    for (Event e : List.of(synth, neon, metal, symphonic, retro)) {
      audit(e.getId(), AuditLog.Kind.EVENTO, "Evento publicado", "Rascunho revisado e publicado no catálogo.", "admin@bolsopass.io", 72);
      audit(e.getId(), AuditLog.Kind.BILHETERIA, "Abertura da bilheteria",
          e.isHasVirtualQueue() ? "Vendas liberadas com sala de espera virtual." : "Vendas diretas liberadas.", "Sistema BolsoPass", 48);
    }
    audit(synth.getId(), AuditLog.Kind.LOTE, "Virada automática de lote",
        "Lote 01 da Pista Premium Front Stage esgotou. Lote 02 ativado com o novo preço.", "Sistema BolsoPass", 3);

    // Pedidos da Sarah (mockup "Meus Pedidos & Ingressos").
    paidOrder(sarah, synth, "A", List.of(14, 15), "PLS-98402-BR", Order.PaymentMethod.CREDIT_CARD, 0, false);
    paidOrder(sarah, neon, "A", List.of(7), "PLS-91204-BR", Order.PaymentMethod.PIX, 20, false);
    paidOrder(sarah, metal, "V", List.of(2, 3), "PLS-84391-BR", Order.PaymentMethod.CREDIT_CARD, 40, false);
    paidOrder(sarah, retro, "A", List.of(1, 2), "PLS-70119-BR", Order.PaymentMethod.CREDIT_CARD, 150, true);
    buyerOrders(pwd, List.of(synth, neon, metal, symphonic, retro));
    log.info("Seed concluído: {} eventos, {} assentos", events.count(), seats.count());
  }

  /** Compradores de exemplo (nomes, CPFs válidos e telefones fictícios). */
  private static final String[][] BUYERS = {
    {"Ana Paula Ribeiro", "(11) 99812-4410"},
    {"Bruno Carvalho Mendes", "(21) 98745-1022"},
    {"Camila Souza Prado", "(41) 99654-7731"},
    {"Diego Fernandes Lima", "(51) 98123-6604"},
    {"Eduarda Martins Rocha", "(31) 99277-3158"},
    {"Felipe Augusto Nunes", "(11) 97431-8890"},
    {"Gabriela Teixeira Alves", "(61) 99504-2276"},
    {"Henrique Moraes Costa", "(85) 98866-1349"},
  };
  private static final String[] COMPANIONS = {
    "Juliana Castro", "Lucas Pereira", "Mariana Lopes", "Rafael Duarte", "Patrícia Gomes", "Thiago Barros",
  };

  /**
   * O mapa inicial já nasce com assentos vendidos; aqui cada um ganha um pedido pago,
   * comprador e titular com CPF, para o organizador ver para quem vendeu.
   */
  private void buyerOrders(String pwd, List<Event> all) {
    List<User> buyers = new ArrayList<>();
    for (int i = 0; i < BUYERS.length; i++) {
      String name = BUYERS[i][0];
      String email = name.toLowerCase().split(" ")[0] + "." + name.toLowerCase().split(" ")[2] + "@exemplo.com";
      email = java.text.Normalizer.normalize(email, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
      buyers.add(users.save(new User("u-buyer-" + i, name, email, pwd, Set.of(Role.ROLE_CLIENT))
          .withContact(formatCpf(cpf(100_000 + i * 7919)), BUYERS[i][1])));
    }
    int code = 30_000;
    int person = 0;
    for (Event e : all) {
      Set<String> taken = orders.findByEventIdInAndStatus(Set.of(e.getId()), OrderStatus.PAGO).stream()
          .flatMap(o -> o.getItems().stream()).map(Order.Item::getSeatId).collect(Collectors.toSet());
      for (Sector sec : e.getSectors()) {
        List<Seat> free = seats.findByEventIdOrderBySectorIdAscNumberAsc(e.getId()).stream()
            .filter(s -> s.getSectorId().equals(sec.getId()) && !taken.contains(s.getId()))
            .filter(s -> s.getStatus() == Seat.Status.VENDIDO || s.getStatus() == Seat.Status.UTILIZADO)
            .toList();
        int soldInSector = (int) taken.stream().filter(id -> id.startsWith(e.getId() + ":" + sec.getCode())).count();
        for (int i = 0; i < free.size(); ) {
          int size = Math.min(1 + (code % 3), free.size() - i);
          List<Seat> group = free.subList(i, i + size);
          User buyer = buyers.get(person++ % buyers.size());
          int daysAgo = e.getStatus() == Event.Status.ENCERRADO ? 130 + code % 20 : code % 9;
          Instant paidAt = Instant.now().minus(Duration.ofDays(daysAgo)).minus(Duration.ofMinutes(code % 600));
          Order order = new Order("ord-" + UUID.randomUUID(), "PLS-" + code + "-BR", buyer.getId(), e.getId(), paidAt);
          List<Order.Item> items = new ArrayList<>();
          for (Seat seat : group) {
            items.add(new Order.Item(seat.getId(), seat.getLabel(), sec.getId(), sec.getName(), priceAt(sec, soldInSector++)));
          }
          order.attachReservation(null, items, props.serviceFeeRate());
          order.apply(OrderStatus.Event.ASSENTO_BLOQUEADO);
          order.apply(OrderStatus.Event.SUBMETER_PAGAMENTO);
          Order.PaymentMethod method = code % 2 == 0 ? Order.PaymentMethod.PIX : Order.PaymentMethod.CREDIT_CARD;
          order.paymentAttempt(method, method == Order.PaymentMethod.CREDIT_CARD ? String.valueOf(1000 + code % 9000) : null, 1);
          for (int k = 0; k < items.size(); k++) {
            String seatId = items.get(k).getSeatId();
            if (k == 0) order.assignHolder(seatId, buyer.getName(), buyer.getCpf().replaceAll("\\D", ""));
            else order.assignHolder(seatId, COMPANIONS[(code + k) % COMPANIONS.length], cpf(code * 31 + k));
          }
          order.approved(paidAt);
          orders.save(order);
          tickets.issue(order);
          code++;
          i += size;
        }
      }
    }
  }

  /** Preço do lote em que caiu a n-ésima venda do setor (lotes em sequência). */
  private static long priceAt(Sector sec, int nth) {
    int acc = 0;
    for (LotTier t : sec.getLots()) {
      acc += t.quantity();
      if (nth < acc) return t.priceCents();
    }
    return sec.getLots().get(sec.getLots().size() - 1).priceCents();
  }

  /** CPF válido (dígitos verificadores corretos) gerado a partir de uma semente. */
  static String cpf(int seed) {
    int[] d = new int[11];
    long base = Math.floorMod(seed * 2654435761L, 1_000_000_000L);
    for (int i = 8; i >= 0; i--) {
      d[i] = (int) (base % 10);
      base /= 10;
    }
    if (java.util.Arrays.stream(d, 0, 9).distinct().count() == 1) d[0] = (d[0] + 1) % 10;
    for (int pos = 9; pos <= 10; pos++) {
      int sum = 0;
      for (int i = 0; i < pos; i++) sum += d[i] * (pos + 1 - i);
      int r = (sum * 10) % 11;
      d[pos] = r == 10 ? 0 : r;
    }
    StringBuilder sb = new StringBuilder();
    for (int x : d) sb.append(x);
    return sb.toString();
  }

  private static String formatCpf(String c) {
    return c.substring(0, 3) + "." + c.substring(3, 6) + "." + c.substring(6, 9) + "-" + c.substring(9);
  }

  private void audit(String eventId, AuditLog.Kind kind, String title, String text, String actor, int hoursAgo) {
    audit.save(new AuditLog(eventId, kind, title, text, actor).at(Instant.now().minus(Duration.ofHours(hoursAgo))));
  }

  private static Instant days(int days, int hour) {
    return Instant.now().truncatedTo(ChronoUnit.DAYS).plus(Duration.ofDays(days)).plus(Duration.ofHours(hour + 3));
  }

  private static Event event(String id, String title, Category cat, int inDays, int hour, Event.Status status) {
    return new Event(id, title, cat, days(inDays, hour), status).ownedBy("u-admin");
  }

  /**
   * Setores com lotes em sequência. A soma das cotas é igual à capacidade de cada setor
   * (A=24, V=6, B=6, T=20). O setor A tem 3 lotes para demonstrar a virada automática.
   */
  private static void sectors(
      Event e, long base, String premiumName, Long premiumOriginal, String vipName, boolean topClosed) {
    e.addSector(
        new Sector("A", premiumName, "#e11d48", 12)
            .info("Visão frontal privilegiada, colado ao palco principal.", "Portão 04 - VIP",
                List.of("Entrada dedicada Portão B", "Pulseira RFID inclusa"))
            .addLot(new LotTier("Lote 01", Math.round(base * 0.8), null, 6, 4, 10))
            .addLot(new LotTier("Lote 02", base, premiumOriginal, 10, 4, 10))
            .addLot(new LotTier("Lote 03", Math.round(base * 1.2), null, 8, 4, 10)));
    e.addSector(
        new Sector("V", vipName, "#1e293b", 6)
            .info("Deck elevado, open bar, gastronomia autoral e after party exclusivo.", "Portão VIP Sul",
                List.of("Entrada VIP Portão VIP-1", "Fast pass para atrações"))
            .addLot(new LotTier("Lote único", Math.round(base * 1.41), null, 6, 2, 12)));
    e.addSector(
        new Sector("B", "Setor B • Lateral", "#64748b", 6)
            .info("Visão lateral com assentos numerados.", "Portão 02 - Leste", List.of("Entrada Portão 02"))
            .addLot(new LotTier("Lote único", Math.round(base * 0.59), null, 6, 4, 6)));
    Sector top =
        new Sector("T", "Tribuna Superior", "#94a3b8", 10)
            .info("Visão panorâmica com cobertura completa.", "Portão 01 - Pista Comum", List.of("Entrada Portão 01"))
            .addLot(new LotTier("Lote 01", Math.round(base * 0.45), null, 10, 4, 6))
            .addLot(new LotTier("Lote 02", Math.round(base * 0.55), null, 10, 4, 6));
    e.addSector(topClosed ? top.close() : top);
  }

  /** Estado inicial do mapa: ~28% vendido, de forma determinística. */
  private static List<Seat> buildSeats(Event e) {
    List<Seat> out = new ArrayList<>();
    for (Sector sec : e.getSectors()) {
      int[] layout = LAYOUT.get(sec.getCode());
      for (int n = 1; n <= layout[0]; n++) {
        Seat.Kind kind = sec.getCode().equals("A") && (n == 3 || n == 4) ? Seat.Kind.PCD : Seat.Kind.PADRAO;
        Seat seat = new Seat(e.getId(), sec.getId(), sec.getCode(), n, layout[1], kind);
        boolean sold =
            e.getStatus() == Event.Status.ENCERRADO
                || sec.isClosed()
                || Math.floorMod((e.getId() + n + sec.getCode()).hashCode(), 100) < 28;
        if (sold) seat.markSold();
        out.add(seat);
      }
    }
    return out;
  }

  private void paidOrder(
      User user, Event e, String sectorCode, List<Integer> numbers, String code,
      Order.PaymentMethod method, int daysAgo, boolean used) {
    Map<String, Seat> byId =
        seats.findByEventIdOrderBySectorIdAscNumberAsc(e.getId()).stream()
            .collect(Collectors.toMap(Seat::getId, Function.identity()));
    Sector sec = e.getSectors().stream().filter(s -> s.getCode().equals(sectorCode)).findFirst().orElseThrow();
    Instant paidAt = Instant.now().minus(Duration.ofDays(daysAgo));
    Order order = new Order("ord-" + UUID.randomUUID(), code, user.getId(), e.getId(), paidAt);
    List<Order.Item> items = new ArrayList<>();
    for (int n : numbers) {
      Seat seat = byId.get(e.getId() + ":" + sectorCode + n);
      if (used) seat.markUsed();
      else seat.markSold();
      items.add(new Order.Item(seat.getId(), seat.getLabel(), sec.getId(), sec.getName(), sec.getLots().get(0).priceCents()));
    }
    order.attachReservation(null, items, props.serviceFeeRate());
    order.apply(OrderStatus.Event.ASSENTO_BLOQUEADO);
    order.apply(OrderStatus.Event.SUBMETER_PAGAMENTO);
    order.paymentAttempt(method, method == Order.PaymentMethod.CREDIT_CARD ? "4291" : null, 1);
    for (int i = 0; i < items.size(); i++) {
      Order.Item it = items.get(i);
      if (i == 0) order.assignHolder(it.getSeatId(), user.getName(), "52998224725");
      else order.assignHolder(it.getSeatId(), "Marcus Vinicius Andrade", "41200897189");
    }
    order.approved(paidAt);
    orders.save(order);
    tickets.issue(order);
  }
}
