package com.ticketapp.application.auth;

import com.ticketapp.application.exception.AppException;
import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import com.ticketapp.domain.user.UserRole;
import com.ticketapp.domain.user.UserStatus;
import com.ticketapp.infrastructure.security.JwtService;
import com.ticketapp.infrastructure.security.RedisRefreshTokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthAppService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisRefreshTokenStore refreshTokenStore;

    public AuthAppService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtService jwtService, RedisRefreshTokenStore refreshTokenStore) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
    }

    public TokenPair register(String email, String rawPassword) {
        userRepository.findByEmail(email).ifPresent(existing -> {
            log.warn("register rejected, email already used: {}", email);
            throw new AppException(ErrorCode.EMAIL_ALREADY_USED);
        });
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        try {
            return issueTokens(userRepository.save(user));
        } catch (DataIntegrityViolationException ex) {
            log.warn("register lost the unique-email race for {}", email);
            throw new AppException(ErrorCode.EMAIL_ALREADY_USED);
        }
    }

    public TokenPair login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> {
            log.warn("login failed, unknown email: {}", email);
            return new AppException(ErrorCode.LOGIN_FAILED);
        });
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("login failed, user {} is {}", user.getId(), user.getStatus());
            throw new AppException(ErrorCode.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.warn("login failed, bad password for user {}", user.getId());
            throw new AppException(ErrorCode.LOGIN_FAILED);
        }
        return issueTokens(user);
    }

    public TokenPair refresh(String refreshToken) {
        Long userId = refreshTokenStore.consume(refreshToken)
                .orElseThrow(() -> {
                    log.warn("refresh failed, token unknown or already consumed");
                    return new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
                });
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("refresh failed, user {} no longer exists", userId);
                    return new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
                });
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("refresh failed, user {} is {}", userId, user.getStatus());
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        refreshTokenStore.revoke(refreshToken);
    }

    public AuthenticatedUser currentUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
    }

    private TokenPair issueTokens(User user) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole());
        String refreshToken = refreshTokenStore.issue(user.getId());
        return new TokenPair(accessToken, refreshToken, jwtService.accessTtlSeconds());
    }
}
