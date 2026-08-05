package com.cardsync.core.file.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileHashServiceTest {

  private final FileHashService service = new FileHashService();

  @TempDir
  Path tempDir;

  @Test
  void ignoresDifferencesInsideMaskedHeaderRanges() throws IOException {
    // Mesmo achado real: 2 arquivos com o mesmo corpo, só a data de geração (11-19) e a sequência
    // (35-42) do Header diferentes — devem hashear igual.
    Path file1 = write("h1.txt", "010515831172026050220260502202605020001172CIELO03I\nE-linha-de-venda\n9-trailer\n");
    Path file2 = write("h2.txt", "010515831172026052620260502202605020001196CIELO03I\nE-linha-de-venda\n9-trailer\n");

    assertThat(service.sha256MaskingFirstLineRanges(file1, 11, 19, 35, 42))
      .isEqualTo(service.sha256MaskingFirstLineRanges(file2, 11, 19, 35, 42));
  }

  @Test
  void doesNotCollideDifferentDaysWithIdenticalEmptyBody() throws IOException {
    // Bug real evitado: 2 dias sem movimento (Header+Trailer só, trailer genérico idêntico) NÃO
    // podem colidir — a data inicial/final (19-35, fora da máscara) diferencia os dois.
    Path day1 = write("day1.txt", "010515831172026050220250719202507190001172CIELO03I\n9-trailer-generico-zerado\n");
    Path day2 = write("day2.txt", "010515831172026050220250720202507200001172CIELO03I\n9-trailer-generico-zerado\n");

    assertThat(service.sha256MaskingFirstLineRanges(day1, 11, 19, 35, 42))
      .isNotEqualTo(service.sha256MaskingFirstLineRanges(day2, 11, 19, 35, 42));
  }

  @Test
  void detectsGenuinelyDifferentBodiesAsDifferent() throws IOException {
    Path file1 = write("b1.txt", "010515831172026050220260502202605020001172CIELO03I\nE-venda-A\n9-trailer\n");
    Path file2 = write("b2.txt", "010515831172026050220260502202605020001172CIELO03I\nE-venda-B\n9-trailer\n");

    assertThat(service.sha256MaskingFirstLineRanges(file1, 11, 19, 35, 42))
      .isNotEqualTo(service.sha256MaskingFirstLineRanges(file2, 11, 19, 35, 42));
  }

  private Path write(String name, String content) throws IOException {
    Path path = tempDir.resolve(name);
    Files.writeString(path, content, StandardCharsets.ISO_8859_1);
    return path;
  }
}
