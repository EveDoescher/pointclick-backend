package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.RefreshToken;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.dto.request.LoginRequest;
import com.pim.ecommerce.dto.request.RefreshTokenRequest;
import com.pim.ecommerce.dto.response.AuthenticatedUserResponse;
import com.pim.ecommerce.dto.response.LoginResponse;
import com.pim.ecommerce.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("E-mail ou senha inválidos"));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("E-mail ou senha inválidos");
        }

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            throw new IllegalArgumentException("E-mail ou senha inválidos");
        }

        String accessToken = jwtService.generateToken(user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new LoginResponse(
                accessToken,
                "Bearer",
                refreshToken.getToken(),
                jwtExpiration,
                toAuthenticatedUserResponse(user)
        );
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(request.refreshToken());

        User user = newRefreshToken.getUser();

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("Usuário inativo");
        }

        String accessToken = jwtService.generateToken(user.getEmail());

        return new LoginResponse(
                accessToken,
                "Bearer",
                newRefreshToken.getToken(),
                jwtExpiration,
                toAuthenticatedUserResponse(user)
        );
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revokeToken(request.refreshToken());
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse me(String email) {
        User user = userRepository.findByEmailAndActiveTrue(normalizeEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        return toAuthenticatedUserResponse(user);
    }

    private AuthenticatedUserResponse toAuthenticatedUserResponse(User user) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getRole()
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("E-mail é obrigatório");
        }

        return email.trim().toLowerCase();
    }
}