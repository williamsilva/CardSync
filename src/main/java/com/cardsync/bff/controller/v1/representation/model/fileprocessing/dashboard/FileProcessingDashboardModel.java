package com.cardsync.bff.controller.v1.representation.model.fileprocessing.dashboard;

import java.util.List;

public record FileProcessingDashboardModel(
  List<FileProcessingMetricModel> cards,
  List<FileProcessingStatusCountModel> filesByStatus,
  List<ReconciliationStatusAmountModel> reconciliationByStatus,
  List<FileProcessingDivergenceContextModel> divergenceContexts,
  List<FileProcessingTopErrorFileModel> topFilesWithErrors
) {}
