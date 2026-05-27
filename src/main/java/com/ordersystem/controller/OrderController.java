package com.ordersystem.controller;

import com.ordersystem.dto.request.OrderFilterParams;
import com.ordersystem.dto.request.OrderFullRequest;
import com.ordersystem.dto.request.OrderItemRequest;
import com.ordersystem.dto.request.OrderItemUpdateRequest;
import com.ordersystem.dto.request.OrderRequest;
import com.ordersystem.dto.request.OrderUpdateRequest;
import com.ordersystem.dto.response.*;
import com.ordersystem.enums.OrderStatus;
import com.ordersystem.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Criação, consulta e gerenciamento do ciclo de vida dos pedidos")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Criar pedido", description = "Cria pedido vazio. Itens podem ser adicionados posteriormente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido criado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "422", description = "Regra de negócio violada", content = @Content)
    })
    public ResponseEntity<OrderResponse> create(@RequestBody(required = false) OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }

    @PostMapping("/full")
    @Operation(summary = "Criar pedido completo", description = "Cria pedido com itens e desconto opcional em transação única.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado", content = @Content),
            @ApiResponse(responseCode = "422", description = "Itens duplicados ou desconto inválido", content = @Content)
    })
    public ResponseEntity<OrderDetailResponse> createFull(@Valid @RequestBody OrderFullRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createFull(request));
    }

    @GetMapping("/details")
    @Operation(summary = "Listar pedidos com filtros", description = "Busca paginada com filtros por status, usuário, datas, código e nome do cliente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    public ResponseEntity<Page<OrderDetailResponse>> findAllDetails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<OrderStatus> statuses,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "newest") String sort) {

        OrderFilterParams params = new OrderFilterParams();
        params.setStatuses(statuses);
        params.setUserId(userId);
        params.setCustomerName(customerName);
        params.setOrderCode(orderCode);
        params.setSort(sort);
        if (startDate != null) params.setStartDate(LocalDate.parse(startDate));
        if (endDate != null) params.setEndDate(LocalDate.parse(endDate));

        return ResponseEntity.ok(orderService.findAllDetailsFiltered(params, page, size));
    }

    @GetMapping
    @Operation(summary = "Listar pedidos (paginado)")
    public ResponseEntity<Page<OrderListResponse>> findAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.findAll(pageable));
    }

    @GetMapping("/active")
    @Operation(summary = "Listar pedidos em aberto")
    public ResponseEntity<Page<OrderListResponse>> findActive(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.findActive(pageable));
    }

    @GetMapping("/history")
    @Operation(summary = "Histórico de pedidos", description = "Retorna pedidos com status COMPLETED ou CANCELED.")
    public ResponseEntity<Page<OrderListResponse>> findHistory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.findHistory(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pedido por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content)
    })
    public ResponseEntity<OrderDetailResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ADMIN_MASTER')")
    @Operation(summary = "Aplicar desconto", description = "Aplica desconto no pedido. Restrito a ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Desconto aplicado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "422", description = "Pedido não está aberto ou desconto excede subtotal", content = @Content)
    })
    public ResponseEntity<OrderUpdateResponse> applyDiscount(@PathVariable UUID id,
                                                             @Valid @RequestBody OrderUpdateRequest request) {
        return ResponseEntity.ok(orderService.applyDiscount(id, request));
    }

    @PutMapping("/{id}/complete")
    @Operation(summary = "Concluir pedido", description = "Transição OPEN → COMPLETED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido concluído"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "422", description = "Transição de status inválida", content = @Content)
    })
    public ResponseEntity<OrderResponse> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.completeOrder(id));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancelar pedido", description = "Transição OPEN → CANCELED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido cancelado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "422", description = "Transição de status inválida", content = @Content)
    })
    public ResponseEntity<OrderResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ADMIN_MASTER')")
    @Operation(summary = "Excluir pedido", description = "Soft-delete. Pode ser desfeito em até 60 segundos. Restrito a ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido excluído"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content)
    })
    public ResponseEntity<MessageResponse> delete(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.delete(id));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','ADMIN_MASTER')")
    @Operation(summary = "Restaurar pedido excluído", description = "Desfaz exclusão dentro da janela de 60 segundos. Restrito a ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido restaurado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "422", description = "Janela de restauração expirada ou pedido não excluído", content = @Content)
    })
    public ResponseEntity<MessageResponse> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.restore(id));
    }

    @GetMapping(value = "/{id}/pdf", produces = "application/pdf")
    @Operation(summary = "Exportar PDF do pedido", description = "Gera PDF com dados completos do pedido. Retorna 404 para pedidos soft-deleted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF gerado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content)
    })
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id) {
        byte[] pdf = orderService.exportPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("pedido-" + id + ".pdf")
                .build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping("/{id}/status-history")
    @Operation(summary = "Histórico de status", description = "Retorna todas as transições de status do pedido em ordem cronológica decrescente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico retornado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content)
    })
    public ResponseEntity<List<OrderStatusHistoryResponse>> statusHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getStatusHistory(id));
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Adicionar item ao pedido", description = "Adiciona produto ao pedido. Requer pedido em status OPEN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Item adicionado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido ou produto não encontrado", content = @Content),
            @ApiResponse(responseCode = "422", description = "Pedido não está aberto", content = @Content)
    })
    public ResponseEntity<OrderItemResponse> addItem(@PathVariable UUID id,
                                                     @Valid @RequestBody OrderItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.addItem(id, request));
    }

    @PutMapping("/{id}/items/{itemId}")
    @Operation(summary = "Atualizar quantidade do item")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido ou item não encontrado", content = @Content),
            @ApiResponse(responseCode = "422", description = "Pedido não está aberto", content = @Content)
    })
    public ResponseEntity<OrderItemUpdateResponse> updateItem(@PathVariable UUID id,
                                                              @PathVariable UUID itemId,
                                                              @Valid @RequestBody OrderItemUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateItem(id, itemId, request));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "Remover item do pedido")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item removido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido ou item não encontrado", content = @Content),
            @ApiResponse(responseCode = "422", description = "Pedido não está aberto", content = @Content)
    })
    public ResponseEntity<MessageResponse> removeItem(@PathVariable UUID id,
                                                      @PathVariable UUID itemId) {
        return ResponseEntity.ok(orderService.removeItem(id, itemId));
    }
}
