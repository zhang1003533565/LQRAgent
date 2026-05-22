package com.lqragent.backend.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminStatusDto {
    private final String serverPort;
    private final String aiServerBaseUrl;
    private final String aiServerWsUrl;
    private final boolean aiServerAutoStart;
    private final boolean aiServerReachable;
    private final long userCount;
    private final long uploadTaskCount;
}
