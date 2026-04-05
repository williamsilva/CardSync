package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.UsersFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.spec.UserAllowedFields;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.model.enums.StatusUserEnum;
import java.util.UUID;

import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class UserSpecs extends BaseSpecificationSupport<UserEntity> {

  private final UserAllowedFields userAllowedFields;
  private final SpecificationFactory specificationFactory;

  public UserSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    UserAllowedFields userAllowedFields
  ) {
    super(dateFilterService);
    this.userAllowedFields = userAllowedFields;
    this.specificationFactory = specificationFactory;
  }

  public Specification<UserEntity> fromQuery(ListQueryDto<UsersFilter> query) {
    Specification<UserEntity> spec = Specs.all();

    spec = spec.and(
      specificationFactory.fromTableFilters(
        query.tableFilters(),
        userAllowedFields.table()
      )
    );

    if (query.advanced() != null) {
      var a = query.advanced();

      spec = spec.and(contains("name", a.name()));
      spec = spec.and(contains("userName", a.userName()));
      spec = spec.and(contains("document", a.document()));
      spec = spec.and(rangeOdt("createdAt", a.createdAtFrom(), a.createdAtTo()));

      spec = spec.and(
        inPath(a.createdBy(), value -> {
          try {
            return UUID.fromString(value);
          } catch (Exception e) {
            return null;
          }
        }, "createdBy", "id")
      );

      spec = spec.and(inCodes("status", a.status(), StatusUserEnum::getCode));
      spec = spec.and(rangeOdt("createdAt", a.createdAtFrom(), a.createdAtTo()));
      spec = spec.and(rangeOdt("lastLoginAt", a.lastLoginAtFrom(), a.lastLoginAtTo()));
      spec = spec.and(rangeOdt("blockedUntil", a.blockedUntilFrom(), a.blockedUntilTo()));
      spec = spec.and(rangeOdt("passwordExpiresAt", a.passwordExpiresAtFrom(), a.passwordExpiresAtTo()));
    }

    if (query.globalFilter() != null && !query.globalFilter().trim().isEmpty()) {
      String gf = query.globalFilter().trim();

      spec = spec.and(
        contains("name", gf)
          .or(contains("userName", gf))
          .or(contains("document", gf))
      );
    }
    spec = spec.and(orderByAsc("name"));
    return spec;
  }

}
