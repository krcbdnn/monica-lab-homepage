package com.monicalab.popup.repository;

import com.monicalab.popup.entity.Popup;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PopupRepository extends JpaRepository<Popup, Long> {

    @Query("SELECT p FROM Popup p WHERE p.isVisible = true AND p.startDate <= :now AND p.endDate >= :now")
    List<Popup> findVisibleAndWithinPeriod(@Param("now") LocalDateTime now, Sort sort);
}
