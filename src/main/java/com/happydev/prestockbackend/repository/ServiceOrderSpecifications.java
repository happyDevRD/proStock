package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.ServiceOrder;
import com.happydev.prestockbackend.entity.ServiceOrderStatus;
import com.happydev.prestockbackend.entity.ServiceOrderType;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ServiceOrderSpecifications {

    private ServiceOrderSpecifications() {}

    public static Specification<ServiceOrder> withFilters(
            ServiceOrderType type,
            ServiceOrderStatus status,
            Boolean activeOnly,
            String search
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (type != null) {
                predicates.add(cb.equal(root.get("orderType"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (Boolean.TRUE.equals(activeOnly)) {
                predicates.add(root.get("status").in(
                        ServiceOrderStatus.OPEN,
                        ServiceOrderStatus.IN_PROGRESS,
                        ServiceOrderStatus.WAITING_CLIENT,
                        ServiceOrderStatus.READY
                ));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                var customer = root.join("customer", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("orderNumber")), pattern),
                        cb.like(cb.lower(root.get("deviceBrand")), pattern),
                        cb.like(cb.lower(root.get("deviceModel")), pattern),
                        cb.like(cb.lower(customer.get("firstName")), pattern),
                        cb.like(cb.lower(customer.get("lastName")), pattern)
                ));
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
