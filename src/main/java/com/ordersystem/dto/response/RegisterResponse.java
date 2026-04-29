package com.ordersystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class RegisterResponse {

    private UUID id;
    private String email;
    private String name;
    private UUID customerSaasId;
}
