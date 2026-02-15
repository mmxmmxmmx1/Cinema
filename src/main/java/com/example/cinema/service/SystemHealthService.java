package com.example.cinema.service;

import org.springframework.stereotype.Service;

import com.example.cinema.dao.SystemHealthDao;

@Service
public class SystemHealthService {

    private final SystemHealthDao systemHealthDao;

    public SystemHealthService(SystemHealthDao systemHealthDao) {
        this.systemHealthDao = systemHealthDao;
    }

    public String databaseStatus() {
        try {
            Integer one = systemHealthDao.pingDatabase();
            return one != null && one.intValue() == 1 ? "UP" : "UNKNOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }
}
