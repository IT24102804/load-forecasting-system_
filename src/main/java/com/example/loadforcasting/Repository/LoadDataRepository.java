package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.LoadData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

public interface LoadDataRepository extends JpaRepository<LoadData, Integer> {

    @Modifying
    @Transactional
    @Query("DELETE FROM LoadData l WHERE l.timestamp < ?1")
    void deleteOldData(LocalDateTime cutoffDate);
}
