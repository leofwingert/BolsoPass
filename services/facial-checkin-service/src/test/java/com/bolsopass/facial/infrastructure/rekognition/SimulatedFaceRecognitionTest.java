package com.bolsopass.facial.infrastructure.rekognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bolsopass.facial.domain.model.FacialException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulatedFaceRecognitionTest {
  static byte[] face(int seed) {
    byte[] b = new byte[2048];
    Arrays.fill(b, (byte) seed);
    return b;
  }

  @Test
  void mesmaFotoReconheceOutraNao() {
    SimulatedFaceRecognition sim = new SimulatedFaceRecognition();
    sim.ensureCollection("evt-1");
    String faceId = sim.indexFace("evt-1", "TK-1", face(1));

    var match = sim.searchFace("evt-1", face(1), 90);
    assertThat(match).isPresent();
    assertThat(match.get().ticketId()).isEqualTo("TK-1");
    assertThat(match.get().faceId()).isEqualTo(faceId);
    assertThat(sim.searchFace("evt-1", face(2), 90)).isEmpty();
    // Outro evento não enxerga a collection deste.
    assertThat(sim.searchFace("evt-2", face(1), 90)).isEmpty();
  }

  @Test
  void fotoSemRostoOuComVariosERecusada() {
    SimulatedFaceRecognition sim = new SimulatedFaceRecognition();
    sim.ensureCollection("evt-1");
    assertThatThrownBy(() -> sim.indexFace("evt-1", "TK-1", new byte[10]))
        .isInstanceOf(FacialException.class)
        .extracting(e -> ((FacialException) e).status())
        .isEqualTo(422);
    byte[] multi = face(3);
    System.arraycopy("MULTI".getBytes(StandardCharsets.US_ASCII), 0, multi, 0, 5);
    assertThatThrownBy(() -> sim.indexFace("evt-1", "TK-1", multi)).hasMessageContaining("mais de um rosto");
  }

  @Test
  void apagarRostoEColecao() {
    SimulatedFaceRecognition sim = new SimulatedFaceRecognition();
    sim.ensureCollection("evt-1");
    String faceId = sim.indexFace("evt-1", "TK-1", face(1));
    sim.indexFace("evt-1", "TK-2", face(2));
    sim.deleteFaces("evt-1", List.of(faceId));
    assertThat(sim.size("evt-1")).isEqualTo(1);
    assertThat(sim.searchFace("evt-1", face(1), 90)).isEmpty();
    sim.deleteCollection("evt-1");
    assertThat(sim.size("evt-1")).isZero();
    // Apagar o que não existe não quebra.
    sim.deleteFaces("evt-x", List.of("nada"));
    sim.deleteCollection("evt-x");
  }

  @Test
  void indexarSemCollectionFalha() {
    assertThatThrownBy(() -> new SimulatedFaceRecognition().indexFace("evt-9", "TK-1", face(1)))
        .isInstanceOf(FacialException.class);
  }
}
