package com.cardsync.api.internal.controller;

import com.cardsync.core.backup.FileVolumeZipper;
import com.nimbussystems.commons.legacy.backup.PgDumpRunner;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** API interna machine-to-machine (rota /internal/backup/**, ver InternalBackupSecretFilter) -
 *  consumida pelo NimbusAuth pra compor o backup centralizado do ecossistema Nimbus. Espelha
 *  exatamente o InternalBackupController já existente no NimbusAuthServer (que os satélites
 *  consomem hoje pra incluir o banco do NimbusAuth no próprio zip via
 *  BackupController/BackupService/NimbusAuthInternalClient) - agora na direção inversa. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InternalBackupController {

  private final PgDumpRunner pgDumpRunner;
  private final FileVolumeZipper fileVolumeZipper;

  @GetMapping("/internal/backup/database")
  public ResponseEntity<byte[]> database() {
    log.info("Backup do banco Cardsync solicitado via API interna.");
    byte[] dump = pgDumpRunner.dump();
    return ResponseEntity.ok()
      .contentType(MediaType.APPLICATION_OCTET_STREAM)
      .body(dump);
  }

  @GetMapping("/internal/backup/files")
  public void files(HttpServletResponse response) throws IOException {
    log.info("Backup de arquivos Cardsync solicitado via API interna.");
    response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cardsync-files.zip\"");

    try (ZipOutputStream zipOut = new ZipOutputStream(response.getOutputStream())) {
      fileVolumeZipper.zipInto(zipOut, "arquivos");
    }
  }
}
