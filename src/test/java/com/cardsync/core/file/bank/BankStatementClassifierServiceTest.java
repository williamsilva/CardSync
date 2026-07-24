package com.cardsync.core.file.bank;

import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.repository.FlagRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre a correção do bug de bandeira errada no extrato bancário CNAB240 (Santander/Itaú/
 * Bradesco — Cnab240FileProcessor.buildRelease): resolveFlag casava, na falta de sinal
 * conhecido (Visa/Master/Elo/Amex), pelo erp_code da bandeira como substring solta no texto —
 * um código de 1-2 dígitos (ex.: American Express=3) quase sempre coincide com algum dígito do
 * PV/referência do lançamento (ex.: "867379"), então bandeiras sem sinal próprio (Cabal,
 * Banescard, Hipercard...) nunca eram avaliadas por nome: o primeiro erp_code batido por
 * coincidência na ordem de findAll() vencia antes.
 */
class BankStatementClassifierServiceTest {

  private final FlagRepository flagRepository = mock(FlagRepository.class);
  private final BankStatementClassifierService service = new BankStatementClassifierService(
    new BankTextSignalResolver(),
    null,
    null,
    null,
    flagRepository
  );

  @Test
  void resolvesCabalByNameInsteadOfFalsePositiveOnAmexErpCodeDigit() {
    // Mesma ordem de inserção da seed real (V20260516_11): Mastercard=1, Visa=2, American
    // Express=3, ..., Banescard=9, Cabal=10 — o "3" de American Express aparece dentro do PV
    // 867379, e Cabal só bateria depois na lista.
    when(flagRepository.findAll()).thenReturn(List.of(
      flag("Mastercard", 1),
      flag("Visa", 2),
      flag("American Express", 3),
      flag("Banescard", 9),
      flag("Cabal", 10)
    ));

    String normalized = new BankTextSignalResolver().normalize("PAGTO. C DEB 867379REDE-CABAL DEB");

    var resolved = service.resolveFlag(normalized);

    assertThat(resolved).isPresent();
    assertThat(resolved.get().getName()).isEqualTo("Cabal");
  }

  @Test
  void resolvesBanescardFromSantanderAbbreviatedSignalBanesc() {
    // Santander abrevia "Banescard" para "BANESC" no histórico (confirmado em arquivo real:
    // 22 ocorrências de "REDE-BANESC", nenhuma com o nome completo) — sem sinal próprio, o
    // casamento por substring de nome nunca bate ("BANESC" não contém "BANESCARD").
    when(flagRepository.findAll()).thenReturn(List.of(
      flag("Mastercard", 1),
      flag("Visa", 2),
      flag("American Express", 3),
      flag("Banescard", 9),
      flag("Cabal", 10)
    ));

    String normalized = new BankTextSignalResolver().normalize("PAGTO. C DEB 867379REDE-BANESC DEB");

    var resolved = service.resolveFlag(normalized);

    assertThat(resolved).isPresent();
    assertThat(resolved.get().getName()).isEqualTo("Banescard");
  }

  @Test
  void stillResolvesVisaByKnownSignalWhenPresent() {
    when(flagRepository.findAll()).thenReturn(List.of(
      flag("Mastercard", 1),
      flag("Visa", 2),
      flag("American Express", 3)
    ));

    String normalized = new BankTextSignalResolver().normalize("867379REDE-VISA CRED");

    var resolved = service.resolveFlag(normalized);

    assertThat(resolved).isPresent();
    assertThat(resolved.get().getName()).isEqualTo("Visa");
  }

  private FlagEntity flag(String name, int erpCode) {
    FlagEntity flag = new FlagEntity();
    flag.setId(UUID.randomUUID());
    flag.setName(name);
    flag.setErpCode(erpCode);
    return flag;
  }
}
