package com.bolsopass.orders;

/** Validação de CPF (dígitos verificadores), igual à do front. */
public final class Cpf {
  private Cpf() {}

  public static String digits(String value) {
    return value == null ? "" : value.replaceAll("\\D", "");
  }

  public static boolean isValid(String value) {
    String cpf = digits(value);
    if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) return false;
    return digit(cpf, 9) == cpf.charAt(9) - '0' && digit(cpf, 10) == cpf.charAt(10) - '0';
  }

  private static int digit(String cpf, int len) {
    int sum = 0;
    for (int i = 0; i < len; i++) sum += (cpf.charAt(i) - '0') * (len + 1 - i);
    int r = (sum * 10) % 11;
    return r == 10 ? 0 : r;
  }
}
