package com.ordersystem.repository;

import com.ordersystem.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryCustom {

    Optional<User> findByEmailGlobal(String email);

    boolean existsByEmailGlobal(String email);

    Optional<User> findByIdGlobal(UUID id);
}
