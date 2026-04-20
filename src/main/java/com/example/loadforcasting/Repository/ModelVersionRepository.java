package com.example.loadforcasting.Repository;

import com.example.loadforcasting.Entity.ModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, Long> {

    Optional<ModelVersion> findFirstByModuleCodeAndVersionLabel(String moduleCode, String versionLabel);
}
