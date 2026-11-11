package com.bolsopass.facial.infrastructure.persistence;

import com.bolsopass.facial.domain.model.BiometricConsent;
import com.bolsopass.facial.domain.port.ConsentRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/** Implementação JPA da porta {@link ConsentRepository} (Spring Data gera o código). */
public interface JpaConsentRepository extends JpaRepository<BiometricConsent, String>, ConsentRepository {}
