package com.cardsync.bff.controller.v1.representation.model;

public record EmailSettingsModel(
  String impl,
  /** false em produção (perfil Spring "prod") - a tela esconde a opção FAKE da lista quando este
   *  campo vem false, pra não deixar alguém sem querer "silenciar" o envio de e-mail real em
   *  produção (ver EmailSettingsService). Nunca calculado no frontend - depende do perfil ativo
   *  do processo, que só o backend sabe. */
  boolean allowFakeImpl,
  String fromName,
  String fromEmail,
  String brevoApiKey,
  String brevoBaseUrl,
  Integer brevoPort,
  String brevoUsername,
  String chargebackRecipients,
  String smtpHost,
  Integer smtpPort,
  String smtpUsername,
  String smtpPassword,
  Boolean smtpAuth,
  Boolean smtpStarttls,
  Boolean smtpSsl
) {}
