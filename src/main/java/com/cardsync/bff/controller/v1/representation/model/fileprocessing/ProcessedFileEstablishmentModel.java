package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import java.util.UUID;

public record ProcessedFileEstablishmentModel(Integer pvNumber, String companyName, UUID companyId) {
}
