package com.authservice.exception;

public class UserBlockedException extends RuntimeException {
    public UserBlockedException(String reason) {
        super(reason != null ? reason : "User is blocked");
    }
}