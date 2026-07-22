package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.input.BackupExecuteInput;
import com.cardsync.core.backup.BackupService;
import com.cardsync.core.security.CheckSecurity;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/settings/backup")
public class BackupController {

  private static final DateTimeFormatter FILENAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  private final BackupService backupService;

  @PostMapping("/execute")
  @CheckSecurity.Settings.Backup.CanProcess
  public ResponseEntity<Resource> execute(@Valid @RequestBody BackupExecuteInput input) {
    byte[] zip = backupService.execute(input.targets());

    String filename = "cardsync-backup-" + LocalDateTime.now().format(FILENAME_TIMESTAMP) + ".zip";
    ContentDisposition disposition = ContentDisposition.attachment()
      .filename(filename, StandardCharsets.UTF_8)
      .build();

    return ResponseEntity.ok()
      .contentType(MediaType.APPLICATION_OCTET_STREAM)
      .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
      .body(new ByteArrayResource(zip));
  }
}
