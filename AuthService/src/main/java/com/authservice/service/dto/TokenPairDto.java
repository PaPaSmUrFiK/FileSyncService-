package com.authservice.service.dto;

import lombok.*;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenPairDto {

    String accessToken;
    String refreshToken;

    UUID userId;
    String email;
    String name;
    Set<String> roles;

    long expiresIn;
}
