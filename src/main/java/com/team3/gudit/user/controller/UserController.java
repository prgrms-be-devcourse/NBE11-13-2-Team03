package com.team3.gudit.user.controller;

import com.team3.gudit.auth.security.CustomUserDetails;
import com.team3.gudit.user.dto.UserMeResponseDto;
import com.team3.gudit.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserMeResponseDto> getMyInfo(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                userService.getMyInfo(userDetails.getUserId())
        );
    }
}
