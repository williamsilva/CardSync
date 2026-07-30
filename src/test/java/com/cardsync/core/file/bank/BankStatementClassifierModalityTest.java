package com.cardsync.core.file.bank;

import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.BankingDomicileRepository;
import com.cardsync.domain.repository.EstablishmentRepository;
import com.cardsync.domain.repository.FlagRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre a classificação de modalidade de PIX em BankStatementClassifierService#classify —
 * antes, recebimento/pagamento PIX caía em CASH_DEBIT/CASH_CREDIT (cartão) por coincidência de
 * texto (o marcador "PIX_CRED"/"PIX_DEB" do Sicredi contém um "CRED"/"DEB" isolado válido para
 * isCreditSignal/isDebitSignal), reportado pelo usuário na tela de Extrato Bancário. Agora PIX é
 * resolvido antes, com os códigos próprios PIX_REC(3)/PIX_ENV(5) — ver também
 * ReleasesBankSpecs#getModalityPaymentBank (precisou incluir os dois códigos, senão o PIX
 * desaparece da listagem).
 */
class BankStatementClassifierModalityTest {

  private final FlagRepository flagRepository = mock(FlagRepository.class);
  private final AcquirerRepository acquirerRepository = mock(AcquirerRepository.class);
  private final EstablishmentRepository establishmentRepository = mock(EstablishmentRepository.class);
  private final BankingDomicileRepository bankingDomicileRepository = mock(BankingDomicileRepository.class);
  private final BankStatementClassifierService service = new BankStatementClassifierService(
    flagRepository,
    acquirerRepository,
    new BankTextSignalResolver(),
    new BankingDomicileResolver(bankingDomicileRepository),
    establishmentRepository
  );

  @Test
  void classifiesPixReceiptAsPixReceivedInsteadOfCreditCard() {
    when(flagRepository.findAll()).thenReturn(List.of());
    when(acquirerRepository.findAll()).thenReturn(List.of());

    BankStatementClassification classification = service.classify(
      "RECEBIMENTO PIX PIX_CRED 10424623722 LUANA ALVES CARVA",
      null, null, null, Cnab240BankLayout.SICREDI, 0
    );

    assertThat(classification.getModalityPaymentBank()).isEqualTo(3);
  }

  @Test
  void classifiesPixPaymentAsPixSentInsteadOfDebitCard() {
    when(flagRepository.findAll()).thenReturn(List.of());
    when(acquirerRepository.findAll()).thenReturn(List.of());

    BankStatementClassification classification = service.classify(
      "PAGAMENTO PIX PIX_DEB 39303847000180 ACQUAMANIA MULTIPLO LAZER SA",
      null, null, null, Cnab240BankLayout.SICREDI, 0
    );

    assertThat(classification.getModalityPaymentBank()).isEqualTo(5);
  }

  @Test
  void stillClassifiesRealCardSalesAsDebitOrCredit() {
    when(flagRepository.findAll()).thenReturn(List.of());
    when(acquirerRepository.findAll()).thenReturn(List.of());

    BankStatementClassification debitSale = service.classify(
      "REDE DEBITO MASTER 855845600 |0001-80",
      null, null, null, Cnab240BankLayout.SICREDI, 0
    );
    BankStatementClassification creditSale = service.classify(
      "REDE CREDITO MASTER 855840116 |0001-80",
      null, null, null, Cnab240BankLayout.SICREDI, 0
    );

    assertThat(debitSale.getModalityPaymentBank()).isEqualTo(1);
    assertThat(creditSale.getModalityPaymentBank()).isEqualTo(2);
  }
}
