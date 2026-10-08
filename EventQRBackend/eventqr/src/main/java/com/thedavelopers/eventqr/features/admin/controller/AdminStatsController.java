package com.thedavelopers.eventqr.features.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.admin.model.dto.AdminStatsResponse;
import com.thedavelopers.eventqr.features.admin.service.AdminStatsService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.response.ApiResponse;
import com.thedavelopers.eventqr.shared.security.JwtService;

@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;
    private final JwtService jwtService;

    public AdminStatsController(AdminStatsService adminStatsService, JwtService jwtService) {
        this.adminStatsService = adminStatsService;
        this.jwtService = jwtService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminStatsResponse>> stats(HttpServletRequest request) {
        AccountRole role = jwtService.extractRoleFromBearer(request.getHeader("Authorization"));
        if (role != AccountRole.ADMIN && role != AccountRole.SUPER_ADMIN) {
            throw new ForbiddenException("Admin access required");
        }
        return ResponseEntity.ok(ApiResponse.success(adminStatsService.stats(role)));
    }
}
