package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import java.time.OffsetDateTime;

public record FileBrowserItemModel(
  String name,
  long size,
  OffsetDateTime lastModified
) {
}
