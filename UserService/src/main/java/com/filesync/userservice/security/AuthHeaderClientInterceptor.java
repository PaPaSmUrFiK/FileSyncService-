package com.filesync.userservice.security;

import io.grpc.*;

public class AuthHeaderClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY = Metadata.Key.of("authorization",
            Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String token = AuthHeaderServerInterceptor.AUTH_TOKEN_CTX_KEY.get();
                if (token != null) {
                    headers.put(AUTHORIZATION_METADATA_KEY, token);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
