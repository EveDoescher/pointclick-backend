package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Address;
import com.pim.ecommerce.domain.entity.Coupon;
import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.OrderItem;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.dto.request.AddOrderItemRequest;
import com.pim.ecommerce.dto.request.ApplyCouponRequest;
import com.pim.ecommerce.dto.request.CreateOrderRequest;
import com.pim.ecommerce.dto.request.ShippingQuoteRequest;
import com.pim.ecommerce.dto.request.UpdateOrderItemRequest;
import com.pim.ecommerce.dto.response.AdminOrderSummaryResponse;
import com.pim.ecommerce.dto.response.OrderItemResponse;
import com.pim.ecommerce.dto.response.OrderResponse;
import com.pim.ecommerce.dto.response.OrderStatusSummaryResponse;
import com.pim.ecommerce.dto.response.OrderSummaryResponse;
import com.pim.ecommerce.dto.response.ShippingQuoteResponse;
import com.pim.ecommerce.integration.viacep.ViaCepClient;
import com.pim.ecommerce.integration.viacep.ViaCepResponse;
import com.pim.ecommerce.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int RESERVATION_HOURS = 24;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ProductRepository productRepository;
    private final FreightService freightService;
    private final ViaCepClient viaCepClient;
    private final CouponService couponService;
    private final NotificationService notificationService;

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        currentUserService.ensureAdminOrSelf(request.userId());

        User user = userRepository.findByIdAndActiveTrue(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .itemsAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .freightAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();

        Order savedOrder = orderRepository.save(order);
        return toResponse(savedOrder);
    }

    @Transactional
    public OrderResponse addItem(Long orderId, AddOrderItemRequest request) {
        Order order = findOrderById(orderId);

        currentUserService.ensureAdminOrOrderOwner(order);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Só é possível adicionar itens em pedidos com status PENDING");
        }

        Product product = productRepository.findByIdAndActiveTrue(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        validateAvailableStock(product, request.quantity());

        OrderItem existingItem = order.getItems()
                .stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        int currentQuantityInOrder = existingItem != null ? existingItem.getQuantity() : 0;
        int newTotalQuantity = currentQuantityInOrder + request.quantity();

        validateAvailableStock(product, newTotalQuantity);

        if (existingItem != null) {
            existingItem.setQuantity(newTotalQuantity);
            existingItem.setUnitPriceAtMoment(product.getPrice());
            existingItem.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(newTotalQuantity)));
        } else {
            OrderItem newItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(request.quantity())
                    .unitPriceAtMoment(product.getPrice())
                    .subtotal(product.getPrice().multiply(BigDecimal.valueOf(request.quantity())))
                    .build();

            order.getItems().add(newItem);
        }

        recalculateAmounts(order);

        Order savedOrder = orderRepository.save(order);
        return toResponse(savedOrder);
    }

    @Transactional
    public OrderResponse updateItem(Long orderId, Long itemId, UpdateOrderItemRequest request) {
        Order order = findOrderById(orderId);

        currentUserService.ensureAdminOrOrderOwner(order);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Só é possível alterar itens em pedidos com status PENDING");
        }

        OrderItem item = findItemInOrder(order, itemId);

        Product product = productRepository.findByIdAndActiveTrue(item.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        validateAvailableStock(product, request.quantity());

        item.setQuantity(request.quantity());
        item.setUnitPriceAtMoment(product.getPrice());
        item.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(request.quantity())));

        recalculateAmounts(order);

        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse removeItem(Long orderId, Long itemId) {
        Order order = findOrderById(orderId);

        currentUserService.ensureAdminOrOrderOwner(order);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Só é possível remover itens em pedidos com status PENDING");
        }

        OrderItem item = findItemInOrder(order, itemId);

        order.getItems().remove(item);

        if (order.getItems().isEmpty()) {
            clearCoupon(order);
        }

        recalculateAmounts(order);

        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse applyCoupon(Long orderId, ApplyCouponRequest request) {
        Order order = findOrderById(orderId);

        currentUserService.ensureAdminOrOrderOwner(order);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Só é possível aplicar cupom em pedidos com status PENDING");
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Adicione itens ao carrinho antes de aplicar cupom");
        }

        Coupon coupon = couponService.findValidCouponForOrder(request.code(), order.getItemsAmount());

        order.setCoupon(coupon);
        order.setCouponCode(coupon.getCode());

        recalculateAmounts(order);

        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse removeCoupon(Long orderId) {
        Order order = findOrderById(orderId);

        currentUserService.ensureAdminOrOrderOwner(order);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Só é possível remover cupom em pedidos com status PENDING");
        }

        clearCoupon(order);
        recalculateAmounts(order);

        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse closeOrder(Long orderId) {
        Order order = findOrderById(orderId);

        currentUserService.ensureAdminOrOrderOwner(order);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Só é possível fechar pedidos com status PENDING");
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Não é possível fechar um pedido sem itens");
        }

        validateOrderItemsStock(order);

        Address userAddress = order.getUser().getAddress();

        order.setDeliveryAddress(buildDeliveryAddressSnapshot(userAddress));

        BigDecimal freightAmount = freightService.calculateFreightByDestinationCep(userAddress.getCep());
        order.setFreightAmount(freightAmount);

        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findByIdAndActiveTrue(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

            item.setUnitPriceAtMoment(product.getPrice());
            item.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));

            product.setReservedQuantity(product.getReservedQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        recalculateAmounts(order);

        if (order.getCoupon() != null) {
            couponService.registerCouponUse(order.getCoupon());
        }

        LocalDateTime now = LocalDateTime.now();

        order.setStatus(OrderStatus.CLOSED);
        order.setClosedAt(now);
        order.setReservationExpiresAt(now.plusHours(RESERVATION_HOURS));

        Order closedOrder = orderRepository.save(order);
        return toResponse(closedOrder);
    }

    @Transactional
    public OrderResponse shipOrder(Long orderId) {
        ensureAdmin();

        Order order = findOrderById(orderId);

        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalArgumentException("Só é possível enviar pedidos com status PAID");
        }

        order.setStatus(OrderStatus.SHIPPED);
        order.setShippedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        notificationService.notifyOrderShipped(savedOrder);

        return toResponse(savedOrder);
    }

    @Transactional
    public OrderResponse deliverOrder(Long orderId) {
        ensureAdmin();

        Order order = findOrderById(orderId);

        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new IllegalArgumentException("Só é possível marcar como entregue pedidos com status SHIPPED");
        }

        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        notificationService.notifyOrderDelivered(savedOrder);

        return toResponse(savedOrder);
    }

    @Transactional
    public OrderResponse confirmDelivery(Long orderId) {
        Order order = findOrderById(orderId);

        Long currentUserId = currentUserService.getCurrentUserId();

        if (!order.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Apenas o dono do pedido pode confirmar o recebimento");
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Só é possível confirmar recebimento de pedidos com status DELIVERED");
        }

        order.setStatus(OrderStatus.FINISHED);
        order.setFinishedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        notificationService.notifyOrderFinished(savedOrder);

        return toResponse(savedOrder);
    }

    @Transactional
    public OrderResponse finishOrder(Long orderId) {
        ensureAdmin();

        Order order = findOrderById(orderId);

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Só é possível finalizar pedidos com status DELIVERED");
        }

        order.setStatus(OrderStatus.FINISHED);
        order.setFinishedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        notificationService.notifyOrderFinished(savedOrder);

        return toResponse(savedOrder);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order order = findOrderById(orderId);

        currentUserService.ensureAdminOrOrderOwner(order);

        if (order.getStatus() == OrderStatus.PAID ||
                order.getStatus() == OrderStatus.SHIPPED ||
                order.getStatus() == OrderStatus.DELIVERED ||
                order.getStatus() == OrderStatus.FINISHED) {
            throw new IllegalArgumentException("Não é possível cancelar pedidos após o pagamento");
        }

        if (order.getStatus() == OrderStatus.CLOSED) {
            releaseReservation(order);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setReservationExpiresAt(null);

        Order savedOrder = orderRepository.save(order);

        notificationService.notifyOrderCancelled(savedOrder);

        return toResponse(savedOrder);
    }

    @Transactional
    public OrderResponse reopenExpiredOrder(Long orderId) {
        ensureAdmin();

        Order order = findOrderById(orderId);

        if (order.getStatus() != OrderStatus.CLOSED) {
            throw new IllegalArgumentException("Só é possível reabrir pedidos com status CLOSED");
        }

        if (order.getReservationExpiresAt() == null || LocalDateTime.now().isBefore(order.getReservationExpiresAt())) {
            throw new IllegalArgumentException("A reserva do pedido ainda não expirou");
        }

        reopenExpiredOrderInternal(order);

        Order reopenedOrder = orderRepository.save(order);
        return toResponse(reopenedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long orderId) {
        Order order = findOrderById(orderId);

        currentUserService.ensureAdminOrOrderOwner(order);

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findByIdForAdmin(Long orderId) {
        ensureAdmin();

        return toResponse(findOrderById(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> findByUserId(Long userId) {
        currentUserService.ensureAdminOrSelf(userId);

        userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findCurrentCart() {
        Long currentUserId = currentUserService.getCurrentUserId();

        return orderRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(
                        currentUserId,
                        OrderStatus.PENDING
                )
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> findMyOrders() {
        Long currentUserId = currentUserService.getCurrentUserId();

        return orderRepository.findByUserIdOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminOrderSummaryResponse> findAllForAdmin(OrderStatus status) {
        ensureAdmin();

        List<Order> orders;

        if (status != null) {
            orders = orderRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDesc();
        }

        return orders.stream()
                .map(this::toAdminSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShippingQuoteResponse quoteShipping(Long orderId, ShippingQuoteRequest request) {
        Order order = findOrderById(orderId);

        currentUserService.ensureAdminOrOrderOwner(order);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Só é possível simular frete para pedidos com status PENDING");
        }

        ViaCepResponse address = viaCepClient.getAddressByCep(request.cep());
        BigDecimal shippingPrice = freightService.calculateFreightByDestinationCep(request.cep());

        return new ShippingQuoteResponse(
                address.cep().replaceAll("\\D", ""),
                shippingPrice,
                5,
                address.logradouro(),
                address.bairro(),
                address.localidade(),
                address.uf()
        );
    }

    @Transactional(readOnly = true)
    public OrderStatusSummaryResponse getStatusSummary() {
        ensureAdmin();

        return new OrderStatusSummaryResponse(
                orderRepository.countByStatus(OrderStatus.PENDING),
                orderRepository.countByStatus(OrderStatus.CLOSED),
                orderRepository.countByStatus(OrderStatus.PAID),
                orderRepository.countByStatus(OrderStatus.SHIPPED),
                orderRepository.countByStatus(OrderStatus.DELIVERED),
                orderRepository.countByStatus(OrderStatus.FINISHED),
                orderRepository.countByStatus(OrderStatus.CANCELLED)
        );
    }

    private void reopenExpiredOrderInternal(Order order) {
        releaseReservation(order);

        order.setStatus(OrderStatus.PENDING);
        order.setClosedAt(null);
        order.setPaidAt(null);
        order.setPaymentMethod(null);
        order.setReservationExpiresAt(null);
        order.setDeliveryAddress(null);
        order.setNotes(null);
        order.setFreightAmount(BigDecimal.ZERO);

        recalculateAmounts(order);
    }

    private void releaseReservation(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

            int newReserved = product.getReservedQuantity() - item.getQuantity();
            product.setReservedQuantity(Math.max(newReserved, 0));

            productRepository.save(product);
        }
    }

    private void validateOrderItemsStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findByIdAndActiveTrue(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

            validateAvailableStock(product, item.getQuantity());
        }
    }

    private void validateAvailableStock(Product product, int requestedQuantity) {
        int reservedQuantity = product.getReservedQuantity() != null
                ? product.getReservedQuantity()
                : 0;

        int stockQuantity = product.getStockQuantity() != null
                ? product.getStockQuantity()
                : 0;

        int availableQuantity = stockQuantity - reservedQuantity;

        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero");
        }

        if (availableQuantity < requestedQuantity) {
            throw new IllegalArgumentException("Estoque insuficiente para o produto: " + product.getName());
        }
    }

    private void recalculateAmounts(Order order) {
        BigDecimal itemsAmount = order.getItems()
                .stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (order.getFreightAmount() == null) {
            order.setFreightAmount(BigDecimal.ZERO);
        }

        BigDecimal discountAmount = BigDecimal.ZERO;

        if (order.getCoupon() != null) {
            couponService.validateCouponForOrder(order.getCoupon(), itemsAmount);
            discountAmount = couponService.calculateDiscount(order.getCoupon(), itemsAmount);
        }

        BigDecimal totalAmount = itemsAmount
                .subtract(discountAmount)
                .add(order.getFreightAmount());

        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        order.setItemsAmount(itemsAmount);
        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(totalAmount);
    }

    private String buildDeliveryAddressSnapshot(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("Cadastre um endereço antes de fechar o pedido");
        }

        String complementPart = (address.getComplement() != null && !address.getComplement().isBlank())
                ? ", " + address.getComplement().trim()
                : "";

        return String.format(
                "%s, %s%s - %s/%s - CEP: %s",
                address.getStreet().trim(),
                address.getNumber().trim(),
                complementPart,
                address.getCity().trim(),
                address.getState().trim().toUpperCase(),
                address.getCep().replaceAll("\\D", "")
        );
    }

    private OrderItem findItemInOrder(Order order, Long itemId) {
        return order.getItems()
                .stream()
                .filter(orderItem -> orderItem.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item não encontrado no pedido"));
    }

    private Order findOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado"));
    }

    private void clearCoupon(Order order) {
        order.setCoupon(null);
        order.setCouponCode(null);
        order.setDiscountAmount(BigDecimal.ZERO);
    }

    private void ensureAdmin() {
        if (!currentUserService.isAdmin()) {
            throw new AccessDeniedException("Apenas ADMIN pode executar esta operação");
        }
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getUser().getFullName(),
                order.getOrderDate(),
                order.getItemsAmount(),
                order.getDiscountAmount(),
                order.getFreightAmount(),
                order.getTotalAmount(),
                order.getCouponCode(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getDeliveryAddress(),
                order.getNotes(),
                order.getClosedAt(),
                order.getPaidAt(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getFinishedAt(),
                order.getCancelledAt(),
                order.getReservationExpiresAt(),
                order.getItems().stream().map(this::toItemResponse).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        Product product = item.getProduct();

        return new OrderItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getCategory(),
                product.getImageUrl(),
                item.getQuantity(),
                item.getUnitPriceAtMoment(),
                item.getSubtotal(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private OrderSummaryResponse toSummaryResponse(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderDate(),
                order.getItemsAmount(),
                order.getDiscountAmount(),
                order.getFreightAmount(),
                order.getTotalAmount(),
                order.getCouponCode(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getCreatedAt()
        );
    }

    private AdminOrderSummaryResponse toAdminSummaryResponse(Order order) {
        return new AdminOrderSummaryResponse(
                order.getId(),
                order.getUser().getId(),
                order.getUser().getFullName(),
                order.getUser().getEmail(),
                order.getStatus(),
                order.getItemsAmount(),
                order.getDiscountAmount(),
                order.getFreightAmount(),
                order.getTotalAmount(),
                order.getCouponCode(),
                order.getPaymentMethod(),
                order.getPaidAt(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getFinishedAt(),
                order.getCreatedAt()
        );
    }
}