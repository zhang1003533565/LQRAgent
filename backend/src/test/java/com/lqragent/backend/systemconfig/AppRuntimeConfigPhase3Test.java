package com.lqragent.backend.systemconfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.lqragent.backend.systemconfig.service.SysConfigService;

class AppRuntimeConfigPhase3Test {

    @Test
    void supervisorDisabledByDefault() {
        AppRuntimeConfig config = new AppRuntimeConfig(emptySysConfig(), new MockEnvironment());
        assertFalse(config.isSupervisorEnabled());
        assertTrue(config.isSupervisorPersistTranscript());
    }

    @Test
    void supervisorSceneGate() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("supervisor.enabled", "true");
        env.setProperty("supervisor.scenes", "path_generation,quiz_design");
        AppRuntimeConfig config = new AppRuntimeConfig(emptySysConfig(), env);
        assertTrue(config.isSupervisorSceneEnabled("path_generation"));
        assertTrue(config.isSupervisorSceneEnabled("quiz_design"));
        assertFalse(config.isSupervisorSceneEnabled("intervention"));
    }

    private static SysConfigService emptySysConfig() {
        return new SysConfigService(null) {
            @Override
            public Optional<String> getValue(String key) {
                return Optional.empty();
            }
        };
    }
}
