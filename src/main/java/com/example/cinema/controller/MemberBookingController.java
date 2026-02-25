package com.example.cinema.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/member/api", "/member/api/v1" })
public class MemberBookingController {

    @PostMapping("/bookings")
    public ResponseEntity<Void> purchase() {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }
}
