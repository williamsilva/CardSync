package com.cardsync.bff.controller.v1.representation.model;

public record EmailSettingsModel(
  String impl,
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
