package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Address;
import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.OrderItem;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.integration.viacep.ViaCepClient;
import com.pim.ecommerce.dto.request.AddOrderItemRequest;
import com.pim.ecommerce.dto.request.CreateOrderRequest;
import com.pim.ecommerce.dto.request.UpdateOrderItemRequest;
import com.pim.ecommerce.dto.response.OrderResponse;
import com.pim.ecommerce.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.pim.ecommerce.domain.entity.Coupon;
import com.pim.ecommerce.domain.entity.enums.DiscountType;
import com.pim.ecommerce.domain.entity.enums.PaymentMethod;
import com.pim.ecommerce.dto.request.ApplyCouponRequest;
import com.pim.ecommerce.dto.request.ShippingQuoteRequest;
import com.pim.ecommerce.dto.response.ShippingQuoteResponse;
import com.pim.ecommerce.integration.viacep.ViaCepResponse;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private FreightService freightService;

    @Mock
    private ViaCepClient viaCepClient;

    @Mock
    private CouponService couponService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldCreatePendingOrderForUser() {
        User user = userWithAddress(2L);
        when(userRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(user));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(7L);
            return order;
        });

        OrderResponse response = orderService.create(new CreateOrderRequest(2L));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.itemsAmount()).isEqualByComparingTo("0.00");
        assertThat(response.items()).isEmpty();
        verify(currentUserService).ensureAdminOrSelf(2L);
    }

    @Test
    void shouldAddItemToPendingOrder() {
        Product product = product(1L, 5, 0);
        Order order = pendingOrder(userWithAddress(2L));

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.addItem(7L, new AddOrderItemRequest(1L, 2));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(response.itemsAmount()).isEqualByComparingTo("200.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("200.00");
        verify(currentUserService).ensureAdminOrOrderOwner(order);
    }

    @Test
    void shouldIncreaseQuantityWhenAddingSameProductAgain() {
        Product product = product(1L, 5, 0);
        Order order = pendingOrderWithItem(product, 2);

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.addItem(7L, new AddOrderItemRequest(1L, 1));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(3);
        assertThat(response.itemsAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void shouldRejectAddItemAboveAvailableStock() {
        Product product = product(1L, 2, 0);
        Order order = pendingOrder(userWithAddress(2L));

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.addItem(7L, new AddOrderItemRequest(1L, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Estoque insuficiente para o produto: Teclado Mecânico");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldCloseOrderAndReserveStock() {
        Product product = product(1L, 5, 0);
        Order order = pendingOrderWithItem(product, 2);

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));
        when(freightService.calculateFreightByDestinationCep("13480-370")).thenReturn(new BigDecimal("15.00"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.closeOrder(7L);

        assertThat(response.status()).isEqualTo(OrderStatus.CLOSED);
        assertThat(response.freightAmount()).isEqualByComparingTo("15.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("215.00");
        assertThat(response.deliveryAddress()).contains("Rua das Flores, 123");
        assertThat(response.reservationExpiresAt()).isNotNull();
        assertThat(product.getReservedQuantity()).isEqualTo(2);
        verify(productRepository).save(product);
    }

    @Test
    void shouldRejectCloseOrderWithoutItems() {
        Order order = pendingOrder(userWithAddress(2L));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.closeOrder(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Não é possível fechar um pedido sem itens");
    }

    @Test
    void shouldCancelClosedOrderAndReleaseReservation() {
        Product product = product(1L, 5, 2);
        Order order = pendingOrderWithItem(product, 2);
        order.setStatus(OrderStatus.CLOSED);
        order.setReservationExpiresAt(LocalDateTime.now().plusHours(20));

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.cancelOrder(7L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancelledAt()).isNotNull();
        assertThat(response.reservationExpiresAt()).isNull();
        assertThat(product.getReservedQuantity()).isZero();
        verify(notificationService).notifyOrderCancelled(order);
    }

    @Test
    void shouldRejectCancelPaidOrder() {
        Order order = pendingOrderWithItem(product(1L, 5, 2), 2);
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Não é possível cancelar pedidos após o pagamento");
    }

    @Test
    void shouldRejectConfirmDeliveryByNonOwner() {
        Order order = pendingOrderWithItem(product(1L, 5, 0), 2);
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(currentUserService.getCurrentUserId()).thenReturn(99L);

        assertThatThrownBy(() -> orderService.confirmDelivery(7L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Apenas o dono do pedido pode confirmar o recebimento");
    }

    private Order pendingOrder(User user) {
        return Order.builder()
                .id(7L)
                .user(user)
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .itemsAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .freightAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();
    }

    private Order pendingOrderWithItem(Product product, int quantity) {
        Order order = pendingOrder(userWithAddress(2L));
        OrderItem item = OrderItem.builder()
                .id(20L)
                .order(order)
                .product(product)
                .quantity(quantity)
                .unitPriceAtMoment(product.getPrice())
                .subtotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)))
                .build();
        order.getItems().add(item);
        order.setItemsAmount(item.getSubtotal());
        order.setTotalAmount(item.getSubtotal());
        return order;
    }

    private Product product(Long id, int stockQuantity, int reservedQuantity) {
        return Product.builder()
                .id(id)
                .name("Teclado Mecânico")
                .description("Descrição")
                .categoryGroup("Periféricos")
                .category("Teclados")
                .brand("PointClick")
                .price(new BigDecimal("100.00"))
                .stockQuantity(stockQuantity)
                .reservedQuantity(reservedQuantity)
                .active(true)
                .build();
    }

    private User userWithAddress(Long id) {
        return User.builder()
                .id(id)
                .fullName("Ana Souza")
                .email("ana@email.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .address(Address.builder()
                        .cep("13480-370")
                        .street("Rua das Flores")
                        .number("123")
                        .complement("Casa")
                        .city("Limeira")
                        .state("SP")
                        .build())
                .build();
    }

    @Test
    void shouldUpdateItemQuantityAndRecalculateAmounts() {
        Product product = mutationProduct(1L, 10, 0);
        Order order = mutationPendingOrderWithItem(product, 2);

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.updateItem(7L, 20L, new UpdateOrderItemRequest(4));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().quantity()).isEqualTo(4);
        assertThat(response.items().getFirst().subtotal()).isEqualByComparingTo("400.00");
        assertThat(response.itemsAmount()).isEqualByComparingTo("400.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("400.00");
        verify(currentUserService).ensureAdminOrOrderOwner(order);
    }

    @Test
    void shouldRejectUpdateItemWhenOrderIsNotPending() {
        Product product = mutationProduct(1L, 10, 0);
        Order order = mutationPendingOrderWithItem(product, 2);
        order.setStatus(OrderStatus.CLOSED);

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateItem(7L, 20L, new UpdateOrderItemRequest(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Só é possível alterar itens em pedidos com status PENDING");

        verify(productRepository, never()).findByIdAndActiveTrue(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldRemoveLastItemAndClearCoupon() {
        Product product = mutationProduct(1L, 10, 0);
        Order order = mutationPendingOrderWithItem(product, 2);
        order.setCoupon(mutationCoupon("POINT10", new BigDecimal("10.00")));
        order.setCouponCode("POINT10");
        order.setDiscountAmount(new BigDecimal("20.00"));
        order.setTotalAmount(new BigDecimal("180.00"));

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.removeItem(7L, 20L);

        assertThat(response.items()).isEmpty();
        assertThat(response.couponCode()).isNull();
        assertThat(response.itemsAmount()).isEqualByComparingTo("0.00");
        assertThat(response.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldApplyCouponAndRecalculateDiscount() {
        Product product = mutationProduct(1L, 10, 0);
        Order order = mutationPendingOrderWithItem(product, 2);
        Coupon coupon = mutationCoupon("POINT10", new BigDecimal("10.00"));

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(couponService.findValidCouponForOrder("POINT10", new BigDecimal("200.00"))).thenReturn(coupon);
        when(couponService.calculateDiscount(coupon, new BigDecimal("200.00"))).thenReturn(new BigDecimal("20.00"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.applyCoupon(7L, new ApplyCouponRequest("POINT10"));

        assertThat(response.couponCode()).isEqualTo("POINT10");
        assertThat(response.itemsAmount()).isEqualByComparingTo("200.00");
        assertThat(response.discountAmount()).isEqualByComparingTo("20.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("180.00");
    }

    @Test
    void shouldRemoveCouponAndRestoreTotalAmount() {
        Product product = mutationProduct(1L, 10, 0);
        Order order = mutationPendingOrderWithItem(product, 2);
        order.setCoupon(mutationCoupon("POINT10", new BigDecimal("10.00")));
        order.setCouponCode("POINT10");
        order.setDiscountAmount(new BigDecimal("20.00"));
        order.setTotalAmount(new BigDecimal("180.00"));

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.removeCoupon(7L);

        assertThat(response.couponCode()).isNull();
        assertThat(response.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void shouldRegisterCouponUseWhenClosingOrderWithCoupon() {
        Product product = mutationProduct(1L, 10, 0);
        Order order = mutationPendingOrderWithItem(product, 2);
        Coupon coupon = mutationCoupon("POINT10", new BigDecimal("10.00"));
        order.setCoupon(coupon);
        order.setCouponCode("POINT10");

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));
        when(freightService.calculateFreightByDestinationCep("13480-370")).thenReturn(new BigDecimal("15.00"));
        when(couponService.calculateDiscount(coupon, new BigDecimal("200.00"))).thenReturn(new BigDecimal("20.00"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.closeOrder(7L);

        assertThat(response.status()).isEqualTo(OrderStatus.CLOSED);
        assertThat(response.discountAmount()).isEqualByComparingTo("20.00");
        assertThat(response.freightAmount()).isEqualByComparingTo("15.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("195.00");
        assertThat(product.getReservedQuantity()).isEqualTo(2);
        verify(couponService).registerCouponUse(coupon);
    }

    @Test
    void shouldRejectCloseOrderWithoutAddress() {
        Product product = mutationProduct(1L, 10, 0);
        User user = mutationUserWithAddress(2L);
        user.setAddress(null);
        Order order = mutationPendingOrderWithItem(user, product, 2);

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.closeOrder(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cadastre um endereço antes de fechar o pedido");
    }

    @Test
    void shouldShipPaidOrderAsAdminAndNotifyCustomer() {
        Order order = mutationOrderWithStatus(OrderStatus.PAID);
        when(currentUserService.isAdmin()).thenReturn(true);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.shipOrder(7L);

        assertThat(response.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(response.shippedAt()).isNotNull();
        verify(notificationService).notifyOrderShipped(order);
    }

    @Test
    void shouldRejectShipOrderWhenCurrentUserIsNotAdmin() {
        when(currentUserService.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> orderService.shipOrder(7L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Apenas ADMIN pode executar esta operação");

        verify(orderRepository, never()).findById(any());
    }

    @Test
    void shouldDeliverShippedOrderAsAdminAndNotifyCustomer() {
        Order order = mutationOrderWithStatus(OrderStatus.SHIPPED);
        when(currentUserService.isAdmin()).thenReturn(true);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.deliverOrder(7L);

        assertThat(response.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(response.deliveredAt()).isNotNull();
        verify(notificationService).notifyOrderDelivered(order);
    }

    @Test
    void shouldFinishDeliveredOrderAsAdminAndNotifyCustomer() {
        Order order = mutationOrderWithStatus(OrderStatus.DELIVERED);
        when(currentUserService.isAdmin()).thenReturn(true);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.finishOrder(7L);

        assertThat(response.status()).isEqualTo(OrderStatus.FINISHED);
        assertThat(response.finishedAt()).isNotNull();
        verify(notificationService).notifyOrderFinished(order);
    }

    @Test
    void shouldConfirmDeliveryAsOrderOwner() {
        Order order = mutationOrderWithStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.confirmDelivery(7L);

        assertThat(response.status()).isEqualTo(OrderStatus.FINISHED);
        assertThat(response.finishedAt()).isNotNull();
        verify(notificationService).notifyOrderFinished(order);
    }

    @Test
    void shouldReopenExpiredClosedOrderAndReleaseReservation() {
        Product product = mutationProduct(1L, 10, 2);
        Order order = mutationPendingOrderWithItem(product, 2);
        order.setStatus(OrderStatus.CLOSED);
        order.setClosedAt(LocalDateTime.now().minusDays(2));
        order.setReservationExpiresAt(LocalDateTime.now().minusHours(1));
        order.setDeliveryAddress("Rua das Flores, 123 - Limeira/SP - CEP: 13480370");
        order.setNotes("Entregar à tarde");
        order.setFreightAmount(new BigDecimal("15.00"));
        order.setTotalAmount(new BigDecimal("215.00"));

        when(currentUserService.isAdmin()).thenReturn(true);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.reopenExpiredOrder(7L);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.closedAt()).isNull();
        assertThat(response.reservationExpiresAt()).isNull();
        assertThat(response.deliveryAddress()).isNull();
        assertThat(response.notes()).isNull();
        assertThat(response.freightAmount()).isEqualByComparingTo("0.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("200.00");
        assertThat(product.getReservedQuantity()).isZero();
    }

    @Test
    void shouldRejectReopenWhenReservationHasNotExpired() {
        Order order = mutationOrderWithStatus(OrderStatus.CLOSED);
        order.setReservationExpiresAt(LocalDateTime.now().plusHours(2));

        when(currentUserService.isAdmin()).thenReturn(true);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.reopenExpiredOrder(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A reserva do pedido ainda não expirou");
    }

    @Test
    void shouldQuoteShippingForPendingOrder() {
        Order order = mutationPendingOrder(mutationUserWithAddress(2L));
        ViaCepResponse viaCepResponse = new ViaCepResponse(
                "13480-370",
                "Rua das Flores",
                "Casa",
                "Centro",
                "Limeira",
                "SP",
                false
        );

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(viaCepClient.getAddressByCep("13480-370")).thenReturn(viaCepResponse);
        when(freightService.calculateFreightByDestinationCep("13480-370")).thenReturn(new BigDecimal("15.00"));

        ShippingQuoteResponse response = orderService.quoteShipping(7L, new ShippingQuoteRequest("13480-370"));

        assertThat(response.cep()).isEqualTo("13480370");
        assertThat(response.shippingPrice()).isEqualByComparingTo("15.00");
        assertThat(response.estimatedDays()).isEqualTo(5);
        assertThat(response.city()).isEqualTo("Limeira");
        assertThat(response.state()).isEqualTo("SP");
    }

    @Test
    void shouldBuildStatusSummaryForAdmin() {
        when(currentUserService.isAdmin()).thenReturn(true);
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(1L);
        when(orderRepository.countByStatus(OrderStatus.CLOSED)).thenReturn(2L);
        when(orderRepository.countByStatus(OrderStatus.PAID)).thenReturn(3L);
        when(orderRepository.countByStatus(OrderStatus.SHIPPED)).thenReturn(4L);
        when(orderRepository.countByStatus(OrderStatus.DELIVERED)).thenReturn(5L);
        when(orderRepository.countByStatus(OrderStatus.FINISHED)).thenReturn(6L);
        when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(7L);

        var response = orderService.getStatusSummary();

        assertThat(response.pending()).isEqualTo(1L);
        assertThat(response.closed()).isEqualTo(2L);
        assertThat(response.paid()).isEqualTo(3L);
        assertThat(response.shipped()).isEqualTo(4L);
        assertThat(response.delivered()).isEqualTo(5L);
        assertThat(response.finished()).isEqualTo(6L);
        assertThat(response.cancelled()).isEqualTo(7L);
    }

    @Test
    void shouldFindAdminOrdersFilteredByStatus() {
        Order paidOrder = mutationOrderWithStatus(OrderStatus.PAID);
        when(currentUserService.isAdmin()).thenReturn(true);
        when(orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.PAID)).thenReturn(List.of(paidOrder));

        var response = orderService.findAllForAdmin(OrderStatus.PAID);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().status()).isEqualTo(OrderStatus.PAID);
        assertThat(response.getFirst().customerEmail()).isEqualTo("ana@email.com");
    }

    @Test
    void shouldFindUserOrdersAsSummary() {
        Order order = mutationOrderWithStatus(OrderStatus.PAID);
        when(userRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(mutationUserWithAddress(2L)));
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(order));

        var response = orderService.findByUserId(2L);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().status()).isEqualTo(OrderStatus.PAID);
        verify(currentUserService).ensureAdminOrSelf(2L);
    }

    @Test
    void shouldReturnNullWhenCurrentCartDoesNotExist() {
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(orderRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(2L, OrderStatus.PENDING))
                .thenReturn(Optional.empty());

        assertThat(orderService.findCurrentCart()).isNull();
    }

    @Test
    void shouldFindMyOrdersUsingCurrentUser() {
        Order order = mutationOrderWithStatus(OrderStatus.CLOSED);
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(order));

        var response = orderService.findMyOrders();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().status()).isEqualTo(OrderStatus.CLOSED);
    }


    @Test
    void shouldRejectAddItemWhenOrderIsNotPending() {
        Order order = mutationOrderWithStatus(OrderStatus.CLOSED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.addItem(7L, new AddOrderItemRequest(1L, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Só é possível adicionar itens em pedidos com status PENDING");

        verify(productRepository, never()).findByIdAndActiveTrue(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldRejectAddItemWhenProductDoesNotExist() {
        Order order = mutationPendingOrder(mutationUserWithAddress(2L));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.addItem(7L, new AddOrderItemRequest(99L, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Produto não encontrado");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldRejectAddItemWithZeroQuantity() {
        Product product = mutationProduct(1L, 10, 0);
        Order order = mutationPendingOrder(mutationUserWithAddress(2L));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.addItem(7L, new AddOrderItemRequest(1L, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A quantidade deve ser maior que zero");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldRejectUpdateItemWhenItemDoesNotBelongToOrder() {
        Order order = mutationPendingOrderWithItem(mutationProduct(1L, 10, 0), 1);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateItem(7L, 999L, new UpdateOrderItemRequest(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Item não encontrado no pedido");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldRejectRemoveCouponWhenOrderIsNotPending() {
        Order order = mutationOrderWithStatus(OrderStatus.CLOSED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.removeCoupon(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Só é possível remover cupom em pedidos com status PENDING");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldRejectQuoteShippingWhenOrderIsNotPending() {
        Order order = mutationOrderWithStatus(OrderStatus.CLOSED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.quoteShipping(7L, new ShippingQuoteRequest("13480-370")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Só é possível simular frete para pedidos com status PENDING");

        verify(viaCepClient, never()).getAddressByCep(any());
        verify(freightService, never()).calculateFreightByDestinationCep(any());
    }

    @Test
    void shouldFindCurrentCartWhenItExists() {
        Order order = mutationPendingOrderWithItem(mutationProduct(1L, 10, 0), 1);
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(orderRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(2L, OrderStatus.PENDING))
                .thenReturn(Optional.of(order));

        OrderResponse response = orderService.findCurrentCart();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void shouldFindAllOrdersForAdminWhenStatusFilterIsNull() {
        Order paidOrder = mutationOrderWithStatus(OrderStatus.PAID);
        Order pendingOrder = mutationPendingOrder(mutationUserWithAddress(2L));
        when(currentUserService.isAdmin()).thenReturn(true);
        when(orderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(paidOrder, pendingOrder));

        var response = orderService.findAllForAdmin(null);

        assertThat(response).hasSize(2);
        assertThat(response).extracting("status").containsExactly(OrderStatus.PAID, OrderStatus.PENDING);
    }

    @Test
    void shouldRejectFindByUserIdWhenUserDoesNotExist() {
        when(userRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findByUserId(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuário não encontrado");

        verify(currentUserService).ensureAdminOrSelf(99L);
        verify(orderRepository, never()).findByUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void shouldRejectDeliverOrderWhenStatusIsNotShipped() {
        Order order = mutationOrderWithStatus(OrderStatus.PAID);
        when(currentUserService.isAdmin()).thenReturn(true);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.deliverOrder(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Só é possível marcar como entregue pedidos com status SHIPPED");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldRejectFinishOrderWhenStatusIsNotDelivered() {
        Order order = mutationOrderWithStatus(OrderStatus.SHIPPED);
        when(currentUserService.isAdmin()).thenReturn(true);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.finishOrder(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Só é possível finalizar pedidos com status DELIVERED");

        verify(orderRepository, never()).save(any());
    }

    private Order mutationPendingOrder(User user) {
        return Order.builder()
                .id(7L)
                .user(user)
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .itemsAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .freightAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();
    }

    private Order mutationPendingOrderWithItem(Product product, int quantity) {
        return mutationPendingOrderWithItem(mutationUserWithAddress(2L), product, quantity);
    }

    private Order mutationPendingOrderWithItem(User user, Product product, int quantity) {
        Order order = mutationPendingOrder(user);
        OrderItem item = OrderItem.builder()
                .id(20L)
                .order(order)
                .product(product)
                .quantity(quantity)
                .unitPriceAtMoment(product.getPrice())
                .subtotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)))
                .build();
        order.getItems().add(item);
        order.setItemsAmount(item.getSubtotal());
        order.setTotalAmount(item.getSubtotal());
        return order;
    }

    private Order mutationOrderWithStatus(OrderStatus status) {
        Product product = mutationProduct(1L, 10, 0);
        Order order = mutationPendingOrderWithItem(product, 1);
        order.setStatus(status);
        if (status == OrderStatus.PAID) {
            order.setPaymentMethod(PaymentMethod.CREDIT_CARD);
            order.setPaidAt(LocalDateTime.now().minusHours(1));
        }
        return order;
    }

    private Product mutationProduct(Long id, int stockQuantity, int reservedQuantity) {
        return Product.builder()
                .id(id)
                .name("Teclado Mecânico")
                .description("Descrição")
                .categoryGroup("Periféricos")
                .category("Teclados")
                .brand("PointClick")
                .price(new BigDecimal("100.00"))
                .stockQuantity(stockQuantity)
                .reservedQuantity(reservedQuantity)
                .active(true)
                .build();
    }

    private Coupon mutationCoupon(String code, BigDecimal discountValue) {
        return Coupon.builder()
                .id(5L)
                .code(code)
                .description("Cupom de teste")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(discountValue)
                .minimumOrderValue(BigDecimal.ZERO)
                .active(true)
                .usedCount(0)
                .build();
    }

    private User mutationUserWithAddress(Long id) {
        return User.builder()
                .id(id)
                .fullName("Ana Souza")
                .email("ana@email.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .address(Address.builder()
                        .cep("13480-370")
                        .street("Rua das Flores")
                        .number("123")
                        .complement("Casa")
                        .city("Limeira")
                        .state("SP")
                        .build())
                .build();
    }
}
