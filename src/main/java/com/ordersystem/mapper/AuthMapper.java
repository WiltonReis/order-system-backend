package com.ordersystem.mapper;

import com.ordersystem.dto.response.RegisterResponse;
import com.ordersystem.entity.CustomerSaas;
import com.ordersystem.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    @Mapping(source = "user.id", target = "id")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.name", target = "name")
    @Mapping(source = "tenant.id", target = "customerSaasId")
    RegisterResponse toRegisterResponse(User user, CustomerSaas tenant);
}
