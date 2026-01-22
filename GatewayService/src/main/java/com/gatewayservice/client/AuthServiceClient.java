package com.gatewayservice.client;

import com.authservice.grpc.AuthServiceGrpc;
import com.authservice.grpc.ValidateTokenRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewayservice.model.LoginRequest;
import com.gatewayservice.model.LogoutRequest;
import com.gatewayservice.model.RegisterRequest;
import com.gatewayservice.model.RefreshTokenRequest;
import com.gatewayservice.model.TokenResponse;
import com.gatewayservice.model.ValidationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import io.grpc.Context;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceClient {

    @GrpcClient("auth-service")
    private AuthServiceGrpc.AuthServiceBlockingStub authServiceStub;

    public Mono<com.gatewayservice.model.ValidationResponse> validateToken(String token) {
        return Mono.fromCallable(() -> {
            try {
                com.authservice.grpc.ValidationResponse grpcResponse = authServiceStub.validateToken(
                        ValidateTokenRequest.newBuilder().setAccessToken(token).build());

                return com.gatewayservice.model.ValidationResponse.builder()
                        .isValid(grpcResponse.getIsValid())
                        .userId(grpcResponse.getUserId())
                        .email(grpcResponse.getEmail())
                        .roles(new ArrayList<>(grpcResponse.getRolesList()))
                        .errorMessage(grpcResponse.getErrorMessage())
                        .build();
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error validating token: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                return com.gatewayservice.model.ValidationResponse.builder()
                        .isValid(false)
                        .errorMessage(e.getStatus().getDescription())
                        .build();
            } catch (Exception e) {
                log.error("Unexpected error validating token: {}", e.getMessage());
                return com.gatewayservice.model.ValidationResponse.builder()
                        .isValid(false)
                        .errorMessage("Auth service error")
                        .build();
            }
        });
    }

    public Mono<TokenResponse> login(LoginRequest request) {
        return Mono.fromCallable((Callable<TokenResponse>) () -> {
            try {
                com.authservice.grpc.LoginRequest grpcRequest = com.authservice.grpc.LoginRequest.newBuilder()
                        .setEmail(request.getEmail())
                        .setPassword(request.getPassword())
                        .setDeviceInfo(request.getDeviceInfo() != null ? request.getDeviceInfo() : "")
                        .build();

                com.authservice.grpc.TokenResponse grpcResponse = authServiceStub.login(grpcRequest);
                return mapToTokenResponse(grpcResponse);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error during login: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                String description = e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Authentication failed";
                // Include status code in message so frontend can detect it (e.g.
                // PERMISSION_DENIED)
                throw new RuntimeException(e.getStatus().getCode() + " - " + description);
            } catch (Exception e) {
                log.error("Unexpected error during login: {}", e.getMessage(), e);
                throw new RuntimeException("Auth service error");
            }
        });
    }

    public Mono<TokenResponse> register(RegisterRequest request) {
        return Mono.fromCallable((Callable<TokenResponse>) () -> {
            try {
                com.authservice.grpc.RegisterRequest grpcRequest = com.authservice.grpc.RegisterRequest.newBuilder()
                        .setEmail(request.getEmail())
                        .setPassword(request.getPassword())
                        .setName(request.getName())
                        .build();

                com.authservice.grpc.TokenResponse grpcResponse = authServiceStub.register(grpcRequest);
                return mapToTokenResponse(grpcResponse);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error during register: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Registration failed");
            } catch (Exception e) {
                log.error("Unexpected error during register: {}", e.getMessage(), e);
                throw new RuntimeException("Auth service error");
            }
        });
    }

    public Mono<TokenResponse> refreshToken(RefreshTokenRequest request) {
        return Mono.fromCallable((Callable<TokenResponse>) () -> {
            try {
                com.authservice.grpc.RefreshTokenRequest grpcRequest = com.authservice.grpc.RefreshTokenRequest
                        .newBuilder()
                        .setRefreshToken(request.getRefreshToken())
                        .build();

                com.authservice.grpc.TokenResponse grpcResponse = authServiceStub.refreshToken(grpcRequest);
                return mapToTokenResponse(grpcResponse);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error during token refresh: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Token refresh failed");
            } catch (Exception e) {
                log.error("Unexpected error during token refresh: {}", e.getMessage(), e);
                throw new RuntimeException("Auth service error");
            }
        });
    }

    public Mono<Void> logout(LogoutRequest request) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.authservice.grpc.LogoutRequest grpcRequest = com.authservice.grpc.LogoutRequest.newBuilder()
                        .setRefreshToken(request.getRefreshToken())
                        .build();

                authServiceStub.logout(grpcRequest);
                return null;
            } catch (Exception e) {
                log.error("Error during logout via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("Auth service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<Void> logoutAll(String userId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                Context.Key<UUID> userIdKey = Context.key("userId");
                UUID userIdUuid = UUID.fromString(userId);

                com.authservice.grpc.LogoutAllRequest grpcRequest = com.authservice.grpc.LogoutAllRequest.newBuilder()
                        .build();

                Context context = Context.current().withValue(userIdKey, userIdUuid);
                context.run(() -> {
                    try {
                        authServiceStub.logoutAll(grpcRequest);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                return null;
            } catch (Exception e) {
                log.error("Error during logout all via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("Auth service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<Void> changePassword(String token, String userId, String oldPassword, String newPassword) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.authservice.grpc.ChangePasswordRequest grpcRequest = com.authservice.grpc.ChangePasswordRequest
                        .newBuilder()
                        .setUserId(userId)
                        .setOldPassword(oldPassword)
                        .setNewPassword(newPassword)
                        .build();

                io.grpc.Metadata metadata = new io.grpc.Metadata();
                metadata.put(io.grpc.Metadata.Key.of("authorization", io.grpc.Metadata.ASCII_STRING_MARSHALLER), token);

                io.grpc.ClientInterceptor interceptor = io.grpc.stub.MetadataUtils
                        .newAttachHeadersInterceptor(metadata);
                AuthServiceGrpc.AuthServiceBlockingStub stubWithHeaders = authServiceStub.withInterceptors(interceptor);
                stubWithHeaders.changePassword(grpcRequest);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error changing password: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Password change failed");
            } catch (Exception e) {
                log.error("Unexpected error changing password: {}", e.getMessage(), e);
                throw new RuntimeException("Auth service error");
            }
        });
    }

    private TokenResponse mapToTokenResponse(com.authservice.grpc.TokenResponse grpcResponse) {
        return TokenResponse.builder()
                .accessToken(grpcResponse.getAccessToken())
                .refreshToken(grpcResponse.getRefreshToken())
                .tokenType(grpcResponse.getTokenType())
                .expiresIn(grpcResponse.getExpiresIn())
                .userId(grpcResponse.getUserId())
                .email(grpcResponse.getEmail())
                .name(grpcResponse.getName())
                .roles(new ArrayList<>(grpcResponse.getRolesList()))
                .build();
    }
}
