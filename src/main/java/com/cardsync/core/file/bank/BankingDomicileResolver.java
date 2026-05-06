package com.cardsync.core.file.bank;

import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.repository.BankingDomicileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BankingDomicileResolver {

  private final BankingDomicileRepository bankingDomicileRepository;

  public Optional<BankingDomicileEntity> resolve(Integer agency, Integer currentAccount, CompanyEntity company) {
    if (agency == null || currentAccount == null) return Optional.empty();

    if (company != null && company.getId() != null) {
      Optional<BankingDomicileEntity> byCompany = bankingDomicileRepository
        .findFirstByAgencyAndCurrentAccountAndCompany_Id(agency, currentAccount, company.getId());
      if (byCompany.isPresent()) return byCompany;
    }

    return bankingDomicileRepository.findFirstByAgencyAndCurrentAccount(agency, currentAccount);
  }
}
