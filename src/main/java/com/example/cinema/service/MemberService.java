package com.example.cinema.service;

import com.example.cinema.dao.MemberDao;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    private final MemberDao memberDao;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberDao memberDao, PasswordEncoder passwordEncoder) {
        this.memberDao = memberDao;
        this.passwordEncoder = passwordEncoder;
    }

    public Long createMemberWithHashedPassword(String firstName, String lastName, String email, String phone, String rawPassword) {
        String hashed = passwordEncoder.encode(rawPassword); // e.g. {bcrypt}$2a...
        return memberDao.createMember(firstName, lastName, email, phone, hashed);
    }
}

