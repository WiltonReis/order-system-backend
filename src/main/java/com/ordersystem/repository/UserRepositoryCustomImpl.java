package com.ordersystem.repository;

import com.ordersystem.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;

import java.util.Optional;
import java.util.UUID;

public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<User> findByEmailGlobal(String email) {
        Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        return entityManager.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                .setParameter("email", email)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public boolean existsByEmailGlobal(String email) {
        Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        Long count = entityManager.createQuery("SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                .setParameter("email", email)
                .getSingleResult();
        return count != null && count > 0;
    }

    @Override
    public Optional<User> findByIdGlobal(UUID id) {
        Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        return entityManager.createQuery("SELECT u FROM User u WHERE u.id = :id", User.class)
                .setParameter("id", id)
                .getResultList()
                .stream()
                .findFirst();
    }
}
