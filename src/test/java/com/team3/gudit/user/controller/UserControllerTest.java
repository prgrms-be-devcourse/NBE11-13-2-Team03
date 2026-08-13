package com.team3.gudit.user.controller;

import com.team3.gudit.auth.security.CustomUserDetails;
import com.team3.gudit.user.domain.entity.Role;
import com.team3.gudit.user.dto.UserMeResponseDto;
import com.team3.gudit.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("인증된 사용자의 정보를 조회한다")
    void getMyInfo_success() {

        // given
        Long userId = 1L;

        CustomUserDetails userDetails =
                new CustomUserDetails(
                        userId,
                        Role.USER
                );

        UserMeResponseDto responseDto =
                new UserMeResponseDto(
                        userId,
                        "테스트유저",
                        "test@test.com",
                        Role.USER
                );

        when(userService.getMyInfo(userId))
                .thenReturn(responseDto);

        // when
        ResponseEntity<UserMeResponseDto> response =
                userController.getMyInfo(userDetails);

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().id())
                .isEqualTo(userId);

        assertThat(response.getBody().nickname())
                .isEqualTo("테스트유저");

        assertThat(response.getBody().email())
                .isEqualTo("test@test.com");

        assertThat(response.getBody().role())
                .isEqualTo(Role.USER);

        verify(userService)
                .getMyInfo(userId);
    }
}