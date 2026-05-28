package com.lqragent.backend.admin.service;

import com.lqragent.backend.admin.dto.AdminStatusDto;
import com.lqragent.backend.admin.dto.AdminUserDto;
import com.lqragent.backend.admin.dto.SysConfigDto;
import com.lqragent.backend.chat.proxy.AiServerClient;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import com.lqragent.backend.systemconfig.entity.SysConfig;
import com.lqragent.backend.systemconfig.service.SysConfigService;
import com.lqragent.backend.uploadqueue.service.UploadQueueService;
import com.lqragent.backend.user.entity.User;
import com.lqragent.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final SysConfigService sysConfigService;
    private final AppRuntimeConfig runtimeConfig;
    private final AiServerClient aiServerClient;
    private final UserRepository userRepository;
    private final UploadQueueService uploadQueueService;

    public List<SysConfigDto> listConfigs() {
        return sysConfigService.listAll().stream().map(this::toDto).toList();
    }

    public SysConfigDto saveConfig(String key, String value, String remark) {
        return toDto(sysConfigService.upsert(key, value, remark));
    }

    public void deleteConfig(String key) {
        sysConfigService.deleteByKey(key);
    }

    public AdminStatusDto getStatus() {
        return AdminStatusDto.builder()
                .serverPort(runtimeConfig.get(ConfigKeys.SERVER_PORT, "8080"))
                .aiServerBaseUrl(runtimeConfig.getAiServerBaseUrl())
                .aiServerWsUrl(runtimeConfig.getAiServerWsUrl())
                .aiServerAutoStart(runtimeConfig.isAiServerAutoStart())
                .aiServerReachable(aiServerClient.ping())
                .userCount(userRepository.count())
                .uploadTaskCount(uploadQueueService.totalCount())
                .build();
    }

    public List<AdminUserDto> listUsers() {
        return userRepository.findAll().stream().map(this::toUserDto).toList();
    }

    public boolean pingAiServer() {
        return aiServerClient.ping();
    }

    public boolean processOneUpload() {
        return uploadQueueService.processOnePending();
    }

    private SysConfigDto toDto(SysConfig c) {
        return SysConfigDto.builder()
                .id(c.getId())
                .configKey(c.getConfigKey())
                .configValue(c.getConfigValue())
                .remark(c.getRemark())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private AdminUserDto toUserDto(User u) {
        return AdminUserDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .displayName(u.getDisplayName())
                .role(u.getRole().name())
                .enabled(u.getEnabled())
                .build();
    }
}
