package com.ordersystem.mapper;

import com.ordersystem.dto.response.UserResponse;
import com.ordersystem.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toUserResponse(User user);
}
