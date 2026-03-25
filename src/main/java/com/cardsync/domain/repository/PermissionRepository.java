package com.cardsync.domain.repository;

import com.cardsync.domain.model.PermissionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<PermissionEntity, UUID> {

  List<PermissionEntity> findAllByNameNotIgnoreCaseOrderByNameAsc(String name);

}
