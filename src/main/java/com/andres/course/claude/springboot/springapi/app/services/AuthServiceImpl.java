package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.LoginDto;
import com.andres.course.claude.springboot.springapi.app.exceptions.InvalidTokenException;
import com.andres.course.claude.springboot.springapi.app.mappers.UserMapper;
import com.andres.course.claude.springboot.springapi.app.models.User;
import com.andres.course.claude.springboot.springapi.app.repositories.UserRepository;
import com.andres.course.claude.springboot.springapi.app.security.AuthTokens;
import com.andres.course.claude.springboot.springapi.app.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthServiceImpl(AuthenticationManager authenticationManager, UserRepository userRepository,
                           UserMapper userMapper, JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @Transactional
    public AuthTokens login(LoginDto dto) {
        // Lanza BadCredentialsException / DisabledException; GlobalExceptionHandler las traduce a 401.
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(dto.email(), dto.password()));
        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
        return issueTokens(user, refreshTokenService.issue(user));
    }

    @Override
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidTokenException("Falta el refresh token: inicia sesión");
        }
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        return issueTokens(rotation.user(), rotation.refreshToken());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
    }

    private AuthTokens issueTokens(User user, String refreshToken) {
        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole());
        return new AuthTokens(accessToken, refreshToken, userMapper.toDto(user));
    }
}
