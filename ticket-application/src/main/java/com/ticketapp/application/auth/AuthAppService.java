package com.ticketapp.application.auth;

import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import com.ticketapp.domain.user.UserRole;
import com.ticketapp.domain.user.UserStatus;
import com.ticketapp.infrastructure.security.JwtService;
import com.ticketapp.infrastructure.security.RedisRefreshTokenStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
            throw new EmailAlreadyUsedException();
        });
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        try {
            return issueTokens(userRepository.save(user));
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyUsedException();
        }
    }

    public TokenPair login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElseThrow(LoginFailedException::new);
        if (user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new LoginFailedException();
        }
        return issueTokens(user);
    }

    public TokenPair refresh(String refreshToken) {
        Long userId = refreshTokenStore.consume(refreshToken).orElseThrow(InvalidRefreshTokenException::new);
        User user = userRepository.findById(userId).orElseThrow(InvalidRefreshTokenException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRefreshTokenException();
        }
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        refreshTokenStore.revoke(refreshToken);
    }

    public AuthenticatedUser currentUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(LoginFailedException::new);
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
    }

    private TokenPair issueTokens(User user) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole());
        String refreshToken = refreshTokenStore.issue(user.getId());
        return new TokenPair(accessToken, refreshToken, jwtService.accessTtlSeconds());
    }
}
