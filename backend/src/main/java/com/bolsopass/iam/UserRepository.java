package com.bolsopass.iam;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
  Optional<User> findByEmail(String email);

  Optional<User> findByBadge(String badge);

  /** CPF pode estar gravado com ou sem máscara: a busca recebe as duas formas. */
  List<User> findByCpfIn(Collection<String> cpfs);
}
