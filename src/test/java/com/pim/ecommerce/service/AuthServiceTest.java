package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.RefreshToken;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.dto.request.LoginRequest;
import com.pim.ecommerce.dto.request.RefreshTokenRequest;
import com.pim.ecommerce.dto.response.LoginResponse;
import com.pim.ecommerce.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtExpiration", 3600000L);
    }

    @Test
    void shouldLoginWithValidCredentials() {
        User user = user(1L, true);
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(userRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
        when(jwtService.generateToken("ana@email.com")).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken);

        LoginResponse response = authService.login(new LoginRequest(" ANA@EMAIL.COM ", "123456"));

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.type()).isEqualTo("Bearer");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(3600000L);
        assertThat(response.user().email()).isEqualTo("ana@email.com");
        assertThat(response.user().role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void shouldRejectInvalidPasswordWithGenericMessage() {
        User user = user(1L, true);
        when(userRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@email.com", "wrong")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("E-mail ou senha inválidos");

        verify(jwtService, never()).generateToken("ana@email.com");
    }

    @Test
    void shouldRejectInactiveUserWithGenericMessage() {
        User user = user(1L, false);
        when(userRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@email.com", "123456")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("E-mail ou senha inválidos");

        verify(passwordEncoder, never()).matches("123456", "hash");
    }

    @Test
    void shouldRefreshSessionRotatingRefreshToken() {
        User user = user(1L, true);
        RefreshToken newRefreshToken = RefreshToken.builder()
                .token("new-refresh-token")
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenService.rotateRefreshToken("old-refresh-token")).thenReturn(newRefreshToken);
        when(jwtService.generateToken("ana@email.com")).thenReturn("new-access-token");

        LoginResponse response = authService.refresh(new RefreshTokenRequest("old-refresh-token"));

        assertThat(response.token()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.user().id()).isEqualTo(1L);
    }

    @Test
    void shouldLogoutRevokingRefreshToken() {
        authService.logout(new RefreshTokenRequest("refresh-token"));

        verify(refreshTokenService).revokeToken("refresh-token");
    }

    @Test
    void shouldReturnAuthenticatedUserByEmail() {
        User user = user(1L, true);
        when(userRepository.findByEmailAndActiveTrue("ana@email.com")).thenReturn(Optional.of(user));

        var response = authService.me(" ANA@EMAIL.COM ");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("ana@email.com");
    }


    @Test
    void shouldRejectLoginWhenEmailDoesNotExistUsingGenericMessage() {
        when(userRepository.findByEmail("missing@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(" missing@email.com ", "123456")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("E-mail ou senha inválidos");

        verify(passwordEncoder, never()).matches("123456", "hash");
        verify(jwtService, never()).generateToken("missing@email.com");
    }

    @Test
    void shouldRejectLoginWhenEmailIsBlank() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("   ", "123456")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("E-mail é obrigatório");

        verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectRefreshWhenUserIsInactive() {
        User inactiveUser = user(1L, false);
        RefreshToken refreshToken = RefreshToken.builder()
                .token("new-refresh-token")
                .user(inactiveUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenService.rotateRefreshToken("old-refresh-token")).thenReturn(refreshToken);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("old-refresh-token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuário inativo");

        verify(jwtService, never()).generateToken("ana@email.com");
    }

    @Test
    void shouldRejectMeWhenEmailIsBlank() {
        assertThatThrownBy(() -> authService.me(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("E-mail é obrigatório");

        verify(userRepository, never()).findByEmailAndActiveTrue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectMeWhenActiveUserDoesNotExist() {
        when(userRepository.findByEmailAndActiveTrue("missing@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.me(" missing@email.com "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuário não encontrado");
    }

    private User user(Long id, Boolean active) {
        return User.builder()
                .id(id)
                .fullName("Ana Souza")
                .email("ana@email.com")
                .passwordHash("hash")
                .avatarUrl("/uploads/users/avatars/ana.png")
                .role(UserRole.CUSTOMER)
                .active(active)
                .build();
    }
}
