package com.example.loadforcasting.Entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "model_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_model_versions_module_label", columnNames = {"module_code", "version_label"})
})
public class ModelVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module_code", nullable = false, length = 100)
    private String moduleCode;

    @Column(name = "model_name", nullable = false, length = 150)
    private String modelName;

    @Column(name = "version_label", nullable = false, length = 100)
    private String versionLabel;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "deployed_at", nullable = false)
    private LocalDateTime deployedAt;

    @PrePersist
    protected void onCreate() {
        if (deployedAt == null) {
            deployedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public void setVersionLabel(String versionLabel) {
        this.versionLabel = versionLabel;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getDeployedAt() {
        return deployedAt;
    }

    public void setDeployedAt(LocalDateTime deployedAt) {
        this.deployedAt = deployedAt;
    }
}
