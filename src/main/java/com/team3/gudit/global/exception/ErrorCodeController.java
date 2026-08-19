package com.team3.gudit.global.exception;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/error-codes")
public class ErrorCodeController {

    private final List<ErrorCodeProvider<?>> errorCodeProviders;

    @GetMapping
    @Operation(summary = "에러 코드 목록 조회")
    public Map<String, List<ErrorCode>> getErrorCodes() {
        return errorCodeProviders.stream()
                .collect(Collectors.toMap(
                        ErrorCodeProvider::getDomain,
                        provider -> provider.getErrorCodes()
                                .stream()
                                .toList()
                ));
    }
}
