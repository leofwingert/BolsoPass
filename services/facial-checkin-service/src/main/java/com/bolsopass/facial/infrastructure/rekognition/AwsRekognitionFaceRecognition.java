package com.bolsopass.facial.infrastructure.rekognition;

import com.bolsopass.facial.domain.model.FacialException;
import com.bolsopass.facial.domain.port.FaceRecognition;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CreateCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.DeleteCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.DeleteFacesRequest;
import software.amazon.awssdk.services.rekognition.model.FaceRecord;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.IndexFacesRequest;
import software.amazon.awssdk.services.rekognition.model.IndexFacesResponse;
import software.amazon.awssdk.services.rekognition.model.InvalidParameterException;
import software.amazon.awssdk.services.rekognition.model.QualityFilter;
import software.amazon.awssdk.services.rekognition.model.Reason;
import software.amazon.awssdk.services.rekognition.model.ResourceAlreadyExistsException;
import software.amazon.awssdk.services.rekognition.model.ResourceNotFoundException;
import software.amazon.awssdk.services.rekognition.model.SearchFacesByImageRequest;
import software.amazon.awssdk.services.rekognition.model.SearchFacesByImageResponse;

/**
 * AWS Rekognition real ({@code facial.rekognition.mode=aws}). Credenciais pela cadeia padrão do SDK
 * (variáveis {@code AWS_ACCESS_KEY_ID}/{@code AWS_SECRET_ACCESS_KEY} ou perfil). A imagem vai em
 * bytes na requisição e não é gravada em lugar nenhum (nem no S3).
 */
public class AwsRekognitionFaceRecognition implements FaceRecognition {
  private static final Logger log = LoggerFactory.getLogger(AwsRekognitionFaceRecognition.class);

  private final RekognitionClient client;
  private final String prefix;

  public AwsRekognitionFaceRecognition(RekognitionClient client, String collectionPrefix) {
    this.client = client;
    this.prefix = collectionPrefix;
  }

  /** Uma collection por evento: bolsopass-evt-neon. */
  String collectionId(String eventId) {
    return prefix + eventId.replaceAll("[^a-zA-Z0-9_.\\-]", "-");
  }

  @Override
  public void ensureCollection(String eventId) {
    try {
      client.createCollection(CreateCollectionRequest.builder().collectionId(collectionId(eventId)).build());
      log.info("Collection {} criada", collectionId(eventId));
    } catch (ResourceAlreadyExistsException e) {
      // Já existe: nada a fazer.
    } catch (SdkException e) {
      throw unavailable(e);
    }
  }

  @Override
  public String indexFace(String eventId, String ticketId, byte[] image) {
    IndexFacesResponse r;
    try {
      r = client.indexFaces(IndexFacesRequest.builder()
          .collectionId(collectionId(eventId))
          .image(image(image))
          .externalImageId(ticketId)
          .maxFaces(1)
          .qualityFilter(QualityFilter.AUTO)
          .build());
    } catch (InvalidParameterException e) {
      throw noFace();
    } catch (SdkException e) {
      throw unavailable(e);
    }
    List<FaceRecord> records = r.faceRecords();
    boolean manyFaces = r.unindexedFaces().stream().anyMatch(u -> u.reasons().contains(Reason.EXCEEDS_MAX_FACES));
    if (manyFaces) {
      // Selfie com outra pessoa: não indexa nada para não liberar o rosto errado.
      deleteFaces(eventId, records.stream().map(f -> f.face().faceId()).toList());
      throw FacialException.unprocessable("A foto tem mais de um rosto. Tire a selfie sozinho(a).");
    }
    if (records.isEmpty()) {
      throw FacialException.unprocessable(
          "Rosto não aproveitável (desfocado, escuro ou pequeno demais). Tente com mais luz e de frente.");
    }
    return records.getFirst().face().faceId();
  }

  @Override
  public Optional<FaceMatch> searchFace(String eventId, byte[] image, float similarityThreshold) {
    SearchFacesByImageResponse r;
    try {
      r = client.searchFacesByImage(SearchFacesByImageRequest.builder()
          .collectionId(collectionId(eventId))
          .image(image(image))
          .faceMatchThreshold(similarityThreshold)
          .maxFaces(1)
          .build());
    } catch (ResourceNotFoundException e) {
      return Optional.empty(); // ninguém cadastrou rosto neste evento ainda
    } catch (InvalidParameterException e) {
      throw noFace();
    } catch (SdkException e) {
      throw unavailable(e);
    }
    return r.faceMatches().stream()
        .findFirst()
        .map(m -> new FaceMatch(m.face().externalImageId(), m.face().faceId(), m.similarity()));
  }

  @Override
  public void deleteFaces(String eventId, Collection<String> faceIds) {
    if (faceIds.isEmpty()) return;
    try {
      client.deleteFaces(DeleteFacesRequest.builder().collectionId(collectionId(eventId)).faceIds(faceIds).build());
    } catch (ResourceNotFoundException e) {
      // Collection já apagada: os rostos já não existem.
    } catch (SdkException e) {
      throw unavailable(e);
    }
  }

  @Override
  public void deleteCollection(String eventId) {
    try {
      client.deleteCollection(DeleteCollectionRequest.builder().collectionId(collectionId(eventId)).build());
    } catch (ResourceNotFoundException e) {
      // Já apagada.
    } catch (SdkException e) {
      throw unavailable(e);
    }
  }

  private static Image image(byte[] bytes) {
    return Image.builder().bytes(SdkBytes.fromByteArray(bytes)).build();
  }

  private static FacialException noFace() {
    return FacialException.unprocessable("Nenhum rosto encontrado na foto. Centralize o rosto e tente de novo.");
  }

  private static FacialException unavailable(SdkException e) {
    log.warn("Rekognition indisponível: {}", e.getMessage());
    return FacialException.unavailable("Reconhecimento facial indisponível agora. Use o QR Code.");
  }
}
