package com.example.cinema.controller;

import com.example.cinema.config.SessionConstants;
import com.example.cinema.dto.MemberBooking;
import com.example.cinema.dto.MemberSummary;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/member")
public class MemberApiController {

    @GetMapping("/summary")
    public ResponseEntity<MemberSummary> summary(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute(SessionConstants.MEMBER_SESSION_KEY))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MemberSummary summary = new MemberSummary(
                12450,
                List.of(
                        new MemberBooking("Dune: Part Two", "12/08 18:40", "1 號廳"),
                        new MemberBooking("Oppenheimer", "12/12 20:10", "2 號廳")
                )
        );
        return ResponseEntity.ok(summary);
    }
}
