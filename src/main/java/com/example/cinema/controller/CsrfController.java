package com.example.cinema.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CsrfController {

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        Map<String, String> payload = new LinkedHashMap<>();
        if (token != null) {
            payload.put("token", token.getToken());
            payload.put("headerName", token.getHeaderName());
            payload.put("parameterName", token.getParameterName());
        }
        return payload;
    }
}

