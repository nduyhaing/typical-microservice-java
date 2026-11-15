package com.shopapp.order.repository;

import com.shopapp.order.model.Order;
import com.shopapp.order.model.OrderItem;
import com.shopapp.order.model.enumeration.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Filters of the back-office order list (YAS GET /backoffice/orders; decisions.md 5-1). */
public final class OrderSpecifications {

    private static final char ESCAPE = '\\';

    private OrderSpecifications() {
    }

    /**
     * Orders created in [from, to] (either may be null), in one of the statuses (empty: any), whose email contains
     * {@code email} and with a line whose product name contains {@code productName} (case-insensitive, blank: any).
     */
    public static Specification<Order> matching(ZonedDateTime from, ZonedDateTime to,
                                                Collection<OrderStatus> statuses, String email,
                                                String productName) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdOn"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdOn"), to));
            }
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("orderStatus").in(statuses));
            }
            if (email != null && !email.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")), likeTerm(email), ESCAPE));
            }
            if (productName != null && !productName.isBlank() && query != null) {
                Subquery<Long> line = query.subquery(Long.class);
                var item = line.from(OrderItem.class);
                line.select(item.get("id")).where(cb.equal(item.get("orderId"), root.get("id")),
                    cb.like(cb.lower(item.get("productName")), likeTerm(productName), ESCAPE));
                predicates.add(cb.exists(line));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** {@code %term%} in lower case with LIKE wildcards escaped. */
    static String likeTerm(String value) {
        return "%" + value.strip().toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
