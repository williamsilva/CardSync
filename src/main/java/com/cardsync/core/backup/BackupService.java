package com.cardsync.core.backup;

import com.cardsync.infrastructure.nimbusauth.NimbusAuthInternalClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquestra o backup sob demanda (tela de Configurações): monta um único zip com os alvos
 * pedidos (banco cardsync, banco nimbusauth e/ou pasta de arquivos). Um alvo que falhar não
 * derruba os demais — entra como uma linha em erros.txt dentro do próprio zip, mesmo padrão de
 * isolamento por item já usado na conciliação bancária (uma falha isolada não deve descartar o
 * que já deu certo).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

  private final PgDumpRunner pgDumpRunner;
  private final FileVolumeZipper fileVolumeZipper;
  private final NimbusAuthInternalClient nimbusAuthInternalClient;

  public byte[] execute(List<BackupTarget> targets) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    List<String> errors = new ArrayList<>();

    try (ZipOutputStream zipOut = new ZipOutputStream(buffer)) {
      if (targets.contains(BackupTarget.CARDSYNC_DB)) {
        addEntry(zipOut, "cardsync.dump", "banco cardsync", pgDumpRunner::dump, errors);
      }
      if (targets.contains(BackupTarget.NIMBUSAUTH_DB)) {
        addEntry(zipOut, "nimbusauth.dump", "banco nimbusauth", nimbusAuthInternalClient::fetchDatabaseBackup, errors);
      }
      if (targets.contains(BackupTarget.FILES)) {
        try {
          fileVolumeZipper.zipInto(zipOut, "arquivos");
        } catch (Exception e) {
          log.warn("Falha ao compactar a pasta de arquivos para o backup: {}", e.getMessage(), e);
          errors.add("Pasta de arquivos: " + e.getMessage());
        }
      }

      if (!errors.isEmpty()) {
        zipOut.putNextEntry(new ZipEntry("erros.txt"));
        zipOut.write(String.join("\n", errors).getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();
      }
    } catch (IOException e) {
      throw new IllegalStateException("Falha ao montar o arquivo zip do backup", e);
    }

    return buffer.toByteArray();
  }

  private void addEntry(
    ZipOutputStream zipOut, String fileName, String label, Supplier<byte[]> action, List<String> errors
  ) {
    try {
      byte[] bytes = action.get();
      zipOut.putNextEntry(new ZipEntry(fileName));
      zipOut.write(bytes);
      zipOut.closeEntry();
    } catch (Exception e) {
      log.warn("Falha ao gerar backup de {}: {}", label, e.getMessage(), e);
      errors.add(label + ": " + e.getMessage());
    }
  }
}
