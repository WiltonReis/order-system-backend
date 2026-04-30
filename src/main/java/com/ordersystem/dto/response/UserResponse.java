package com.ordersystem.dto.response;

import com.ordersystem.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String email;
    private String name;
    private Role role;
}
