package com.cardsync.domain.repository;

import com.cardsync.domain.model.BankingDomicileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankingDomicileRepository extends JpaRepository<BankingDomicileEntity, UUID>, JpaSpecificationExecutor<BankingDomicileEntity> {

  Optional<BankingDomicileEntity> findFirstByAgencyAndCurrentAccount(Integer agency, Integer currentAccount);

  Optional<BankingDomicileEntity> findFirstByAgencyAndCurrentAccountAndCompany_Id(
    Integer agency,
    Integer currentAccount,
    UUID companyId
  );

  Optional<BankingDomicileEntity> findFirstByBank_CodeAndAgencyAndCurrentAccount(
    String bankCode,
    Integer agency,
    Integer currentAccount
  );

  Optional<BankingDomicileEntity> findFirstByBank_CodeAndAgencyAndCurrentAccountAndCompany_Id(
    String bankCode,
    Integer agency,
    Integer currentAccount,
    UUID companyId
  );

  Optional<BankingDomicileEntity> findFirstByAgencyAndCurrentAccountAndAccountDigit(
    Integer agency,
    Integer currentAccount,
    String accountDigit
  );

  Optional<BankingDomicileEntity> findFirstByAgencyAndCurrentAccountAndAccountDigitAndCompany_Id(
    Integer agency,
    Integer currentAccount,
    String accountDigit,
    UUID companyId
  );

  Optional<BankingDomicileEntity> findFirstByBank_CodeAndAgencyAndCurrentAccountAndAccountDigit(
    String bankCode,
    Integer agency,
    Integer currentAccount,
    String accountDigit
  );

  Optional<BankingDomicileEntity> findFirstByBank_CodeAndAgencyAndCurrentAccountAndAccountDigitAndCompany_Id(
    String bankCode,
    Integer agency,
    Integer currentAccount,
    String accountDigit,
    UUID companyId
  );
}
