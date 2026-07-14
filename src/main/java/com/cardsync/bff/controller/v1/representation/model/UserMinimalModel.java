package com.cardsync.bff.controller.v1.representation.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Resumo de usuário (id/nome/username) resolvido via UserDirectoryService (cache local
 * sincronizado sob demanda com o NimbusAuth) - usado só para exibir "criado por"/"alterado por".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserMinimalModel {
  private UUID id;
  private String name;
  private String userName;
}
