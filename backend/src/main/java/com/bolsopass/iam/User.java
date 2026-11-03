package com.bolsopass.iam;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
  @Id private String id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  private String cpf;
  private String phone;

  /** Matrícula do operador de portaria. */
  @Column(unique = true)
  private String badge;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "role")
  private Set<Role> roles = EnumSet.noneOf(Role.class);

  /** PIN da bilheteria (matrícula + PIN), separado da senha do e-mail. Nulo = conta sem acesso à bilheteria. */
  private String pinHash;

  /**
   * Conta criada no balcão da bilheteria, sem senha conhecida: no cadastro público o dono do CPF
   * pode assumi-la. Colunas novas ficam opcionais para não quebrar tabelas que já existem.
   */
  private Boolean counterAccount;
  private String activationCodeHash;
  private Instant activationCodeExpiresAt;

  private Instant createdAt;

  protected User() {}

  public User(String id, String name, String email, String passwordHash, Set<Role> roles) {
    this.id = id;
    this.name = name;
    this.email = email.toLowerCase();
    this.passwordHash = passwordHash;
    this.roles = EnumSet.copyOf(roles);
    this.createdAt = Instant.now();
  }

  public User withContact(String cpf, String phone) {
    this.cpf = cpf;
    this.phone = phone;
    return this;
  }

  public User withBadge(String badge) {
    this.badge = badge;
    return this;
  }

  public User withPin(String pinHash) {
    this.pinHash = pinHash;
    return this;
  }

  public User markCounterAccount(String activationCodeHash, Instant activationCodeExpiresAt) {
    this.counterAccount = true;
    this.activationCodeHash = activationCodeHash;
    this.activationCodeExpiresAt = activationCodeExpiresAt;
    return this;
  }

  public boolean canBeClaimedWith(String activationCode, org.springframework.security.crypto.password.PasswordEncoder encoder) {
    return Boolean.TRUE.equals(counterAccount)
        && activationCode != null
        && activationCodeExpiresAt != null
        && activationCodeExpiresAt.isAfter(Instant.now())
        && activationCodeHash != null
        && encoder.matches(activationCode, activationCodeHash);
  }

  /** O dono do CPF assume a conta criada no balcão: define e-mail, senha e telefone. */
  public void claim(String name, String email, String passwordHash, String phone) {
    this.name = name;
    this.email = email.toLowerCase();
    this.passwordHash = passwordHash;
    this.phone = phone;
    this.counterAccount = false;
    this.activationCodeHash = null;
    this.activationCodeExpiresAt = null;
  }

  /** Troca o perfil: a conta tem um perfil só. Sem bilheteria, perde matrícula e PIN. */
  public void changeRoles(Set<Role> roles, String badge, String pinHash) {
    this.roles.clear();
    this.roles.addAll(roles);
    this.badge = badge;
    this.pinHash = pinHash;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getCpf() {
    return cpf;
  }

  public String getPhone() {
    return phone;
  }

  public String getBadge() {
    return badge;
  }

  public Set<Role> getRoles() {
    return roles;
  }

  public String getPinHash() {
    return pinHash;
  }

  /** E-mail automático das contas criadas no balcão sem e-mail informado. */
  public static final String COUNTER_EMAIL_DOMAIN = "@balcao.bolsopass.io";

  public boolean isCounterAccount() {
    // Contas do balcão criadas antes da coluna existir ficaram com null: o e-mail automático as identifica.
    return Boolean.TRUE.equals(counterAccount)
        || (counterAccount == null && email != null && email.endsWith(COUNTER_EMAIL_DOMAIN));
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
