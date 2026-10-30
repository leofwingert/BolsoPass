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

  protected User() {}

  public User(String id, String name, String email, String passwordHash, Set<Role> roles) {
    this.id = id;
    this.name = name;
    this.email = email.toLowerCase();
    this.passwordHash = passwordHash;
    this.roles = EnumSet.copyOf(roles);
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
}
