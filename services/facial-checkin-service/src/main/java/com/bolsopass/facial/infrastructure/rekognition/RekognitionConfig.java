package com.bolsopass.facial.infrastructure.rekognition;

import com.bolsopass.facial.application.FacialProperties;
import com.bolsopass.facial.domain.port.FaceRecognition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;

/**
 * Escolhe o adaptador: {@code facial.rekognition.mode=aws} usa o Rekognition real; qualquer outro
 * valor (padrão: simulated) usa o simulado, sem precisar de conta AWS.
 */
@Configuration
public class RekognitionConfig {

  @Bean
  @ConditionalOnProperty(name = "facial.rekognition.mode", havingValue = "aws")
  FaceRecognition awsFaceRecognition(FacialProperties props) {
    RekognitionClient client = RekognitionClient.builder().region(Region.of(props.rekognition().region())).build();
    return new AwsRekognitionFaceRecognition(client, props.rekognition().collectionPrefix());
  }

  @Bean
  @ConditionalOnMissingBean(FaceRecognition.class)
  FaceRecognition simulatedFaceRecognition() {
    return new SimulatedFaceRecognition();
  }
}
