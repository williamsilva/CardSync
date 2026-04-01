package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.UserModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.ListIdsInput;
import com.cardsync.bff.controller.v1.representation.input.UserInput;
import com.cardsync.bff.controller.v1.representation.model.UserModel;
import com.cardsync.bff.controller.v1.representation.model.UserOptionModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.UsersFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/users")
public class UserController {

  private final UserService service;
  private final UserModelAssembler userModelAssembler;
  private final PagedResourcesAssembler<UserEntity> pagedResourcesAssembler;

  @GetMapping("/options")
  @CheckSecurity.Authenticated
  public List<UserOptionModel> listOptions() {
    return service.listOptions();
  }

  @PostMapping("/search")
  @CheckSecurity.Security.Users.CanConsult
  public PagedModel<UserModel> search(@RequestBody ListQueryDto<UsersFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = service.list(pageable, body);
    return pagedResourcesAssembler.toModel(page, userModelAssembler);
  }

  @GetMapping("/{id}")
  @CheckSecurity.Authenticated
  public UserModel get(@PathVariable UUID id) {
    UserEntity entity = service.getById(id);
    return userModelAssembler.toModel(entity);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.Security.Users.CanCreate
  public UserModel create(@Valid @RequestBody UserInput body, HttpServletRequest req) {
    String baseUrl = baseUrl(req);
    UserEntity created = service.create(body, baseUrl);

    return userModelAssembler.toModel(created);
  }

  @PutMapping("/{id}")
  @CheckSecurity.Security.Users.CanChange
  public UserModel update(@PathVariable UUID id, @Valid @RequestBody UserInput body) {
      UserEntity updatedUser  = service.update(id, body);
      return userModelAssembler.toModel(updatedUser);
  }

  @PostMapping("/{id}/activate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.Security.Users.CanActiveOrInactiveUser
  public void activate(@PathVariable UUID id) {
    service.activate(id);
  }

  @PostMapping("/{id}/deactivate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.Security.Users.CanActiveOrInactiveUser
  public void deactivate(@PathVariable UUID id, Authentication authentication) {
    service.deactivate(id, authentication);
  }

  @PostMapping("/activate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.Security.Users.CanActiveOrInactiveUser
  public void activateBulk(@Valid @RequestBody ListIdsInput body) {
    service.activateBulk(body.ids());
  }

  @PostMapping("/deactivate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.Security.Users.CanActiveOrInactiveUser
  public void deactivateBulk(@Valid @RequestBody ListIdsInput body, Authentication authentication) {
    service.deactivateBulk(body.ids(), authentication);
  }

  @PostMapping("/{id}/resend-invite")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.Security.Users.CanResendInvite
  public void resendInvite(@PathVariable UUID id, HttpServletRequest req) {
    service.resendInvite(id, baseUrl(req));
  }

  static String baseUrl(HttpServletRequest req) {
    String scheme = req.getScheme();
    String host = req.getServerName();
    int port = req.getServerPort();

    boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
      || ("https".equalsIgnoreCase(scheme) && port == 443);

    return defaultPort ? (scheme + "://" + host) : (scheme + "://" + host + ":" + port);
  }
}
