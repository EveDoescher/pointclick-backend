package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.RefreshToken;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration:604800000}")
    private Long refreshExpiration;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(LocalDateTime.now().plusNanos(refreshExpiration * 1_000_000))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public RefreshToken rotateRefreshToken(String token) {
        RefreshToken currentRefreshToken = validateRefreshToken(token);

        revoke(currentRefreshToken);

        return createRefreshToken(currentRefreshToken.getUser());
    }

    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Refresh token é obrigatório");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(token.trim())
                .orElseThrow(() -> new IllegalArgumentException("Sessão inválida. Faça login novamente."));

        if (Boolean.TRUE.equals(refreshToken.getRevoked())) {
            throw new IllegalArgumentException("Sessão expirada. Faça login novamente.");
        }

        if (refreshToken.getExpiresAt() == null || LocalDateTime.now().isAfter(refreshToken.getExpiresAt())) {
            throw new IllegalArgumentException("Sessão expirada. Faça login novamente.");
        }

        if (refreshToken.getUser() == null || !Boolean.TRUE.equals(refreshToken.getUser().getActive())) {
            throw new IllegalArgumentException("Usuário inativo");
        }

        return refreshToken;
    }

    @Transactional
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        refreshTokenRepository.findByToken(token.trim())
                .ifPresent(this::revoke);
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        refreshTokenRepository.findByUserIdAndRevokedFalse(userId)
                .forEach(this::revoke);
    }

    private void revoke(RefreshToken refreshToken) {
        refreshToken.setRevoked(true);
        refreshToken.setRevokedAt(LocalDateTime.now());

        refreshTokenRepository.save(refreshToken);
    }
}