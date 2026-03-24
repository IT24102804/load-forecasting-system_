package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.LoadRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoadRepository extends JpaRepository<LoadRequest, Long> {
    List<LoadRequest> findTop23ByOrderByIdDesc();
}