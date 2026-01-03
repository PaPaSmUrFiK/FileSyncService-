package com.authservice.grpc;


import com.authservice.security.JwtTokenProvider;
import com.authservice.service.AuthService;
import com.authservice.service.dto.TokenPairDto;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Set;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    /* =========================
       Register
       ========================= */

    @Override
    public void register(
            RegisterRequest request,
            StreamObserver<TokenResponse> responseObserver) {

        TokenPairDto dto = authService.register(
                request.getEmail(),
                request.getPassword(),
                request.getName()
        );

        responseObserver.onNext(mapTokenResponse(dto));
        responseObserver.onCompleted();
    }

    /* =========================
       Login
       ========================= */

    @Override
    public void login(
            LoginRequest request,
            StreamObserver<TokenResponse> responseObserver) {

        TokenPairDto dto = authService.login(
                request.getEmail(),
                request.getPassword(),
                request.hasDeviceInfo() ? request.getDeviceInfo() : null
        );

        responseObserver.onNext(mapTokenResponse(dto));
        responseObserver.onCompleted();
    }

    /* =========================
       Refresh
       ========================= */

    @Override
    public void refreshToken(
            RefreshTokenRequest request,
            StreamObserver<TokenResponse> responseObserver) {

        TokenPairDto dto = authService.refresh(request.getRefreshToken());

        responseObserver.onNext(mapTokenResponse(dto));
        responseObserver.onCompleted();
    }

    /* =========================
       Validate
       ========================= */

    @Override
    public void validateToken(
            ValidateTokenRequest request,
            StreamObserver<ValidationResponse> responseObserver) {

        try {
            authService.validate(request.getAccessToken());

            // Извлекаем информацию из токена напрямую, так как это публичный метод
            UUID userId = jwtTokenProvider.getUserId(request.getAccessToken());
            String email = jwtTokenProvider.getEmail(request.getAccessToken());
            Set<String> roles = jwtTokenProvider.getRoles(request.getAccessToken());

            ValidationResponse response = ValidationResponse.newBuilder()
                    .setIsValid(true)
                    .setUserId(userId.toString())
                    .setEmail(email)
                    .addAllRoles(roles)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            ValidationResponse response = ValidationResponse.newBuilder()
                    .setIsValid(false)
                    .setErrorMessage(ex.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /* =========================
       Logout
       ========================= */

    @Override
    public void logout(
            LogoutRequest request,
            StreamObserver<EmptyResponse> responseObserver) {

        authService.logout(request.getRefreshToken());

        responseObserver.onNext(EmptyResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    /* =========================
       Logout All
       ========================= */

    @Override
    public void logoutAll(
            LogoutAllRequest request,
            StreamObserver<EmptyResponse> responseObserver) {

        UUID userId = GrpcContextKeys.USER_ID.get();
        authService.logoutAll(userId);

        responseObserver.onNext(EmptyResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    /* =========================
       Role management
       ========================= */

    @Override
    public void assignRole(
            AssignRoleRequest request,
            StreamObserver<EmptyResponse> responseObserver) {

        authService.assignRole(
                UUID.fromString(request.getUserId()),
                request.getRoleName()
        );

        responseObserver.onNext(EmptyResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void revokeRole(
            RevokeRoleRequest request,
            StreamObserver<EmptyResponse> responseObserver) {

        authService.revokeRole(
                UUID.fromString(request.getUserId()),
                request.getRoleName()
        );

        responseObserver.onNext(EmptyResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void getUserRoles(
            GetUserRolesRequest request,
            StreamObserver<UserRolesResponse> responseObserver) {

        var roles = authService.getUserRoles(
                UUID.fromString(request.getUserId())
        );

        UserRolesResponse response = UserRolesResponse.newBuilder()
                .addAllRoles(roles)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /* =========================
       Mapper
       ========================= */

    private TokenResponse mapTokenResponse(TokenPairDto dto) {
        return TokenResponse.newBuilder()
                .setAccessToken(dto.getAccessToken())
                .setRefreshToken(dto.getRefreshToken())
                .setTokenType("Bearer")
                .setExpiresIn(dto.getExpiresIn())
                .setUserId(dto.getUserId().toString())
                .setEmail(dto.getEmail())
                .setName(dto.getName())
                .addAllRoles(dto.getRoles())
                .build();
    }
}

