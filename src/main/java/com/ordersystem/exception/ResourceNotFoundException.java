package com.ordersystem.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super(switch (resource) {
            case "User" -> "Usuário não encontrado";
            case "Product" -> "Produto não encontrado";
            case "Order" -> "Pedido não encontrado";
            case "OrderItem" -> "Item do pedido não encontrado";
            default -> resource + " não encontrado";
        });
    }
}
