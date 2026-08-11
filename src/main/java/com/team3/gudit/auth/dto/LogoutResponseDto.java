package com.team3.gudit.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LogoutResponseDto {
    String message;
    String url;
}
