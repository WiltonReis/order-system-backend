package com.ordersystem.dto.request;

import com.ordersystem.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserUpdateRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String name;

    private String password;

    @NotNull
    private Role role;
}
