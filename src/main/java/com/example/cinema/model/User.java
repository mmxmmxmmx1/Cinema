package com.example.cinema.model;

import java.time.LocalDateTime;
import java.util.List;

public class User {
    
    public enum UserType {
        CUSTOMER,   // 純會員
        EMPLOYEE,   // 純員工
        BOTH        // 既是會員又是員工
    }
    
    private final Long id;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final String password;
    private final UserType userType;
    private final LocalDateTime createdAt;
    private final List<Role> roles;

    public User(Long id,
                String username,
                String firstName,
                String lastName,
                String email,
                String phone,
                String password,
                UserType userType,
                LocalDateTime createdAt,
                List<Role> roles) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.userType = userType;
        this.createdAt = createdAt;
        this.roles = roles;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPassword() {
        return password;
    }

    public UserType getUserType() {
        return userType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Role> getRoles() {
        return roles;
    }
    
    public boolean isCustomer() {
        return userType == UserType.CUSTOMER || userType == UserType.BOTH;
    }
    
    public boolean isEmployee() {
        return userType == UserType.EMPLOYEE || userType == UserType.BOTH;
    }
}
