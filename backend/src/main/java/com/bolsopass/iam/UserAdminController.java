package com.bolsopass.iam;

import com.bolsopass.iam.UserAdminService.NewUser;
import com.bolsopass.iam.UserAdminService.Profile;
import com.bolsopass.iam.UserAdminService.UserPage;
import com.bolsopass.iam.UserAdminService.UserResult;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Página "Usuários" da administração. Só ROLE_ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {
  private final UserAdminService service;

  public UserAdminController(UserAdminService service) {
    this.service = service;
  }

  public record ProfileChange(Profile profile) {}

  @GetMapping
  UserPage list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Profile profile,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(q, profile, from, to, page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  UserResult create(@RequestBody NewUser body) {
    return service.create(body, CurrentUser.id());
  }

  @PatchMapping("/{id}/profile")
  UserResult changeProfile(@PathVariable String id, @RequestBody ProfileChange body) {
    return service.changeProfile(id, body.profile(), CurrentUser.id());
  }
}
