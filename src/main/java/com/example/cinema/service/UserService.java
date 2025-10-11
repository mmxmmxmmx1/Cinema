package com.example.cinema.service;

import com.example.cinema.dao.UserDao;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserDao userDao, PasswordEncoder passwordEncoder) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
    }

    public Long registerUser(String username, String rawPassword, boolean asAdmin) {
        String hash = passwordEncoder.encode(rawPassword);
        Long userId = userDao.createUser(username, hash);
        userDao.assignRole(userId, "ROLE_USER");
        if (asAdmin) {
            userDao.assignRole(userId, "ROLE_ADMIN");
        }
        return userId;
    }
}
