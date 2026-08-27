// com/gigshield/dashboard/controller/DashboardController.java
package com.gigshield.dashboard.controller;

import com.gigshield.dashboard.dto.AdminDashboardResponse;
import com.gigshield.dashboard.dto.WorkerDashboardResponse;
import com.gigshield.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/worker")
    public ResponseEntity<WorkerDashboardResponse> workerDashboard(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(dashboardService.getWorkerDashboard(user.getUsername()));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminDashboardResponse> adminDashboard() {
        return ResponseEntity.ok(dashboardService.getAdminDashboard());
    }
}