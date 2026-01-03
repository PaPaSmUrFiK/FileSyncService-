package com.authservice.grpc;

import io.grpc.Context;

import java.util.Set;
import java.util.UUID;

public final class GrpcContextKeys {

    private GrpcContextKeys() {}

    public static final Context.Key<UUID> USER_ID =
            Context.key("userId");

    public static final Context.Key<String> EMAIL =
            Context.key("email");

    public static final Context.Key<Set<String>> ROLES =
            Context.key("roles");
}
