package com.cardsync.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Cache local (read-only) do id/nome/username de usuários do NimbusAuth, usado só para
 * exibir "criado por"/"alterado por" nas respostas da API sem bater na rede a cada request.
 * Sem FK: o id aqui é o id do usuário no NimbusAuth, um sistema/banco separado.
 */
@Getter
@Setter
@Entity
@Table(name = "cs_user_directory")
public class UserDirectoryEntity {

  @Id
  private UUID id;

  @Column(name = "username", nullable = false, length = 120)
  private String username;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "synced_at", nullable = false)
  private OffsetDateTime syncedAt;
}
