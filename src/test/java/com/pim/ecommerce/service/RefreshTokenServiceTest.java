package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.RefreshToken;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpiration", 604800000L);
    }

    @Test
    void shouldCreateRefreshToken() {
        User user = user(true);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            token.setId(1L);
            return token;
        });

        RefreshToken token = refreshTokenService.createRefreshToken(user);

        assertThat(token.getId()).isEqualTo(1L);
        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getUser()).isEqualTo(user);
        assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(token.getRevoked()).isFalse();
    }

    @Test
    void shouldValidateRefreshToken() {
        RefreshToken token = validToken("refresh-token", user(true));
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(token));

        RefreshToken response = refreshTokenService.validateRefreshToken(" refresh-token ");

        assertThat(response).isEqualTo(token);
    }

    @Test
    void shouldRejectRevokedToken() {
        RefreshToken token = validToken("refresh-token", user(true));
        token.setRevoked(true);
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("refresh-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sessão expirada. Faça login novamente.");
    }

    @Test
    void shouldRejectExpiredToken() {
        RefreshToken token = validToken("refresh-token", user(true));
        token.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("refresh-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sessão expirada. Faça login novamente.");
    }

    @Test
    void shouldRejectInactiveUser() {
        RefreshToken token = validToken("refresh-token", user(false));
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken("refresh-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuário inativo");
    }

    @Test
    void shouldRotateRefreshTokenRevokingOldOne() {
        User user = user(true);
        RefreshToken oldToken = validToken("old-token", user);

        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(oldToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken newToken = refreshTokenService.rotateRefreshToken("old-token");

        assertThat(oldToken.getRevoked()).isTrue();
        assertThat(oldToken.getRevokedAt()).isNotNull();
        assertThat(newToken.getUser()).isEqualTo(user);
        assertThat(newToken.getRevoked()).isFalse();
    }

    @Test
    void shouldRevokeAllActiveUserTokens() {
        RefreshToken first = validToken("one", user(true));
        RefreshToken second = validToken("two", user(true));
        when(refreshTokenRepository.findByUserIdAndRevokedFalse(1L)).thenReturn(List.of(first, second));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        refreshTokenService.revokeAllUserTokens(1L);

        assertThat(first.getRevoked()).isTrue();
        assertThat(second.getRevoked()).isTrue();
        verify(refreshTokenRepository).save(first);
        verify(refreshTokenRepository).save(second);
    }

    private RefreshToken validToken(String tokenValue, User user) {
        return RefreshToken.builder()
                .id(1L)
                .token(tokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
    }

    private User user(Boolean active) {
        return User.builder()
                .id(1L)
                .fullName("Ana Souza")
                .email("ana@email.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(active)
                .build();
    }
}
