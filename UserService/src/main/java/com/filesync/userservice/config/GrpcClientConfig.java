package com.filesync.userservice.config;

import com.authservice.grpc.AuthServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Value("${grpc.client.auth-service.address}")
    private String authServiceAddress;

    @Bean
    public AuthServiceGrpc.AuthServiceBlockingStub authServiceBlockingStub() {
        // Parse address "static://localhost:9091" -> "localhost", 9091
        // Simplified parsing for now assuming static://host:port format
        String target = authServiceAddress.replace("static://", "");
        String[] parts = target.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        return AuthServiceGrpc.newBlockingStub(channel);
    }
}
