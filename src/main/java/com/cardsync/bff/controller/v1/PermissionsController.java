package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.PermissionOptionModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.service.GroupService;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/bff/v1/permissions")
public class PermissionsController {

  private final GroupService groupService;

  @GetMapping("/options")
  @CheckSecurity.Authenticated
  public List<PermissionOptionModel> listOptions() {
    return groupService.listPermissionOptions();
  }
}
