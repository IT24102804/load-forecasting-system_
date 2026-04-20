package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.ModelVersion;
import com.example.loadforcasting.Repository.ModelVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelVersionService {

    public static final String MODULE_WEATHER = "weather";
    public static final String MODULE_LOAD = "load";
    public static final String MODULE_ANOMALY = "anomaly";
    public static final String MODULE_GENERATION_MIX = "generation_mix";
    public static final String MODULE_COST = "cost";
    public static final String VERSION_V1 = "v1";
    public static final String VERSION_LEGACY = "legacy";

    @Autowired
    private ModelVersionRepository modelVersionRepository;

    @Transactional
    public ModelVersion resolve(String moduleCode, String modelName, String versionLabel) {
        return modelVersionRepository
                .findFirstByModuleCodeAndVersionLabel(moduleCode, versionLabel)
                .orElseGet(() -> {
                    ModelVersion modelVersion = new ModelVersion();
                    modelVersion.setModuleCode(moduleCode);
                    modelVersion.setModelName(modelName);
                    modelVersion.setVersionLabel(versionLabel);
                    modelVersion.setActive(true);
                    return modelVersionRepository.save(modelVersion);
                });
    }

    public ModelVersion resolveLegacy(String moduleCode, String modelName) {
        return resolve(moduleCode, modelName, VERSION_LEGACY);
    }

    public ModelVersion resolveCurrent(String moduleCode, String modelName) {
        return resolve(moduleCode, modelName, VERSION_V1);
    }
}
