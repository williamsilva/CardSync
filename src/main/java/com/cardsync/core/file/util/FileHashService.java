package com.cardsync.core.file.util;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class FileHashService {
  private static final int BUFFER_SIZE = 8192;

  /**
   * Calcula o SHA-256 sobre os bytes exatos do arquivo.
   * O nome e o caminho não participam da assinatura.
   */
  public String sha256(Path file) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[BUFFER_SIZE];

      try (InputStream input = Files.newInputStream(file)) {
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
          digest.update(buffer, 0, bytesRead);
        }
      }

      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException ex) {
      throw new IllegalStateException("Não foi possível calcular o hash do arquivo " + file.getFileName(), ex);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Algoritmo SHA-256 não disponível.", ex);
    }
  }

  /**
   * Calcula o SHA-256 do arquivo inteiro, mascarando (substituindo por '0') faixas de bytes 0-based
   * DENTRO DA PRIMEIRA LINHA (Header) — usado quando a adquirente reenvia o mesmo conteúdo de
   * negócio com alguns campos do Header diferentes (ex.: Cielo reenviando o mesmo dia com uma nova
   * data/sequência de geração no Header, achado real: dois arquivos
   * CIELO03D_..._20260502_20260502_20260502.TXT e ..._20260502_20260502_20260526.TXT com as
   * mesmas linhas de venda byte a byte, só a data de geração e a sequência do Header diferentes).
   *
   * Diferente de ignorar a linha inteira: mascarar só os campos que variam em reenvio preserva a
   * unicidade de arquivos genuinamente diferentes cujo CORPO é trivialmente igual (ex.: dois dias
   * sem nenhuma venda — Header+Trailer só, trailer genérico zerado idêntico entre dias distintos;
   * achado real ao testar a versão "ignora linha inteira" — colidia ~800 arquivos modernos de dias
   * sem movimento). As faixas fora do Header (ex.: data inicial/final da janela do arquivo) e o
   * corpo continuam no hash, então dias diferentes continuam distintos.
   *
   * @param maskedRanges pares [start0, end0), [start1, end1), ... (0-based, exclusive no fim),
   *                      relativos ao início do arquivo — só têm efeito se caírem dentro da 1ª linha.
   */
  public String sha256MaskingFirstLineRanges(Path file, int... maskedRanges) {
    try {
      byte[] allBytes = Files.readAllBytes(file);
      int firstLineEnd = allBytes.length;
      for (int i = 0; i < allBytes.length; i++) {
        if (allBytes[i] == '\n') {
          firstLineEnd = i;
          break;
        }
      }

      byte[] masked = allBytes.clone();
      for (int r = 0; r + 1 < maskedRanges.length; r += 2) {
        int start = Math.max(maskedRanges[r], 0);
        int end = Math.min(maskedRanges[r + 1], firstLineEnd);
        for (int i = start; i < end; i++) {
          masked[i] = '0';
        }
      }

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(masked);
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException ex) {
      throw new IllegalStateException("Não foi possível calcular o hash do arquivo " + file.getFileName(), ex);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Algoritmo SHA-256 não disponível.", ex);
    }
  }
}
