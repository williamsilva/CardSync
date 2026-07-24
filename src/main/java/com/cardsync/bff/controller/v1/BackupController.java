package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.input.BackupExecuteInput;
import com.cardsync.core.backup.BackupService;
import com.cardsync.core.security.CheckSecurity;
import jakarta.validation.Valid;
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

    // Nome sempre ASCII (prefixo fixo + timestamp) — sem Charset o Spring emite um filename=
    // simples, em vez do encoded-word RFC 2047 (=?UTF-8?Q?...?=) que o navegador decodifica
    // nativamente numa navegação direta, mas que o parser em JS do frontend (blob download,
    // já que aqui a resposta é o corpo de um POST, não uma URL navegável) não entende.
    String filename = "backup_nb_" + LocalDateTime.now().format(FILENAME_TIMESTAMP) + ".zip";
    ContentDisposition disposition = ContentDisposition.attachment()
      .filename(filename)
      .build();

    return ResponseEntity.ok()
      .contentType(MediaType.APPLICATION_OCTET_STREAM)
      .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
      .body(new ByteArrayResource(zip));
  }
}
