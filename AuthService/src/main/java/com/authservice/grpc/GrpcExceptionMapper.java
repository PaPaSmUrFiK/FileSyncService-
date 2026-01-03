package com.authservice.grpc;

import com.authservice.exception.AuthException;
import com.authservice.exception.TokenException;
import com.authservice.exception.UserBlockedException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;


@GrpcAdvice
public class GrpcExceptionMapper {

    @GrpcExceptionHandler(AuthException.class)
    public void handleAuth(
            AuthException ex,
            StreamObserver<?> observer) {

        observer.onError(
                Status.UNAUTHENTICATED
                        .withDescription(ex.getMessage())
                        .asRuntimeException()
        );
    }

    @GrpcExceptionHandler(TokenException.class)
    public void handleToken(
            TokenException ex,
            StreamObserver<?> observer) {

        observer.onError(
                Status.UNAUTHENTICATED
                        .withDescription(ex.getMessage())
                        .asRuntimeException()
        );
    }

    @GrpcExceptionHandler(UserBlockedException.class)
    public void handleBlocked(
            UserBlockedException ex,
            StreamObserver<?> observer) {

        observer.onError(
                Status.PERMISSION_DENIED
                        .withDescription(ex.getMessage())
                        .asRuntimeException()
        );
    }

    @GrpcExceptionHandler(Exception.class)
    public void handleUnknown(
            Exception ex,
            StreamObserver<?> observer) {

        observer.onError(
                Status.INTERNAL
                        .withDescription("Internal auth service error")
                        .asRuntimeException()
        );
    }
}

