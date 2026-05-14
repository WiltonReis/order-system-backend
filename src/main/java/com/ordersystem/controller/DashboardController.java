package com.ordersystem.controller;

import com.ordersystem.dto.response.DashboardResponse;
import com.ordersystem.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Métricas e indicadores de desempenho do tenant")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(
            summary = "Obter métricas do dashboard",
            description = "Retorna totais de pedidos, receita, taxa de cancelamento, ticket médio, " +
                          "top produtos e receita por dia. Período via `period` (TODAY/WEEK/MONTH/ALL) " +
                          "ou intervalo customizado via `startDate` + `endDate`."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Métricas retornadas"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de data inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestParam(defaultValue = "ALL") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(dashboardService.getDashboard(period, startDate, endDate));
    }
}
