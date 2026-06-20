package com.cardsync.domain.repository;

import com.cardsync.domain.model.HolidayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HolidayRepository extends JpaRepository<HolidayEntity, UUID>, JpaSpecificationExecutor<HolidayEntity> {

  List<HolidayEntity> findAllByHolidayDateBetweenOrderByHolidayDateAsc(
    LocalDate startDate,
    LocalDate endDate
  );

  List<HolidayEntity> findAllByOrderByHolidayDateDesc();

  Optional<HolidayEntity> findByHolidayDate(LocalDate holidayDate);

  Optional<HolidayEntity> findByRecurringAndHolidayDate(Boolean recurring, LocalDate holidayDate);

  /**
   * Retorna feriados ativos não-recorrentes no intervalo de datas E todos os feriados recorrentes
   * ativos (ano=1900). O chamador deve expandir os recorrentes para o ano do calendário.
   */
  @org.springframework.data.jpa.repository.Query("""
    SELECT h FROM HolidayEntity h
    WHERE h.status = 1
      AND (
        (h.recurring = false AND h.holidayDate BETWEEN :startDate AND :endDate)
        OR h.recurring = true
      )
    """)
  List<HolidayEntity> findActiveForCalendarRange(
    @org.springframework.data.repository.query.Param("startDate") LocalDate startDate,
    @org.springframework.data.repository.query.Param("endDate") LocalDate endDate
  );

  /** Retorna feriados ativos que cobrem a data: específicos com data exata OU recorrentes com mesmo dia/mês. */
  @org.springframework.data.jpa.repository.Query("""
    SELECT h FROM HolidayEntity h
    WHERE h.status = 1
      AND (
        (h.recurring = false AND h.holidayDate = :date)
        OR
        (h.recurring = true
          AND FUNCTION('MONTH', h.holidayDate) = FUNCTION('MONTH', :date)
          AND FUNCTION('DAY',   h.holidayDate) = FUNCTION('DAY',   :date))
      )
    """)
  List<HolidayEntity> findActiveByDate(@org.springframework.data.repository.query.Param("date") LocalDate date);
}
