package com.cardsync.core.file.erp.mapper;

import com.cardsync.core.file.erp.dto.TransactionErpCsvDto;
import com.cardsync.domain.model.TransactionErpEntity;
import org.springframework.stereotype.Component;

@Component
public class TransactionErpMapper {

  public TransactionErpEntity map(TransactionErpCsvDto dto) {
    TransactionErpEntity tx = new TransactionErpEntity();
    tx.setTid(dto.getTid());
    tx.setNsu(dto.getNsu());
    tx.setGrossValue(dto.getGrossValue());
    tx.setOrigin(dto.getOrigin());
    tx.setThreeDs(dto.getThreeDs());
    tx.setCardName(dto.getCardName());
    tx.setSaleDate(dto.getSaleDate());
    tx.setAntiFraud(dto.getAntiFraud());
    tx.setCardNumber(dto.getCardNumber());
    tx.setMachine(dto.getMachine());
    tx.setSourceCompanyCnpj(dto.getCompanyCnpj());
    tx.setSourceCompanyName(dto.getCompanyName());
    tx.setSourceEstablishmentPvNumber(dto.getEstablishmentPvNumber());
    tx.setSourceEstablishmentName(dto.getEstablishmentName());
    tx.setTransaction(dto.getTransaction());
    tx.setAuthorization(dto.getAuthorization());
    tx.setInstallmentType(dto.getInstallmentType());
    tx.setInstallment(dto.getInstallment() == null || dto.getInstallment() <= 0 ? 1 : dto.getInstallment());
    return tx;
  }
}
