package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.AnomalyStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyStatusEventRepository extends JpaRepository<AnomalyStatusEvent, Long> {
}
