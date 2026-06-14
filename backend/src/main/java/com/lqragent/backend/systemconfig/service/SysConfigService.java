package com.lqragent.backend.systemconfig.service;

import com.lqragent.backend.systemconfig.entity.SysConfig;
import com.lqragent.backend.systemconfig.repository.SysConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigRepository repository;

    public List<SysConfig> listAll() {
        return repository.findAll().stream()
                .sorted((a, b) -> a.getConfigKey().compareToIgnoreCase(b.getConfigKey()))
                .toList();
    }

    public Optional<SysConfig> findByKey(String key) {
        return repository.findByConfigKey(key);
    }

    public Optional<String> getValue(String key) {
        return findByKey(key).map(SysConfig::getConfigValue);
    }

    @Transactional
    public SysConfig upsert(String key, String value, String remark) {
        if (value == null || value.isBlank()) {
            return repository.findByConfigKey(key).orElse(null);
        }
        SysConfig config = repository.findByConfigKey(key).orElseGet(() -> SysConfig.builder()
                .configKey(key)
                .build());
        config.setConfigValue(value);
        if (remark != null && !remark.isBlank()) {
            config.setRemark(remark);
        }
        return repository.save(config);
    }

    @Transactional
    public boolean deleteByKey(String key) {
        return repository.findByConfigKey(key)
                .map(entity -> {
                    repository.delete(entity);
                    return true;
                })
                .orElse(false);
    }
}
