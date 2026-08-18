package com.team3.gudit.auth.exception;

import com.team3.gudit.global.exception.ErrorCodeProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthErrorCodeProvider implements ErrorCodeProvider {
    @Override
    public List<AuthErrorCode> getErrorCodes() {
        return List.of(AuthErrorCode.values());
    }
}
