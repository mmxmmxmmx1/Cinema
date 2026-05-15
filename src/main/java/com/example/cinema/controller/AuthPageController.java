package com.example.cinema.controller;

import com.example.cinema.service.SessionService.Realm;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class AuthPageController {

    private final PageSessionSupport pageSessionSupport;

    public AuthPageController(PageSessionSupport pageSessionSupport) {
        this.pageSessionSupport = pageSessionSupport;
    }

    @GetMapping({ "/login", "/login/" })
    public String unifiedLoginPage(
            @RequestParam(value = "target", required = false) String target,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            HttpSession session) {

        String normalized = target == null ? "" : target.trim().toLowerCase();
        boolean memberTarget = normalized.isEmpty() || "member".equals(normalized);
        boolean employeeTarget = "employee".equals(normalized) || "it".equals(normalized) || "manager".equals(normalized)
                || "admin".equals(normalized);

        if (memberTarget && pageSessionSupport.hasActiveSession(session, Realm.MEMBER)) {
            return "redirect:/member";
        }
        if (employeeTarget && pageSessionSupport.hasActiveSession(session, Realm.EMPLOYEE)) {
            return "redirect:/employee";
        }

        String targetLoginPath = employeeTarget ? "/employee/login" : "/member/login";
        UriComponentsBuilder redirect = UriComponentsBuilder.fromPath(targetLoginPath);
        if (error != null) {
            redirect.queryParam("error");
        }
        if (returnTo != null && !returnTo.isBlank()) {
            redirect.queryParam("returnTo", returnTo);
        }
        return "redirect:" + redirect.toUriString();
    }

    @GetMapping({ "/member/login", "/member/login/" })
    public String memberLoginPage(@RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            HttpSession session,
            Model model) {
        if (pageSessionSupport.hasActiveSession(session, Realm.MEMBER)) {
            return "redirect:/member";
        }
        return pageSessionSupport.renderLoginView("member-login", Realm.MEMBER, error, returnTo, session, model);
    }

    @GetMapping({ "/employee/login", "/employee/login/" })
    public String employeeLoginPage(@RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "returnTo", required = false) String returnTo,
            HttpSession session, Model model) {
        if (pageSessionSupport.hasActiveSession(session, Realm.EMPLOYEE)) {
            return "redirect:/employee";
        }
        return pageSessionSupport.renderLoginView("employee-login", Realm.EMPLOYEE, error, returnTo, session, model);
    }
}
