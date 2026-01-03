package com.authservice.grpc;

import com.authservice.security.JwtTokenProvider;
import io.grpc.*;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@GrpcGlobalServerInterceptor
public class JwtGrpcInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String authHeader = headers.get(AUTHORIZATION_KEY);

        // Public methods (Register / Login / Refresh / Validate)
        if (isPublicMethod(call.getMethodDescriptor().getFullMethodName())) {
            return next.startCall(call, headers);
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing Authorization header"), headers);
            return new ServerCall.Listener<>() {};
        }

        String token = authHeader.substring(7);

        try {
            UUID userId = jwtTokenProvider.getUserId(token);
            String email = jwtTokenProvider.getEmail(token);
            Set<String> roles = jwtTokenProvider.getRoles(token);

            Context context = Context.current()
                    .withValue(GrpcContextKeys.USER_ID, userId)
                    .withValue(GrpcContextKeys.EMAIL, email)
                    .withValue(GrpcContextKeys.ROLES, roles);

            return Contexts.interceptCall(context, call, headers, next);

        } catch (Exception ex) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid JWT"), headers);
            return new ServerCall.Listener<>() {};
        }
    }

    private boolean isPublicMethod(String fullMethodName) {
        return fullMethodName.endsWith("Register")
                || fullMethodName.endsWith("Login")
                || fullMethodName.endsWith("RefreshToken")
                || fullMethodName.endsWith("ValidateToken");
    }
}
