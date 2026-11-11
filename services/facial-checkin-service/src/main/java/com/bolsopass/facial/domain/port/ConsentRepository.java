package com.bolsopass.facial.domain.port;

import com.bolsopass.facial.domain.model.BiometricConsent;
import java.util.Optional;

public interface ConsentRepository {
  Optional<BiometricConsent> findById(String userId);

  BiometricConsent save(BiometricConsent consent);
}
