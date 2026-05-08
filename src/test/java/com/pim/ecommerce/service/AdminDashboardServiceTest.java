package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    @Test
    void shouldBuildDashboardWithAllCounters() {
        when(orderRepository.count()).thenReturn(20L);
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(1L);
        when(orderRepository.countByStatus(OrderStatus.CLOSED)).thenReturn(2L);
        when(orderRepository.countByStatus(OrderStatus.PAID)).thenReturn(3L);
        when(orderRepository.countByStatus(OrderStatus.SHIPPED)).thenReturn(4L);
        when(orderRepository.countByStatus(OrderStatus.DELIVERED)).thenReturn(5L);
        when(orderRepository.countByStatus(OrderStatus.FINISHED)).thenReturn(6L);
        when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(7L);
        when(orderRepository.sumTotalAmountByStatusIn(eq(List.of(
                OrderStatus.PAID,
                OrderStatus.SHIPPED,
                OrderStatus.DELIVERED,
                OrderStatus.FINISHED
        )))).thenReturn(new BigDecimal("1234.56"));

        when(productRepository.countByActiveTrue()).thenReturn(10L);
        when(productRepository.countByActiveFalse()).thenReturn(2L);
        when(productRepository.countOutOfStockActiveProducts()).thenReturn(1L);
        when(productRepository.countLowStockActiveProducts(5)).thenReturn(3L);
        when(userRepository.countByActiveTrue()).thenReturn(8L);
        when(userRepository.countByActiveFalse()).thenReturn(1L);
        when(userRepository.countByRole(UserRole.CUSTOMER)).thenReturn(7L);
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        var response = adminDashboardService.getDashboard();

        assertThat(response.totalOrders()).isEqualTo(20L);
        assertThat(response.pendingOrders()).isEqualTo(1L);
        assertThat(response.closedOrders()).isEqualTo(2L);
        assertThat(response.paidOrders()).isEqualTo(3L);
        assertThat(response.totalRevenue()).isEqualByComparingTo("1234.56");
        assertThat(response.activeProducts()).isEqualTo(10L);
        assertThat(response.lowStockProducts()).isEqualTo(3L);
        assertThat(response.customerUsers()).isEqualTo(7L);
        assertThat(response.adminUsers()).isEqualTo(1L);
    }

    @Test
    void shouldUseZeroRevenueWhenRepositoryReturnsNull() {
        when(orderRepository.count()).thenReturn(0L);
        for (OrderStatus status : OrderStatus.values()) {
            when(orderRepository.countByStatus(status)).thenReturn(0L);
        }
        when(orderRepository.sumTotalAmountByStatusIn(eq(List.of(
                OrderStatus.PAID,
                OrderStatus.SHIPPED,
                OrderStatus.DELIVERED,
                OrderStatus.FINISHED
        )))).thenReturn(null);
        when(productRepository.countByActiveTrue()).thenReturn(0L);
        when(productRepository.countByActiveFalse()).thenReturn(0L);
        when(productRepository.countOutOfStockActiveProducts()).thenReturn(0L);
        when(productRepository.countLowStockActiveProducts(5)).thenReturn(0L);
        when(userRepository.countByActiveTrue()).thenReturn(0L);
        when(userRepository.countByActiveFalse()).thenReturn(0L);
        when(userRepository.countByRole(UserRole.CUSTOMER)).thenReturn(0L);
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(0L);

        var response = adminDashboardService.getDashboard();

        assertThat(response.totalRevenue()).isEqualByComparingTo("0.00");
    }
}
