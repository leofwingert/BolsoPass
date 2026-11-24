package com.bolsopass.ticketing.service;

import com.bolsopass.orders.domain.PedidoPagoEvent;
import com.bolsopass.ticketing.domain.DigitalTicket;
import com.bolsopass.ticketing.domain.ScanResultStatus;
import com.bolsopass.ticketing.domain.TicketCryptoSigner;
import com.bolsopass.ticketing.infrastructure.ProcessedMessageEntity;
import com.bolsopass.ticketing.infrastructure.ProcessedMessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servico de Emissao de Ingressos em PDF com QR Code ECDSA P-256, Consumo Idempotente Kafka
 * e Notificacao Reativa via WebSockets (RF-12, RF-13, RF-14).
 */
@Service
public class TicketingService {

    public static final String CONSUMER_GROUP = "ticketing-payment";

    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;
    private final TicketCryptoSigner cryptoSigner = new TicketCryptoSigner();
    private final Map<UUID, DigitalTicket> ticketsByOrderId = new ConcurrentHashMap<>();
    private final Map<UUID, DigitalTicket> ticketsById = new ConcurrentHashMap<>();
    private final List<String> websocketNotifications = new CopyOnWriteArrayList<>();

    public TicketingService(ProcessedMessageRepository processedMessageRepository, ObjectMapper objectMapper) {
        this.processedMessageRepository = processedMessageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumidor Kafka idempotente para o topico `pedidos.pagos`.
     * Garante deduplicacao via tabela `processed_messages`.
     */
    @KafkaListener(topics = "pedidos.pagos", groupId = CONSUMER_GROUP, autoStartup = "${bolsopass.kafka.listener.auto-startup:false}")
    @Transactional
    public void onPedidoPagoKafkaMessage(String payloadJson) {
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            UUID orderId = UUID.fromString(node.get("order_id").asText());
            UUID clientId = UUID.fromString(node.get("client_id").asText());
            UUID eventId = UUID.fromString(node.get("event_id").asText());
            String seatId = node.get("seat_id").asText();
            long amountCents = node.get("amount_cents").asLong();
            Instant paidAt = Instant.parse(node.get("paid_at").asText());

            String messageId = node.has("message_id") ? node.get("message_id").asText() : "msg_" + orderId;
            consumeOrderPaidIdempotently(messageId, new PedidoPagoEvent(orderId, clientId, eventId, seatId, amountCents, paidAt));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process PedidoPagoEvent payload", e);
        }
    }

    @Transactional
    public Optional<DigitalTicket> consumeOrderPaidIdempotently(String messageId, PedidoPagoEvent event) {
        if (processedMessageRepository.existsById(messageId)) {
            // Mensagem duplicada: no-op idempotente (RF-12 / US-12)
            return Optional.empty();
        }
        processedMessageRepository.save(new ProcessedMessageEntity(messageId, CONSUMER_GROUP, Instant.now()));

        DigitalTicket ticket = generateSignedPdfTicket(event);
        ticketsByOrderId.put(event.orderId(), ticket);
        ticketsById.put(ticket.getTicketId(), ticket);

        // Push reativo via canal WebSocket privado (/ws/orders/{orderId}) (RF-14 / US-14)
        String wsDestination = "/ws/orders/" + event.orderId();
        websocketNotifications.add(wsDestination + ":TICKET_ISSUED:" + ticket.getTicketId());
        return Optional.of(ticket);
    }

    public DigitalTicket generateSignedPdfTicket(PedidoPagoEvent event) {
        UUID ticketId = UUID.randomUUID();
        Instant issuedAt = Instant.now();
        String tsHash = cryptoSigner.computeTimestampAuditHash(ticketId, event.orderId(), issuedAt);
        String qrPayload = cryptoSigner.buildCanonicalQrPayload(ticketId, event.orderId(), event.eventId(), event.seatId(), issuedAt);
        String qrSignature = cryptoSigner.signPayload(qrPayload);

        byte[] pdfBytes = renderPdfDocument(ticketId, event, issuedAt, tsHash, qrPayload, qrSignature);
        return new DigitalTicket(
                ticketId,
                event.orderId(),
                event.clientId(),
                event.eventId(),
                event.seatId(),
                issuedAt,
                tsHash,
                qrPayload,
                qrSignature,
                pdfBytes
        );
    }

    public ScanResultStatus scanTicketAtGate(UUID ticketId, String qrPayload, String qrSignature) {
        DigitalTicket ticket = ticketsById.get(ticketId);
        if (ticket == null) {
            return ScanResultStatus.INVALIDO;
        }
        return ticket.validateAndScanAtGate(qrPayload, qrSignature, cryptoSigner, Instant.now());
    }

    private byte[] renderPdfDocument(
            UUID ticketId,
            PedidoPagoEvent event,
            Instant issuedAt,
            String tsHash,
            String qrPayload,
            String qrSignature
    ) {
        try {
            ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, pdfOut);
            document.open();

            document.add(new Paragraph("BOLSOPASS - INGRESSO DIGITAL OFICIAL", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
            document.add(new Paragraph("Ticket ID: " + ticketId));
            document.add(new Paragraph("Order ID: " + event.orderId()));
            document.add(new Paragraph("Event ID: " + event.eventId()));
            document.add(new Paragraph("Assento Numerado: " + event.seatId()));
            document.add(new Paragraph("Valor Pago (centavos): " + event.amountCents()));
            document.add(new Paragraph("Carimbo de Tempo (Timestamp UTC): " + issuedAt));
            document.add(new Paragraph("Hash SHA-256 de Auditoria: " + tsHash));
            document.add(new Paragraph("Assinatura Criptografica ECDSA P-256: " + qrSignature));

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrPayload + "#" + qrSignature, BarcodeFormat.QR_CODE, 180, 180);
            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOut);
            Image qrImage = Image.getInstance(pngOut.toByteArray());
            document.add(qrImage);

            document.close();
            return pdfOut.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate PDF ticket with QR code", e);
        }
    }

    public TicketCryptoSigner getCryptoSigner() {
        return cryptoSigner;
    }

    public Optional<DigitalTicket> findTicketByOrderId(UUID orderId) {
        return Optional.ofNullable(ticketsByOrderId.get(orderId));
    }

    public Optional<DigitalTicket> findTicketById(UUID ticketId) {
        return Optional.ofNullable(ticketsById.get(ticketId));
    }

    public List<String> getWebsocketNotifications() {
        return List.copyOf(websocketNotifications);
    }

    public int countIssuedTickets() {
        return ticketsById.size();
    }
}

