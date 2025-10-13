package com.example.cinema.model;

import java.time.LocalDateTime;
import java.util.List;

public class User {
    private final Long id;
    private final String username;
    private final String passwordHash;
    private final LocalDateTime createdAt;
    private final List<Role> roles;

    public User(Long id, String username, String passwordHash, LocalDateTime createdAt, List<Role> roles) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.roles = roles;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Role> getRoles() {
        return roles;
    }
}
