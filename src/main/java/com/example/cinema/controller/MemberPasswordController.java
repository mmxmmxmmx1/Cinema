package com.example.cinema.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.cinema.exception.UserRegistrationException;
import com.example.cinema.service.UserService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/member/password")
public class MemberPasswordController {

    private final UserService userService;

    public MemberPasswordController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/forgot")
    public String forgotPage(
            @RequestParam(name = "username", required = false) String username,
            Model model) {
        model.addAttribute("username", username == null ? "" : username.trim());
        return "member-password-forgot";
    }

    @PostMapping("/forgot")
    public String issueResetToken(
            @RequestParam("username") String username,
            RedirectAttributes redirectAttributes) {
        String safeUsername = username == null ? "" : username.trim();
        try {
            String token = userService.issueMemberPasswordResetToken(safeUsername);
            redirectAttributes.addFlashAttribute("success", "若帳號存在，已建立 15 分鐘內有效的重設碼。");
            if (token != null) {
                // Demo mode: show token directly because this project does not integrate email/SMS.
                redirectAttributes.addFlashAttribute("demoToken", token);
            }
            return "redirect:/member/password/reset?username=" + safeUsername;
        } catch (UserRegistrationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/member/password/forgot";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "重設碼建立失敗，請稍後再試。");
            return "redirect:/member/password/forgot";
        }
    }

    @GetMapping("/reset")
    public String resetPage(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "token", required = false) String token,
            Model model) {
        model.addAttribute("username", username == null ? "" : username.trim());
        model.addAttribute("token", token == null ? "" : token.trim());
        return "member-password-reset";
    }

    @PostMapping("/reset")
    public String resetPassword(
            @RequestParam("username") String username,
            @RequestParam("token") String token,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes redirectAttributes) {
        String safeUsername = username == null ? "" : username.trim();
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "新密碼與確認密碼不一致。");
            return "redirect:/member/password/reset?username=" + safeUsername;
        }

        try {
            userService.resetMemberPasswordWithToken(safeUsername, token, newPassword);
            redirectAttributes.addFlashAttribute("success", "密碼重設成功，請使用新密碼登入。");
            return "redirect:/member/login";
        } catch (UserRegistrationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/member/password/reset?username=" + safeUsername;
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "密碼重設失敗，請稍後再試。");
            return "redirect:/member/password/reset?username=" + safeUsername;
        }
    }

    @GetMapping("/change")
    public String changePage() {
        return "member-password-change";
    }

    @PostMapping("/change")
    public String changePassword(
            Authentication authentication,
            HttpSession session,
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes redirectAttributes) {
        Authentication auth = authentication != null
                ? authentication
                : SecurityContextHolder.getContext().getAuthentication();
        if (auth == null && session != null) {
            Object securityContext = session.getAttribute("SPRING_SECURITY_CONTEXT");
            if (securityContext instanceof SecurityContextImpl context) {
                auth = context.getAuthentication();
            }
        }
        String username = auth == null ? null : auth.getName();
        if (username == null || username.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "請先登入會員帳號。");
            return "redirect:/member/login";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "新密碼與確認密碼不一致。");
            return "redirect:/member/password/change";
        }

        try {
            userService.changeMemberPassword(username, currentPassword, newPassword);
            redirectAttributes.addFlashAttribute("success", "密碼已更新，請以新密碼重新登入。");
            return "redirect:/member/login";
        } catch (UserRegistrationException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/member/password/change";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "密碼更新失敗，請稍後再試。");
            return "redirect:/member/password/change";
        }
    }
}
