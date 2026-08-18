package com.team3.gudit.user.exception;

import com.team3.gudit.global.exception.ErrorCode;
import com.team3.gudit.global.exception.ErrorCodeProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserErrorCodeProvider implements ErrorCodeProvider<UserErrorCode> {
    @Override
    public String getDomain() {
        return "USER";
    }

    @Override
    public List<ErrorCode> getErrorCodes() {
        return List.of(UserErrorCode.values());
    }
}
