package com.cardsync.core.file.service.report;

import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileErrorModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileSummaryModel;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.ProcessedFileEntity;
import com.cardsync.domain.model.ProcessedFileErrorEntity;
import com.cardsync.domain.repository.ProcessedFileErrorRepository;
import com.cardsync.domain.repository.ProcessedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileProcessingReportService {

  private final ProcessedFileRepository processedFileRepository;
  private final ProcessedFileErrorRepository processedFileErrorRepository;

  @Transactional(readOnly = true)
  public Page<ProcessedFileModel> list(Pageable pageable) {
    return processedFileRepository.findAll(pageable).map(this::toModel);
  }

  @Transactional(readOnly = true)
  public ProcessedFileModel find(UUID processedFileId) {
    return toModel(load(processedFileId));
  }

  @Transactional(readOnly = true)
  public ProcessedFileSummaryModel summary(UUID processedFileId) {
    ProcessedFileEntity file = load(processedFileId);
    return new ProcessedFileSummaryModel(
      file.getId(),
      file.getFile(),
      file.getStatus(),
      file.getStartedAt(),
      file.getFinishedAt(),
      file.getTotalLines(),
      file.getProcessedLines(),
      file.getIgnoredLines(),
      file.getWarningLines(),
      file.getErrorLines(),
      file.getPendingContractLines(),
      file.getPendingBusinessContextLines(),
      file.getStatusMessage(),
      file.getErrorMessage()
    );
  }

  @Transactional(readOnly = true)
  public List<ProcessedFileErrorModel> listErrors(UUID processedFileId) {
    load(processedFileId);
    return processedFileErrorRepository.findByProcessedFile_IdOrderByLineNumberAsc(processedFileId)
      .stream()
      .map(this::toErrorModel)
      .toList();
  }

  private ProcessedFileEntity load(UUID processedFileId) {
    return processedFileRepository.findById(processedFileId)
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "Arquivo processado não encontrado: " + processedFileId));
  }

  private ProcessedFileModel toModel(ProcessedFileEntity file) {
    return new ProcessedFileModel(
      file.getId(),
      file.getFile(),
      file.getOriginFile() != null ? file.getOriginFile().getCode() : null,
      file.getGroup(),
      file.getStatus(),
      file.getDateFile(),
      file.getDateImport(),
      file.getStartedAt(),
      file.getFinishedAt(),
      file.getTotalLines(),
      file.getProcessedLines(),
      file.getIgnoredLines(),
      file.getWarningLines(),
      file.getErrorLines(),
      file.getPendingContractLines(),
      file.getPendingBusinessContextLines(),
      file.getStatusMessage(),
      file.getErrorMessage()
    );
  }

  private ProcessedFileErrorModel toErrorModel(ProcessedFileErrorEntity error) {
    return new ProcessedFileErrorModel(
      error.getId(),
      error.getLineNumber(),
      error.getErrorType(),
      error.getErrorCode(),
      error.getMessage(),
      error.getRawLine(),
      error.getCreatedAt()
    );
  }
}
