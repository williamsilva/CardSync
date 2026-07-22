package com.cardsync.core.backup;

import com.cardsync.infrastructure.nimbusauth.NimbusAuthInternalClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre a orquestração de BackupService: só os alvos pedidos são executados, e uma falha num
 * alvo não impede os demais de aparecer no zip final (entra como uma linha em erros.txt).
 */
class BackupServiceTest {

  private final PgDumpRunner pgDumpRunner = mock(PgDumpRunner.class);
  private final FileVolumeZipper fileVolumeZipper = mock(FileVolumeZipper.class);
  private final NimbusAuthInternalClient nimbusAuthInternalClient = mock(NimbusAuthInternalClient.class);

  private final BackupService service = new BackupService(pgDumpRunner, fileVolumeZipper, nimbusAuthInternalClient);

  @Test
  void onlyRunsRequestedTargets() throws IOException {
    when(pgDumpRunner.dump()).thenReturn("cardsync-dump".getBytes(StandardCharsets.UTF_8));

    byte[] zip = service.execute(List.of(BackupTarget.CARDSYNC_DB));

    Map<String, byte[]> entries = readZipEntries(zip);
    assertThat(entries).containsKey("cardsync.dump");
    assertThat(entries).doesNotContainKeys("nimbusauth.dump", "erros.txt");
    verify(nimbusAuthInternalClient, never()).fetchDatabaseBackup();
    verify(fileVolumeZipper, never()).zipInto(any(), any());
  }

  @Test
  void aFailingTargetDoesNotPreventTheOthersFromSucceeding() throws IOException {
    when(pgDumpRunner.dump()).thenReturn("cardsync-dump".getBytes(StandardCharsets.UTF_8));
    doThrow(new IllegalStateException("NimbusAuth indisponível"))
      .when(nimbusAuthInternalClient).fetchDatabaseBackup();

    byte[] zip = service.execute(List.of(BackupTarget.CARDSYNC_DB, BackupTarget.NIMBUSAUTH_DB));

    Map<String, byte[]> entries = readZipEntries(zip);
    assertThat(entries).containsKey("cardsync.dump");
    assertThat(entries).doesNotContainKey("nimbusauth.dump");
    assertThat(new String(entries.get("erros.txt"), StandardCharsets.UTF_8))
      .contains("NimbusAuth indisponível");
  }

  @Test
  void allThreeTargetsProduceTheirOwnEntries() throws IOException {
    when(pgDumpRunner.dump()).thenReturn("cardsync-dump".getBytes(StandardCharsets.UTF_8));
    when(nimbusAuthInternalClient.fetchDatabaseBackup()).thenReturn("nimbusauth-dump".getBytes(StandardCharsets.UTF_8));

    byte[] zip = service.execute(List.of(BackupTarget.CARDSYNC_DB, BackupTarget.NIMBUSAUTH_DB, BackupTarget.FILES));

    Map<String, byte[]> entries = readZipEntries(zip);
    assertThat(entries).containsKeys("cardsync.dump", "nimbusauth.dump");
    verify(fileVolumeZipper).zipInto(any(), eq("arquivos"));
  }

  private Map<String, byte[]> readZipEntries(byte[] zipBytes) throws IOException {
    Map<String, byte[]> result = new HashMap<>();
    try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zipIn.getNextEntry()) != null) {
        result.put(entry.getName(), zipIn.readAllBytes());
      }
    }
    return result;
  }
}
