package com.bolsopass.facial.infrastructure.persistence;

import com.bolsopass.facial.domain.model.FaceEnrollment;
import com.bolsopass.facial.domain.port.EnrollmentRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/** Implementação JPA da porta {@link EnrollmentRepository} (Spring Data gera o código). */
public interface JpaEnrollmentRepository extends JpaRepository<FaceEnrollment, String>, EnrollmentRepository {}
