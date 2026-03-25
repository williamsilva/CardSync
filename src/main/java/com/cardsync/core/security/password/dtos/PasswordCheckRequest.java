package com.cardsync.core.security.password.dtos;

/**
 * username é opcional e não deve ser exibido em tela.
 * Serve para regras como "não conter username" e validações dependentes do usuário.
 */
public record PasswordCheckRequest(String password, String confirmPassword, String username) {}
