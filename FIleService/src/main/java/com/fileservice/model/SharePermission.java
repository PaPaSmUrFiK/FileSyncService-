package com.fileservice.model;

public enum SharePermission {
    READ, WRITE, ADMIN;

    public boolean canRead() {
        return true;
    }

    public boolean canWrite() {
        return this == WRITE || this == ADMIN;
    }

    public boolean canAdmin() {
        return this == ADMIN;
    }
}
