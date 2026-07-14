package com.cardsync.domain.repository;

import com.cardsync.domain.model.UserDirectoryEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDirectoryRepository extends JpaRepository<UserDirectoryEntity, UUID> {
}
