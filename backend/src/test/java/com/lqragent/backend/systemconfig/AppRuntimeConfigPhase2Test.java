package com.lqragent.backend.systemconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.lqragent.backend.systemconfig.service.SysConfigService;

class AppRuntimeConfigPhase2Test {

    @Test
    void consultationDefaultsOff() {
        AppRuntimeConfig config = new AppRuntimeConfig(emptySysConfig(), new MockEnvironment());
        assertFalse(config.isConsultationEnabled());
        assertEquals("path_generation", config.getConsultationScenes());
        assertEquals(2, config.getConsultationMaxRounds());
        assertFalse(config.isConsultationStreamTranscript());
    }

    @Test
    void consultationSceneEnabledWhenFlagOn() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(ConfigKeys.CONSULTATION_ENABLED, "true");
        env.setProperty(ConfigKeys.CONSULTATION_SCENES, "path_generation");
        AppRuntimeConfig config = new AppRuntimeConfig(emptySysConfig(), env);
        assertTrue(config.isConsultationSceneEnabled("path_generation"));
        assertFalse(config.isConsultationSceneEnabled("quiz_difficulty"));
    }

    @Test
    void consultationTimeoutDefault() {
        AppRuntimeConfig config = new AppRuntimeConfig(emptySysConfig(), new MockEnvironment());
        assertEquals(90000L, config.getConsultationTimeoutMs());
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
