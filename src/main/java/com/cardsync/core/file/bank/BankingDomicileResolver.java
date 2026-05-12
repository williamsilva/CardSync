package com.cardsync.core.file.bank;

import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.repository.BankingDomicileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BankingDomicileResolver {

  private final BankingDomicileRepository bankingDomicileRepository;

  public Optional<BankingDomicileEntity> resolve(Integer agency, Integer currentAccount, CompanyEntity company) {
    return resolve((String) null, agency, currentAccount, company);
  }

  /**
   * Resolve o domicílio bancário considerando os formatos que aparecem nos arquivos das adquirentes.
   *
   * Exemplo comum na Rede:
   * - Arquivo: conta = 214900
   * - Cadastro: currentAccount = 21490, accountDigit = 0
   *
   * Por isso este método tenta primeiro a conta direta e depois a conta separando o último dígito.
   */
  public Optional<BankingDomicileEntity> resolve(String bankCode, Integer agency, Integer currentAccount, CompanyEntity company) {
    if (agency == null || currentAccount == null) {
      return Optional.empty();
    }

    UUID companyId = company != null ? company.getId() : null;
    List<String> bankCodes = bankCodeCandidates(bankCode);
    List<AccountCandidate> accounts = accountCandidates(currentAccount);

    for (AccountCandidate account : accounts) {
      Optional<BankingDomicileEntity> found = findByPriority(bankCodes, agency, account, companyId);
      if (found.isPresent()) {
        return found;
      }
    }

    return Optional.empty();
  }

  public Optional<BankingDomicileEntity> resolveWithAccountDigit(
    String bankCode,
    Integer agency,
    Integer accountWithDigit,
    CompanyEntity company
  ) {
    return resolve(bankCode, agency, accountWithDigit, company);
  }

  private Optional<BankingDomicileEntity> findByPriority(
    List<String> bankCodes,
    Integer agency,
    AccountCandidate account,
    UUID companyId
  ) {
    Optional<BankingDomicileEntity> found;

    if (companyId != null) {
      for (String bankCode : bankCodes) {
        found = findWithBankAndCompany(bankCode, agency, account, companyId);
        if (found.isPresent()) {
          return found;
        }
      }

      found = findWithCompany(agency, account, companyId);
      if (found.isPresent()) {
        return found;
      }
    }

    for (String bankCode : bankCodes) {
      found = findWithBank(bankCode, agency, account);
      if (found.isPresent()) {
        return found;
      }
    }

    return findWithoutBankAndCompany(agency, account);
  }

  private Optional<BankingDomicileEntity> findWithBankAndCompany(
    String bankCode,
    Integer agency,
    AccountCandidate account,
    UUID companyId
  ) {
    if (account.accountDigit() == null) {
      return bankingDomicileRepository.findFirstByBank_CodeAndAgencyAndCurrentAccountAndCompany_Id(
        bankCode,
        agency,
        account.currentAccount(),
        companyId
      );
    }

    return bankingDomicileRepository.findFirstByBank_CodeAndAgencyAndCurrentAccountAndAccountDigitAndCompany_Id(
      bankCode,
      agency,
      account.currentAccount(),
      account.accountDigit(),
      companyId
    );
  }

  private Optional<BankingDomicileEntity> findWithCompany(
    Integer agency,
    AccountCandidate account,
    UUID companyId
  ) {
    if (account.accountDigit() == null) {
      return bankingDomicileRepository.findFirstByAgencyAndCurrentAccountAndCompany_Id(
        agency,
        account.currentAccount(),
        companyId
      );
    }

    return bankingDomicileRepository.findFirstByAgencyAndCurrentAccountAndAccountDigitAndCompany_Id(
      agency,
      account.currentAccount(),
      account.accountDigit(),
      companyId
    );
  }

  private Optional<BankingDomicileEntity> findWithBank(
    String bankCode,
    Integer agency,
    AccountCandidate account
  ) {
    if (account.accountDigit() == null) {
      return bankingDomicileRepository.findFirstByBank_CodeAndAgencyAndCurrentAccount(
        bankCode,
        agency,
        account.currentAccount()
      );
    }

    return bankingDomicileRepository.findFirstByBank_CodeAndAgencyAndCurrentAccountAndAccountDigit(
      bankCode,
      agency,
      account.currentAccount(),
      account.accountDigit()
    );
  }

  private Optional<BankingDomicileEntity> findWithoutBankAndCompany(
    Integer agency,
    AccountCandidate account
  ) {
    if (account.accountDigit() == null) {
      return bankingDomicileRepository.findFirstByAgencyAndCurrentAccount(
        agency,
        account.currentAccount()
      );
    }

    return bankingDomicileRepository.findFirstByAgencyAndCurrentAccountAndAccountDigit(
      agency,
      account.currentAccount(),
      account.accountDigit()
    );
  }

  private List<AccountCandidate> accountCandidates(Integer currentAccount) {
    List<AccountCandidate> candidates = new ArrayList<>();
    candidates.add(new AccountCandidate(currentAccount, null));

    String accountText = String.valueOf(currentAccount);
    if (accountText.length() > 1) {
      Integer accountWithoutDigit = Integer.valueOf(accountText.substring(0, accountText.length() - 1));
      String accountDigit = accountText.substring(accountText.length() - 1);
      candidates.add(new AccountCandidate(accountWithoutDigit, accountDigit));
    }

    return candidates;
  }

  private List<String> bankCodeCandidates(String bankCode) {
    String normalized = normalizeBankCode(bankCode);
    if (normalized == null) {
      return List.of();
    }

    Set<String> candidates = new LinkedHashSet<>();
    candidates.add(normalized);

    String withoutLeadingZeros = removeLeadingZeros(normalized);
    if (!withoutLeadingZeros.isBlank()) {
      candidates.add(withoutLeadingZeros);
    }

    if (normalized.matches("\\d+")) {
      candidates.add(String.format("%03d", new BigInteger(normalized)));
    }

    return new ArrayList<>(candidates);
  }

  private String normalizeBankCode(String bankCode) {
    if (bankCode == null || bankCode.isBlank()) {
      return null;
    }

    return bankCode.trim();
  }

  private String removeLeadingZeros(String value) {
    return value.replaceFirst("^0+(?!$)", "");
  }

  private record AccountCandidate(Integer currentAccount, String accountDigit) {
  }
}
