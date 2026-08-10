package com.team3.gudit.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccessTokenResponseDto {
    private String accessToken;
    private String tokenType = "Bearer";
    private Long expiresIn;
}
