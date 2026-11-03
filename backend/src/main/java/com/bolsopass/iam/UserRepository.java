package com.bolsopass.iam;

import com.bolsopass.orders.Cpf;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
  Optional<User> findByEmail(String email);

  Optional<User> findByBadge(String badge);

  /** CPF pode estar gravado com ou sem máscara: a busca recebe as duas formas. */
  List<User> findByCpfIn(Collection<String> cpfs);

  /** Conta do CPF (gravado com ou sem máscara). CPF incompleto não encontra ninguém. */
  default Optional<User> findByCpf(String cpf) {
    String d = Cpf.digits(cpf);
    if (d.length() != 11) return Optional.empty();
    return findByCpfIn(List.of(d, Cpf.formatted(d))).stream().findFirst();
  }
}
