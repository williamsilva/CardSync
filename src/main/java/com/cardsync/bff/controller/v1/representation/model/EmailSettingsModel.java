package com.cardsync.bff.controller.v1.representation.model;

public record EmailSettingsModel(
  String impl,
  String fromName,
  String fromEmail,
  String brevoApiKey,
  String brevoBaseUrl
) {}
