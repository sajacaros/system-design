package com.minidrive.auth;

import com.minidrive.auth.dto.AuthDtos.LoginRequest;
import com.minidrive.auth.dto.AuthDtos.LoginResponse;
import com.minidrive.auth.dto.AuthDtos.RefreshResponse;
import com.minidrive.auth.dto.AuthDtos.SignupRequest;
import com.minidrive.auth.dto.AuthDtos.SignupResponse;
import com.minidrive.auth.dto.AuthDtos.UserSummary;
import com.minidrive.common.ApiException;
import com.minidrive.common.ErrorCode;
import com.minidrive.user.User;
import com.minidrive.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public SignupResponse signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "Email already registered");
        }
        User user = new User(req.email(), passwordEncoder.encode(req.password()), req.nickname());
        user = userRepository.save(user);
        return new SignupResponse(user.getId(), user.getEmail(), user.getNickname());
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_CREDENTIALS, "Invalid email or password"));
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new ApiException(ErrorCode.BAD_CREDENTIALS, "Invalid email or password");
        }
        String access = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refresh = issueRefreshToken(user.getId());
        return new LoginResponse(access, refresh,
                new UserSummary(user.getId(), user.getEmail(), user.getNickname()));
    }

    @Transactional
    public RefreshResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_REFRESH, "Invalid refresh token");
        }
        if (!"refresh".equals(claims.get("typ", String.class))) {
            throw new ApiException(ErrorCode.INVALID_REFRESH, "Invalid refresh token");
        }
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH, "Invalid refresh token"));
        if (!stored.isValid()) {
            throw new ApiException(ErrorCode.INVALID_REFRESH, "Refresh token expired or revoked");
        }
        // Rotation: revoke old, issue new.
        stored.revoke();
        Long userId = Long.valueOf(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH, "Invalid refresh token"));
        String newAccess = jwtService.generateAccessToken(userId, user.getEmail());
        String newRefresh = issueRefreshToken(userId);
        return new RefreshResponse(newAccess, newRefresh);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .ifPresent(RefreshToken::revoke);
    }

    private String issueRefreshToken(Long userId) {
        String token = jwtService.generateRefreshToken(userId);
        RefreshToken entity = new RefreshToken(userId, hash(token), jwtService.refreshExpiry());
        refreshTokenRepository.save(entity);
        return token;
    }

    private static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
