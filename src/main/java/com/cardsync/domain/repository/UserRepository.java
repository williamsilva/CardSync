package com.cardsync.domain.repository;

import com.cardsync.domain.model.UserEntity;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

  boolean existsByDocument(String document);
  boolean existsByUserNameIgnoreCase(String userName);
  boolean existsByDocumentAndIdNot(String document, UUID id);
  boolean existsByUserNameIgnoreCaseAndIdNot(String userName, UUID id);

  List<UserEntity> findAllByUserNameNotIgnoreCase(String userName, Sort sort);

  @EntityGraph(attributePaths = {"groups"})
  Optional<UserEntity> findDetailedById(UUID id);

  @EntityGraph(attributePaths = {"groups"})
  List<UserEntity> findDetailedByIdIn(Collection<UUID> ids);

  @EntityGraph(attributePaths = {"groups", "groups.permissions"})
  Optional<UserEntity> findByUserNameIgnoreCase(String username);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
    update UserEntity u
       set u.lastLoginAt = :now,
           u.failedAttempts = 0,
           u.blockedUntil = null
     where u.id = :id
  """)
  void markLoginSuccess(@Param("id") UUID id, @Param("now") OffsetDateTime now);

}
