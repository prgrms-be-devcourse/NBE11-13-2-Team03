package com.team3.gudit.global.exception;

import java.util.List;

public interface ErrorCodeProvider {
    List<? extends ErrorCode> getErrorCodes();
}
