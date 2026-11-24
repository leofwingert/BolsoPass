package com.bolsopass.domain;

import com.bolsopass.catalog.domain.EventCatalogItem;
import com.bolsopass.iam.domain.AccountRateLimitedException;
import com.bolsopass.iam.domain.AuditEvent;
import com.bolsopass.iam.domain.InvalidCredentialsException;
import com.bolsopass.iam.domain.RefreshTokenFamily;
import com.bolsopass.iam.domain.RefreshTokenReuseException;
import com.bolsopass.iam.domain.Role;
import com.bolsopass.iam.domain.UnauthorizedRoleException;
import com.bolsopass.iam.domain.UserAccount;
import com.bolsopass.telemetry.domain.TelemetrySnapshot;
import com.bolsopass.ticketing.domain.DigitalTicket;
import com.bolsopass.ticketing.domain.ScanResultStatus;
import com.bolsopass.ticketing.domain.TicketCryptoSigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModulesUnitTest {

    private static final Instant T0 = Instant.parse("2026-11-14T20:00:00Z");

    @Test
    @DisplayName("IAM: RBAC roles e bloqueio por Rate Limiting apos 5 falhas consecutivas (US-01 / US-03)")
    void shouldEnforceRbacAndLockAccountAfterFiveFailedAttempts() {
        UserAccount client = new UserAccount(
                UUID.randomUUID(), "gabriel@bolsopass.com", "123.456.789-00",
                "Gabriel S Silva", "hash_secret", EnumSet.of(Role.ROLE_CLIENT)
        );

        assertThat(client.hasRole(Role.ROLE_CLIENT)).isTrue();
        assertThat(client.hasRole(Role.ROLE_ORGANIZER)).isFalse();
        assertThatThrownBy(() -> client.assertHasRole(Role.ROLE_ORGANIZER))
                .isInstanceOf(UnauthorizedRoleException.class);

        // 4 falhas retornam InvalidCredentialsException; a 5a aciona Rate Limit (HTTP 429)
        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> client.authenticate("wrong_hash", T0))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThatThrownBy(() -> client.authenticate("wrong_hash", T0))
                .isInstanceOf(AccountRateLimitedException.class);
        assertThat(client.isLocked(T0.plusSeconds(30))).isTrue();

        // Mesmo com a senha correta, enquanto estiver bloqueado retorna AccountRateLimitedException
        assertThatThrownBy(() -> client.authenticate("hash_secret", T0.plusSeconds(30)))
                .isInstanceOf(AccountRateLimitedException.class);

        // Apos 1 minuto de bloqueio, autentica com sucesso e zera contador
        client.authenticate("hash_secret", T0.plusSeconds(61));
        assertThat(client.getConsecutiveFailedAttempts()).isZero();
        assertThat(client.isLocked(T0.plusSeconds(61))).isFalse();

        AuditEvent audit = new AuditEvent(UUID.randomUUID(), client.getId(), "LOGIN_OK", "auth/login", "Success", T0);
        assertThat(audit.action()).isEqualTo("LOGIN_OK");
    }

    @Test
    @DisplayName("IAM: Rotacao de Refresh Token e revogacao total da familia ao detectar reuso malicioso (US-02)")
    void shouldRotateRefreshTokensAndInvalidateFamilyOnReplayDetection() {
        UUID familyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RefreshTokenFamily family = new RefreshTokenFamily(familyId, userId, "rt_v1", T0);

        String v2 = family.rotate("rt_v1", "rt_v2", T0.plusSeconds(60));
        assertThat(v2).isEqualTo("rt_v2");
        assertThat(family.getActiveTokenValue()).isEqualTo("rt_v2");

        // Tentativa de reutilizar o token antigo rt_v1 aciona deteccao de sequestro e invalida toda a familia!
        assertThatThrownBy(() -> family.rotate("rt_v1", "rt_attacker", T0.plusSeconds(120)))
                .isInstanceOf(RefreshTokenReuseException.class);

        assertThat(family.isFamilyCompromised()).isTrue();
        assertThat(family.getActiveTokenValue()).isNull();

        // Nem mesmo o token rt_v2 funciona mais apos o comprometimento da familia
        assertThatThrownBy(() -> family.rotate("rt_v2", "rt_v3", T0.plusSeconds(130)))
                .isInstanceOf(RefreshTokenReuseException.class);
    }

    @Test
    @DisplayName("Catalogo: Invariante de soma de lotes <= capacidade total do estadio e datas validas (RF-04)")
    void shouldEnforceCatalogLotCapacityAndDateInvariants() {
        EventCatalogItem event = new EventCatalogItem(
                UUID.randomUUID(), UUID.randomUUID(),
                "Festival Rock PUCRS 2026", "Estadio Central PUCRS",
                1000, T0, T0.plusSeconds(14400)
        );

        EventCatalogItem.TicketLot lot1 = new EventCatalogItem.TicketLot(
                UUID.randomUUID(), "1o Lote Pista", 38500L, 600, T0, T0.plusSeconds(3600)
        );
        EventCatalogItem.TicketLot lot2 = new EventCatalogItem.TicketLot(
                UUID.randomUUID(), "2o Lote Cadeira", 35000L, 400, T0, T0.plusSeconds(7200)
        );

        event.addLot(lot1);
        event.addLot(lot2);
        assertThat(event.getTotalAllocatedQuota()).isEqualTo(1000);
        assertThat(event.getLots()).hasSize(2);

        // 3o lote que excede a capacidade de 1000 lugares deve ser rejeitado
        EventCatalogItem.TicketLot overflowingLot = new EventCatalogItem.TicketLot(
                UUID.randomUUID(), "Lote Extra", 50000L, 1, T0, T0.plusSeconds(3600)
        );
        assertThatThrownBy(() -> event.addLot(overflowingLot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds venue capacity");
    }

    @Test
    @DisplayName("Ticketing: Assinatura ECDSA P-256, verificacao offline em < 300ms e prevencao de dupla entrada na catraca (US-13 / US-15)")
    void shouldSignAndVerifyTicketOfflineAndPreventDoubleEntry() {
        TicketCryptoSigner signer = new TicketCryptoSigner();
        UUID ticketId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        String tsHash = signer.computeTimestampAuditHash(ticketId, orderId, T0);
        String payload = signer.buildCanonicalQrPayload(ticketId, orderId, eventId, "B-04", T0);
        String signature = signer.signPayload(payload);

        long startNanos = System.nanoTime();
        boolean validOffline = signer.verifyOfflineWithPublicKey(payload, signature);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(validOffline).isTrue();
        assertThat(elapsedMillis).isLessThan(300L); // RNF-06 SLA <= 300ms

        DigitalTicket ticket = new DigitalTicket(
                ticketId, orderId, clientId, eventId, "B-04",
                T0, tsHash, payload, signature, "%PDF-1.4 mock".getBytes(StandardCharsets.UTF_8)
        );

        // 1a leitura na catraca -> AUTORIZADO
        assertThat(ticket.validateAndScanAtGate(payload, signature, signer, T0.plusSeconds(3600)))
                .isEqualTo(ScanResultStatus.AUTORIZADO);
        assertThat(ticket.isScannedAtGate()).isTrue();

        // 2a leitura do mesmo QR Code (clone/impressao duplicada) -> DUPLICATA
        assertThat(ticket.validateAndScanAtGate(payload, signature, signer, T0.plusSeconds(3605)))
                .isEqualTo(ScanResultStatus.DUPLICATA);

        // QR Code adulterado -> INVALIDO
        assertThat(ticket.validateAndScanAtGate(payload + "|TAMPERED", signature, signer, T0.plusSeconds(3610)))
                .isEqualTo(ScanResultStatus.INVALIDO);
    }

    @Test
    @DisplayName("Telemetria: Agregacao em tempo real de conversao do funil e zero sobre-venda (US-18)")
    void shouldAggregateTelemetryMetricsAccurately() {
        TelemetrySnapshot snapshot = new TelemetrySnapshot();
        snapshot.recordReservationAttempt(true);
        snapshot.recordReservationAttempt(true);
        snapshot.recordReservationAttempt(false);
        snapshot.recordReservationExpired();
        snapshot.recordOrderPaid(38500L);

        assertThat(snapshot.getTotalRequests()).isEqualTo(3L);
        assertThat(snapshot.getReservationsCreated()).isEqualTo(2L);
        assertThat(snapshot.getReservationsRejectedConflict()).isEqualTo(1L);
        assertThat(snapshot.getReservationsExpired()).isEqualTo(1L);
        assertThat(snapshot.getOrdersPaid()).isEqualTo(1L);
        assertThat(snapshot.getTotalRevenueCents()).isEqualTo(38500L);
        assertThat(snapshot.getCheckoutConversionRatePercent()).isEqualTo(50.0);
        assertThat(snapshot.getOversellingIncidents()).isZero();

        TelemetrySnapshot emptySnapshot = new TelemetrySnapshot();
        assertThat(emptySnapshot.getCheckoutConversionRatePercent()).isEqualTo(0.0);
        assertThatThrownBy(() -> emptySnapshot.recordOrderPaid(0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Telemetria: registra incidentes de sobre-venda")
    void shouldRecordOversellingIncidents() {
        TelemetrySnapshot snapshot = new TelemetrySnapshot();

        assertThat(snapshot.getOversellingIncidents()).isZero();

        snapshot.recordOversellingIncident();

        assertThat(snapshot.getOversellingIncidents()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Guard Clauses & Invariantes de Borda em Todos os Agregados de Dominio")
    void shouldValidateAllDomainGuardClausesAndEdgeCases() {
        // UserAccount & AuditEvent guard clauses
        assertThatThrownBy(() -> new UserAccount(UUID.randomUUID(), "invalid-email", "123", "Name", "hash", EnumSet.of(Role.ROLE_CLIENT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserAccount(UUID.randomUUID(), "a@b.com", " ", "Name", "hash", EnumSet.of(Role.ROLE_CLIENT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserAccount(UUID.randomUUID(), "a@b.com", "123", " ", "hash", EnumSet.of(Role.ROLE_CLIENT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserAccount(UUID.randomUUID(), "a@b.com", "123", "Name", " ", EnumSet.of(Role.ROLE_CLIENT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserAccount(UUID.randomUUID(), "a@b.com", "123", "Name", "hash", EnumSet.noneOf(Role.class)))
                .isInstanceOf(IllegalArgumentException.class);

        UserAccount admin = new UserAccount(UUID.randomUUID(), "admin@b.com", "999", "Admin", "h", EnumSet.of(Role.ROLE_ADMIN));
        assertThat(admin.hasRole(Role.ROLE_ORGANIZER)).isTrue();
        admin.assertHasRole(Role.ROLE_OPERATOR);
        assertThat(admin.getCpf()).isEqualTo("999");
        assertThat(admin.getFullName()).isEqualTo("Admin");
        assertThat(admin.getLockedUntil()).isNull();

        assertThatThrownBy(() -> new AuditEvent(UUID.randomUUID(), admin.getId(), " ", "res", "d", T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditEvent(UUID.randomUUID(), admin.getId(), "ACT", " ", "d", T0))
                .isInstanceOf(IllegalArgumentException.class);

        // RefreshTokenFamily edge cases
        RefreshTokenFamily family = new RefreshTokenFamily(UUID.randomUUID(), admin.getId(), "rt_init", T0);
        assertThat(family.getFamilyId()).isNotNull();
        assertThatThrownBy(() -> new RefreshTokenFamily(UUID.randomUUID(), admin.getId(), " ", T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> family.rotate("rt_init", " ", T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> family.rotate("unknown_token", "rt_next", T0))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> family.rotate("rt_init", "rt_next", T0.plusSeconds(8 * 86400L)))
                .isInstanceOf(InvalidCredentialsException.class);

        // EventCatalogItem & TicketLot edge cases
        assertThatThrownBy(() -> new EventCatalogItem(UUID.randomUUID(), UUID.randomUUID(), " ", "Venue", 100, T0, T0.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventCatalogItem(UUID.randomUUID(), UUID.randomUUID(), "Title", " ", 100, T0, T0.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventCatalogItem(UUID.randomUUID(), UUID.randomUUID(), "Title", "Venue", 0, T0, T0.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventCatalogItem(UUID.randomUUID(), UUID.randomUUID(), "Title", "Venue", 100, T0.plusSeconds(60), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventCatalogItem.TicketLot(UUID.randomUUID(), " ", 100, 10, T0, T0.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventCatalogItem.TicketLot(UUID.randomUUID(), "L1", 0, 10, T0, T0.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventCatalogItem.TicketLot(UUID.randomUUID(), "L1", 100, 0, T0, T0.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventCatalogItem.TicketLot(UUID.randomUUID(), "L1", 100, 10, T0.plusSeconds(60), T0))
                .isInstanceOf(IllegalArgumentException.class);

        // TicketCryptoSigner & DigitalTicket edge cases
        TicketCryptoSigner signer = new TicketCryptoSigner();
        TicketCryptoSigner signerCopy = new TicketCryptoSigner(
                new java.security.KeyPair(signer.getPublicKey(), signer.getPrivateKey()));
        assertThat(signerCopy.verifyOfflineWithPublicKey("", "sig")).isFalse();
        assertThat(signerCopy.verifyOfflineWithPublicKey("payload", "")).isFalse();
        assertThat(signerCopy.verifyOfflineWithPublicKey("payload", "not_hex_zz")).isFalse();
        assertThat(TicketCryptoSigner.verifyOfflineWithPublicKey("payload", "abcd", null)).isFalse();
        assertThatThrownBy(() -> signer.signPayload(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.buildCanonicalQrPayload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), " ", T0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new DigitalTicket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                " ", T0, "h", "p", "s", new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DigitalTicket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "A-1", T0, " ", "p", "s", new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DigitalTicket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "A-1", T0, "h", " ", "s", new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DigitalTicket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "A-1", T0, "h", "p", " ", new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DigitalTicket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "A-1", T0, "h", "p", "s", new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }
}
