package com.filesync.userservice.security;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

@GrpcGlobalServerInterceptor
public class AuthHeaderServerInterceptor implements ServerInterceptor {

    public static final Context.Key<String> AUTH_TOKEN_CTX_KEY = Context.key("AuthToken");
    private static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY = Metadata.Key.of("authorization",
            Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String token = headers.get(AUTHORIZATION_METADATA_KEY);
        Context context = Context.current();
        if (token != null) {
            context = context.withValue(AUTH_TOKEN_CTX_KEY, token);
        }
        return Contexts.interceptCall(context, call, headers, next);
    }
}
