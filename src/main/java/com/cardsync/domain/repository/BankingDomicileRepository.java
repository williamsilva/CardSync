package com.cardsync.domain.repository;

import com.cardsync.domain.model.BankingDomicileEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankingDomicileRepository extends JpaRepository<BankingDomicileEntity, UUID>, JpaSpecificationExecutor<BankingDomicileEntity> {

  @Override
  @EntityGraph(attributePaths = {"createdBy", "updatedBy", "company", "bank"})
  List<BankingDomicileEntity> findAll(Sort sort);

  @Override
  @EntityGraph(attributePaths = {"bank", "company"})
  Optional<BankingDomicileEntity> findById(UUID id);

  /**
   * Carrega os domicílios com o banco para a montagem do calendário de arquivos.
   * A regra de atividade na data é aplicada no serviço, pois considera statusDate.
   */
  @EntityGraph(attributePaths = {"bank"})
  @Query("""
    select domicile
      from BankingDomicileEntity domicile
      left join domicile.bank bank
     order by bank.name asc, domicile.agency asc, domicile.currentAccount asc
  """)
  List<BankingDomicileEntity> findAllForImportedFilesCalendar();

  @Query("""
    select bd
      from BankingDomicileEntity bd
     where bd.bank.id = :bankId
       and bd.company.id = :companyId
       and bd.agency = :agency
       and bd.currentAccount = :currentAccount
       and coalesce(bd.agencyDigit, '') = :agencyDigit
       and coalesce(bd.accountDigit, '') = :accountDigit
    """)
  Optional<BankingDomicileEntity> findDuplicate(
    @Param("bankId") UUID bankId,
    @Param("companyId") UUID companyId,
    @Param("agency") Integer agency,
    @Param("currentAccount") Integer currentAccount,
    @Param("agencyDigit") String agencyDigit,
    @Param("accountDigit") String accountDigit
  );

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
