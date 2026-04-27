package com.ordersystem.dto.request;

import com.ordersystem.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserRoleRequest {

    @NotNull
    private Role role;
}
