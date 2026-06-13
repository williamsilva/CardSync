package com.cardsync.domain.repository;

import com.cardsync.domain.model.HolidayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HolidayRepository extends JpaRepository<HolidayEntity, UUID> {

  List<HolidayEntity> findAllByHolidayDateBetweenOrderByHolidayDateAsc(
    LocalDate startDate,
    LocalDate endDate
  );

  List<HolidayEntity> findAllByOrderByHolidayDateDesc();

  Optional<HolidayEntity> findByHolidayDate(LocalDate holidayDate);
}
