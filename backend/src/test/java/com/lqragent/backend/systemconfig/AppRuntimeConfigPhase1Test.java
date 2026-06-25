package com.lqragent.backend.systemconfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.lqragent.backend.systemconfig.service.SysConfigService;

class AppRuntimeConfigPhase1Test {

    @Test
    void streamProgressDefaultsOff() {
        AppRuntimeConfig config = new AppRuntimeConfig(emptySysConfig(), new MockEnvironment());
        assertFalse(config.isStreamProgressEnabled());
    }

    @Test
    void streamProgressSkipFinalDumpDefaultsOn() {
        AppRuntimeConfig config = new AppRuntimeConfig(emptySysConfig(), new MockEnvironment());
        assertTrue(config.isStreamProgressSkipFinalDump());
    }

    @Test
    void planningGateDefaultsOff() {
        AppRuntimeConfig config = new AppRuntimeConfig(emptySysConfig(), new MockEnvironment());
        assertFalse(config.isPlanningGateEnabled());
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
