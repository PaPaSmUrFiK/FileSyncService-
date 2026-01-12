package com.authservice.grpc;

import com.authservice.exception.AuthException;
import com.authservice.exception.TokenException;
import com.authservice.exception.UserBlockedException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;


@GrpcAdvice
public class GrpcExceptionMapper {

    @GrpcExceptionHandler(AuthException.class)
    public StatusRuntimeException handleAuth(AuthException ex) {
        return Status.UNAUTHENTICATED
                .withDescription(ex.getMessage())
                .asRuntimeException();
    }

    @GrpcExceptionHandler(TokenException.class)
    public StatusRuntimeException handleToken(TokenException ex) {
        return Status.UNAUTHENTICATED
                .withDescription(ex.getMessage())
                .asRuntimeException();
    }

    @GrpcExceptionHandler(UserBlockedException.class)
    public StatusRuntimeException handleBlocked(UserBlockedException ex) {
        return Status.PERMISSION_DENIED
                .withDescription(ex.getMessage())
                .asRuntimeException();
    }

    @GrpcExceptionHandler(Exception.class)
    public StatusRuntimeException handleUnknown(Exception ex) {
        return Status.INTERNAL
                .withDescription("Internal auth service error: " + ex.getMessage())
                .asRuntimeException();
    }
}

