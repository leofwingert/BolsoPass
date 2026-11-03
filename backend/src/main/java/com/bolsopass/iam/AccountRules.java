package com.bolsopass.iam;

import com.bolsopass.shared.ApiException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras de cadastro usadas em todos os pontos que criam ou alteram contas: cadastro público,
 * administração (página Usuários) e cadastro rápido no balcão. Cada método valida e devolve o
 * valor já normalizado, ou lança 400 com a mensagem para o usuário.
 */
public final class AccountRules {
  public static final int MIN_PASSWORD = 8;
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private AccountRules() {}

  /** Nome e sobrenome, com espaços repetidos removidos. */
  public static String fullName(String name) {
    String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
    if (n.split(" ").length < 2) throw ApiException.badRequest("Informe nome e sobrenome");
    return n;
  }

  /** E-mail em minúsculas. */
  public static String email(String email) {
    String e = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    if (!EMAIL.matcher(e).matches()) throw ApiException.badRequest("E-mail inválido");
    return e;
  }

  /** Telefone com DDD (10 ou 11 dígitos), como foi digitado. */
  public static String phone(String phone) {
    String p = phone == null ? "" : phone.trim();
    if (p.replaceAll("\\D", "").length() < 10) throw ApiException.badRequest("Informe um telefone com DDD");
    return p;
  }

  public static String password(String password) {
    if (password == null || password.length() < MIN_PASSWORD) {
      throw ApiException.badRequest("A senha precisa ter pelo menos " + MIN_PASSWORD + " caracteres");
    }
    return password;
  }
}
