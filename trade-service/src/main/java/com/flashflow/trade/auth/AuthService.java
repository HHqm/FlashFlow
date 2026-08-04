package com.flashflow.trade.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long tokenTtlMinutes;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.ttl-minutes:120}") long tokenTtlMinutes
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.username())
                .filter(UserEntity::isEnabled)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(tokenTtlMinutes, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("flashflow-trade-service")
                .subject(user.getUsername())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("uid", user.getId())
                .claim("roles", List.of(user.getRole()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthDtos.TokenResponse(token, "Bearer", tokenTtlMinutes * 60, user.getRole());
    }
}
