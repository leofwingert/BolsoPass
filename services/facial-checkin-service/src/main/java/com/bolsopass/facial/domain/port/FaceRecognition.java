package com.bolsopass.facial.domain.port;

import java.util.Collection;
import java.util.Optional;

/**
 * Porta para o serviço externo de reconhecimento facial (AWS Rekognition). Uma collection por
 * evento, criada sob demanda. A imagem só trafega em memória: nenhuma implementação a grava.
 *
 * <p>Fotos sem rosto (ou com mais de um no cadastro) lançam {@code FacialException} 422.
 */
public interface FaceRecognition {

  /** Cria a collection do evento se ainda não existir. */
  void ensureCollection(String eventId);

  /** IndexFaces com ExternalImageId = ticketId. Devolve o FaceId. */
  String indexFace(String eventId, String ticketId, byte[] image);

  /** SearchFacesByImage com a similaridade mínima. Vazio = ninguém parecido (ou evento sem rostos). */
  Optional<FaceMatch> searchFace(String eventId, byte[] image, float similarityThreshold);

  /** DeleteFaces. Ignora rostos ou collection que já não existem. */
  void deleteFaces(String eventId, Collection<String> faceIds);

  /** Apaga a collection inteira do evento (retenção). */
  void deleteCollection(String eventId);

  record FaceMatch(String ticketId, String faceId, float similarity) {}
}
