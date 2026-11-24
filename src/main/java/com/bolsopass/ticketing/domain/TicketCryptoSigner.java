package com.bolsopass.ticketing.domain;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Assinador e Verificador Criptografico Assimetrico de Ingressos (ECDSA P-256 / SHA256withECDSA).
 * Garante inviolabilidade do QR Code e permite conferencia 100% offline na catraca em <= 300ms (RF-13 / RNF-06 / US-13 / US-15).
 */
public class TicketCryptoSigner {

    private final KeyPair ecKeyPair;

    public TicketCryptoSigner() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            this.ecKeyPair = kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize ECDSA P-256 key pair", e);
        }
    }

    public TicketCryptoSigner(KeyPair ecKeyPair) {
        this.ecKeyPair = Objects.requireNonNull(ecKeyPair, "ecKeyPair cannot be null");
    }

    public String computeTimestampAuditHash(UUID ticketId, UUID orderId, Instant issuedAt) {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
        Objects.requireNonNull(orderId, "orderId cannot be null");
        Objects.requireNonNull(issuedAt, "issuedAt cannot be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = ticketId + ":" + orderId + ":" + issuedAt.toString();
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String buildCanonicalQrPayload(
            UUID ticketId,
            UUID orderId,
            UUID eventId,
            String seatId,
            Instant issuedAt
    ) {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
        Objects.requireNonNull(orderId, "orderId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        if (seatId == null || seatId.isBlank()) {
            throw new IllegalArgumentException("seatId cannot be blank");
        }
        Objects.requireNonNull(issuedAt, "issuedAt cannot be null");
        String tsHash = computeTimestampAuditHash(ticketId, orderId, issuedAt);
        return String.join("|",
                ticketId.toString(),
                orderId.toString(),
                eventId.toString(),
                seatId.trim(),
                issuedAt.toString(),
                tsHash
        );
    }

    public String signPayload(String qrPayload) {
        if (qrPayload == null || qrPayload.isBlank()) {
            throw new IllegalArgumentException("qrPayload cannot be blank");
        }
        try {
            Signature ecdsa = Signature.getInstance("SHA256withECDSA");
            ecdsa.initSign(ecKeyPair.getPrivate());
            ecdsa.update(qrPayload.getBytes(StandardCharsets.UTF_8));
            byte[] sigBytes = ecdsa.sign();
            return HexFormat.of().formatHex(sigBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign QR payload with ECDSA P-256", e);
        }
    }

    public boolean verifyOfflineWithPublicKey(String qrPayload, String hexSignature) {
        return verifyOfflineWithPublicKey(qrPayload, hexSignature, ecKeyPair.getPublic());
    }

    public static boolean verifyOfflineWithPublicKey(String qrPayload, String hexSignature, PublicKey publicKey) {
        if (qrPayload == null || qrPayload.isBlank() || hexSignature == null || hexSignature.isBlank() || publicKey == null) {
            return false;
        }
        try {
            byte[] sigBytes = HexFormat.of().parseHex(hexSignature.trim());
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(qrPayload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(sigBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public PublicKey getPublicKey() {
        return ecKeyPair.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return ecKeyPair.getPrivate();
    }
}

