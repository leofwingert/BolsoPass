package com.bolsopass.ticketing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado de Ingresso Digital Criptografado com prevencao de dupla entrada na catraca (RF-13 / US-13 / US-15).
 */
public class DigitalTicket {

    private final UUID ticketId;
    private final UUID orderId;
    private final UUID clientId;
    private final UUID eventId;
    private final String seatId;
    private final Instant issuedAt;
    private final String timestampHash;
    private final String qrPayload;
    private final String qrSignature;
    private final byte[] pdfContent;
    private boolean scannedAtGate;
    private Instant firstScannedAt;

    public DigitalTicket(
            UUID ticketId,
            UUID orderId,
            UUID clientId,
            UUID eventId,
            String seatId,
            Instant issuedAt,
            String timestampHash,
            String qrPayload,
            String qrSignature,
            byte[] pdfContent
    ) {
        this.ticketId = Objects.requireNonNull(ticketId, "ticketId cannot be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.clientId = Objects.requireNonNull(clientId, "clientId cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        if (seatId == null || seatId.isBlank()) {
            throw new IllegalArgumentException("seatId cannot be blank");
        }
        this.seatId = seatId.trim();
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt cannot be null");
        if (timestampHash == null || timestampHash.isBlank()) {
            throw new IllegalArgumentException("timestampHash cannot be blank");
        }
        if (qrPayload == null || qrPayload.isBlank()) {
            throw new IllegalArgumentException("qrPayload cannot be blank");
        }
        if (qrSignature == null || qrSignature.isBlank()) {
            throw new IllegalArgumentException("qrSignature cannot be blank");
        }
        if (pdfContent == null || pdfContent.length == 0) {
            throw new IllegalArgumentException("pdfContent cannot be empty");
        }
        this.timestampHash = timestampHash;
        this.qrPayload = qrPayload;
        this.qrSignature = qrSignature;
        this.pdfContent = pdfContent.clone();
        this.scannedAtGate = false;
    }

    public synchronized ScanResultStatus validateAndScanAtGate(
            String presentedPayload,
            String presentedSignature,
            TicketCryptoSigner signer,
            Instant scanTime
    ) {
        Objects.requireNonNull(signer, "signer cannot be null");
        Objects.requireNonNull(scanTime, "scanTime cannot be null");

        if (!this.qrPayload.equals(presentedPayload) || !signer.verifyOfflineWithPublicKey(presentedPayload, presentedSignature)) {
            return ScanResultStatus.INVALIDO;
        }
        if (this.scannedAtGate) {
            return ScanResultStatus.DUPLICATA;
        }
        this.scannedAtGate = true;
        this.firstScannedAt = scanTime;
        return ScanResultStatus.AUTORIZADO;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getSeatId() {
        return seatId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public String getTimestampHash() {
        return timestampHash;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public String getQrSignature() {
        return qrSignature;
    }

    public byte[] getPdfContent() {
        return pdfContent.clone();
    }

    public synchronized boolean isScannedAtGate() {
        return scannedAtGate;
    }

    public synchronized Instant getFirstScannedAt() {
        return firstScannedAt;
    }
}

