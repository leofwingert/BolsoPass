package com.bolsopass.facial.infrastructure.rekognition;

import com.bolsopass.facial.domain.model.FacialException;
import com.bolsopass.facial.domain.port.FaceRecognition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rekognition simulado, para testes e desenvolvimento sem conta AWS ({@code facial.rekognition.mode=simulated}).
 *
 * <p>Não reconhece rostos de verdade: a "assinatura" do rosto é o hash SHA-256 da imagem. A mesma
 * foto reconhece; outra foto não. Como no real, só a assinatura fica em memória, nunca a imagem.
 *
 * <ul>
 *   <li>Imagem com menos de 1 KB = "nenhum rosto encontrado".
 *   <li>Imagem que começa com o texto {@code MULTI} = "mais de um rosto" (para os testes).
 * </ul>
 */
public class SimulatedFaceRecognition implements FaceRecognition {
  static final int MIN_FACE_BYTES = 1024;

  private record StoredFace(String faceId, String ticketId, String signature) {}

  /** eventId → (faceId → rosto). */
  private final Map<String, Map<String, StoredFace>> collections = new ConcurrentHashMap<>();

  @Override
  public void ensureCollection(String eventId) {
    collections.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>());
  }

  @Override
  public String indexFace(String eventId, String ticketId, byte[] image) {
    String signature = signature(image);
    if (new String(image, 0, Math.min(5, image.length), StandardCharsets.US_ASCII).equals("MULTI")) {
      throw FacialException.unprocessable("A foto tem mais de um rosto. Tire a selfie sozinho(a).");
    }
    Map<String, StoredFace> faces = collections.get(eventId);
    if (faces == null) throw FacialException.unavailable("Collection do evento não existe");
    String faceId = UUID.randomUUID().toString();
    faces.put(faceId, new StoredFace(faceId, ticketId, signature));
    return faceId;
  }

  @Override
  public Optional<FaceMatch> searchFace(String eventId, byte[] image, float similarityThreshold) {
    String signature = signature(image);
    Map<String, StoredFace> faces = collections.get(eventId);
    if (faces == null) return Optional.empty();
    return faces.values().stream()
        .filter(f -> f.signature().equals(signature))
        .findFirst()
        .map(f -> new FaceMatch(f.ticketId(), f.faceId(), 99.5f))
        .filter(m -> m.similarity() >= similarityThreshold);
  }

  @Override
  public void deleteFaces(String eventId, Collection<String> faceIds) {
    Map<String, StoredFace> faces = collections.get(eventId);
    if (faces != null) faceIds.forEach(faces::remove);
  }

  @Override
  public void deleteCollection(String eventId) {
    collections.remove(eventId);
  }

  /** Quantos rostos há na collection (só para os testes). */
  public int size(String eventId) {
    Map<String, StoredFace> faces = collections.get(eventId);
    return faces == null ? 0 : faces.size();
  }

  private static String signature(byte[] image) {
    if (image.length < MIN_FACE_BYTES) {
      throw FacialException.unprocessable("Nenhum rosto encontrado na foto. Centralize o rosto e tente de novo.");
    }
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
