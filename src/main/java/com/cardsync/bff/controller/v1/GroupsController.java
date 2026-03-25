package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.mapper.model.GroupModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.GroupInput;
import com.cardsync.bff.controller.v1.representation.input.GroupPermissionsInput;
import com.cardsync.bff.controller.v1.representation.input.GroupUsersInput;
import com.cardsync.bff.controller.v1.representation.model.GroupModel;
import com.cardsync.bff.controller.v1.representation.model.GroupOptionModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.GroupsFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.model.GroupEntity;
import com.cardsync.domain.service.GroupService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/groups")
public class GroupsController {

  private final GroupService service;
  private final GroupModelAssembler groupModelAssembler;
  private final PagedResourcesAssembler<GroupEntity> pagedResourcesAssembler;

  @GetMapping("/options")
  @CheckSecurity.Authenticated
  public List<GroupOptionModel> list() {
    return service.listSelectableGroups();
  }

  @GetMapping("/{id}")
  @CheckSecurity.Authenticated
  public GroupModel get(@PathVariable UUID id) {
    GroupEntity entity = service.getById(id);
    return groupModelAssembler.toModel(entity);
  }

  @PostMapping("/search")
  @CheckSecurity.Security.Groups.CanConsult
  public PagedModel<GroupModel> search(@RequestBody ListQueryDto<GroupsFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());
    var page = service.list(pageable, body);
    return pagedResourcesAssembler.toModel(page, groupModelAssembler);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.Security.Groups.CanCreate
  public GroupModel create(@Valid @RequestBody GroupInput body) {
    GroupEntity created = service.create(body);
    return groupModelAssembler.toModel(created);
  }

  @PutMapping("/{id}")
  @CheckSecurity.Security.Groups.CanUpdate
  public GroupModel update(@PathVariable UUID id, @Valid @RequestBody GroupInput body) {
    GroupEntity updatedGroup  = service.update(id, body);
    return groupModelAssembler.toModel(updatedGroup);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.Security.Groups.CanDelete
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  @PutMapping("/{id}/permissions")
  @CheckSecurity.Security.Groups.CanUpdatePermissions
  public GroupModel updatePermissions(@PathVariable UUID id, @Valid @RequestBody GroupPermissionsInput body) {
    return groupModelAssembler.toModel(service.updatePermissions(id, body.permissionIds()));
  }

  @PutMapping("/{id}/users")
  @CheckSecurity.Security.Groups.CanUpdateUsers
  public GroupModel updateUsers(@PathVariable UUID id, @Valid @RequestBody GroupUsersInput body) {
    return groupModelAssembler.toModel(service.updateUsers(id, body.userIds()));
  }
}
