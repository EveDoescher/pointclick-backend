package com.pim.ecommerce.support;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TestDatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void clean() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    review_images,
                    reviews,
                    payments,
                    order_items,
                    orders,
                    favorites,
                    notifications,
                    refresh_tokens,
                    coupons,
                    products,
                    users,
                    addresses
                RESTART IDENTITY CASCADE
                """);
    }
}