package com.team3.gudit.goods.dto.error;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ErrorResponse(
        int status,
        String code,
        String message,
        String api,
        String location,
        LocalDateTime time
) {

    public static ErrorResponse of(
            int status,
            String code,
            String message,
            String api,
            String location,
            LocalDateTime time
    ) {
        return ErrorResponse.builder()
                .status(status)
                .code(code)
                .message(message)
                .api(api)
                .location(location)
                .time(time)
                .build();
    }
}
