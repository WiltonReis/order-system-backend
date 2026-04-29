package com.ordersystem.specification;

import com.ordersystem.dto.request.OrderFilterParams;
import com.ordersystem.entity.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class OrderSpecification {

    private OrderSpecification() {}

    public static Specification<Order> from(OrderFilterParams p) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (p.getStatuses() != null && !p.getStatuses().isEmpty()) {
                predicates.add(root.get("status").in(p.getStatuses()));
            }

            if (p.getUserId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), p.getUserId()));
            }

            if (p.getCustomerName() != null && !p.getCustomerName().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("customerName")),
                        "%" + p.getCustomerName().toLowerCase().trim() + "%"));
            }

            if (p.getOrderCode() != null && !p.getOrderCode().isBlank()) {
                predicates.add(cb.like(root.get("orderCode"),
                        "%" + p.getOrderCode().trim() + "%"));
            }

            if (p.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        p.getStartDate().atStartOfDay()));
            }

            if (p.getEndDate() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        p.getEndDate().plusDays(1).atStartOfDay()));
            }

            String sort = p.getSort();
            if ("most_items".equals(sort) || "least_items".equals(sort)) {
                if (!Long.class.isAssignableFrom(query.getResultType())) {
                    var itemCount = cb.size(root.get("items"));
                    query.orderBy("most_items".equals(sort) ? cb.desc(itemCount) : cb.asc(itemCount));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
