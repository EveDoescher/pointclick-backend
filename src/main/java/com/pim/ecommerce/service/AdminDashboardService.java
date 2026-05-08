package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.dto.response.AdminDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        Long pendingOrders = orderRepository.countByStatus(OrderStatus.PENDING);
        Long closedOrders = orderRepository.countByStatus(OrderStatus.CLOSED);
        Long paidOrders = orderRepository.countByStatus(OrderStatus.PAID);
        Long shippedOrders = orderRepository.countByStatus(OrderStatus.SHIPPED);
        Long deliveredOrders = orderRepository.countByStatus(OrderStatus.DELIVERED);
        Long finishedOrders = orderRepository.countByStatus(OrderStatus.FINISHED);
        Long cancelledOrders = orderRepository.countByStatus(OrderStatus.CANCELLED);

        BigDecimal totalRevenue = orderRepository.sumTotalAmountByStatusIn(List.of(
                OrderStatus.PAID,
                OrderStatus.SHIPPED,
                OrderStatus.DELIVERED,
                OrderStatus.FINISHED
        ));

        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }

        return new AdminDashboardResponse(
                orderRepository.count(),
                pendingOrders,
                closedOrders,
                paidOrders,
                shippedOrders,
                deliveredOrders,
                finishedOrders,
                cancelledOrders,
                totalRevenue,
                productRepository.countByActiveTrue(),
                productRepository.countByActiveFalse(),
                productRepository.countOutOfStockActiveProducts(),
                productRepository.countLowStockActiveProducts(LOW_STOCK_THRESHOLD),
                userRepository.countByActiveTrue(),
                userRepository.countByActiveFalse(),
                userRepository.countByRole(UserRole.CUSTOMER),
                userRepository.countByRole(UserRole.ADMIN)
        );
    }
}