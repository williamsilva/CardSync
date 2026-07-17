package com.cardsync.domain.repository;

import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankRepository extends JpaRepository<BankEntity, UUID>, JpaSpecificationExecutor<BankEntity> {
  Optional<BankEntity> findByCode(String code);
  Optional<BankEntity> findByCodeIgnoreCase(String code);
  List<BankEntity> findAllByOrderByNameAsc();

  /** Opções de filtro: ativos primeiro, depois ordem alfabética por nome. */
  @Query("""
    select b
      from BankEntity b
     order by case when b.status = :activeStatus then 0 else 1 end, b.name asc
  """)
  List<BankEntity> findAllOptionsFilterOrderByActiveThenName(@Param("activeStatus") Integer activeStatus);

}
