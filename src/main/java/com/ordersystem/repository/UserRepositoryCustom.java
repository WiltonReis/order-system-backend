package com.ordersystem.repository;

import com.ordersystem.entity.User;

import java.util.Optional;

public interface UserRepositoryCustom {

    Optional<User> findByEmailGlobal(String email);

    boolean existsByEmailGlobal(String email);
}
