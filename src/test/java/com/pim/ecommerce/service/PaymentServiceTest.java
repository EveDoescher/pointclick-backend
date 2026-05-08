package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.OrderItem;
import com.pim.ecommerce.domain.entity.Payment;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.PaymentMethod;
import com.pim.ecommerce.domain.entity.enums.PaymentStatus;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.PaymentRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.dto.request.CreatePaymentRequest;
import com.pim.ecommerce.dto.response.PaymentResponse;
import com.pim.ecommerce.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private CodeGeneratorService codeGeneratorService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "appBaseUrl", "http://localhost:8080");
    }

    @Test
    void shouldApproveCreditCardPaymentAndFinalizeOrder() {
        Product product = product(1L, 10, 2);
        Order order = closedOrderWithItem(product, 2);

        CreatePaymentRequest request = new CreatePaymentRequest(
                PaymentMethod.CREDIT_CARD,
                "4111 1111 1111 1111",
                "ANA SOUZA",
                "12/30",
                "123",
                3,
                " Entregar em horário comercial "
        );

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(7L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(10L);
            }
            return payment;
        });
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.createPayment(7L, request);

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.method()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(response.installments()).isEqualTo(3);
        assertThat(response.cardLastFourDigits()).isEqualTo("1111");
        assertThat(response.notes()).isEqualTo("Entregar em horário comercial");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(order.getReservationExpiresAt()).isNull();
        assertThat(product.getStockQuantity()).isEqualTo(8);
        assertThat(product.getReservedQuantity()).isZero();

        verify(currentUserService).ensureAdminOrOrderOwner(order);
        verify(productRepository).save(product);
        verify(notificationService).notifyPaymentApproved(order);
    }

    @Test
    void shouldCreatePixPaymentAsPendingWithoutChangingStock() {
        Product product = product(1L, 10, 2);
        Order order = closedOrderWithItem(product, 2);

        CreatePaymentRequest request = new CreatePaymentRequest(
                PaymentMethod.PIX,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(7L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(10L);
            }
            return payment;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(codeGeneratorService.generateQrCodeBase64(anyString())).thenReturn("data:image/png;base64,abc");

        PaymentResponse response = paymentService.createPayment(7L, request);

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.method()).isEqualTo(PaymentMethod.PIX);
        assertThat(response.pixCode()).startsWith("PIX-SIMULADO-");
        assertThat(response.pixQrCodeBase64()).isEqualTo("data:image/png;base64,abc");
        assertThat(response.pixConfirmationUrl()).startsWith("http://localhost:8080/payments/10/pix-confirmation?token=");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(product.getStockQuantity()).isEqualTo(10);
        assertThat(product.getReservedQuantity()).isEqualTo(2);

        verify(productRepository, never()).save(any());
        verify(notificationService, never()).notifyPaymentApproved(any());
    }

    @Test
    void shouldConfirmPixPaymentAndFinalizeOrder() {
        Product product = product(1L, 10, 2);
        Order order = closedOrderWithItem(product, 2);
        Payment payment = Payment.builder()
                .id(10L)
                .order(order)
                .method(PaymentMethod.PIX)
                .status(PaymentStatus.PENDING)
                .amount(order.getTotalAmount())
                .confirmationToken("valid-token")
                .build();

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.confirmPixPayment(10L, "valid-token");

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getConfirmedAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(product.getStockQuantity()).isEqualTo(8);
        assertThat(product.getReservedQuantity()).isZero();
        verify(notificationService).notifyPaymentApproved(order);
    }

    @Test
    void shouldRejectPaymentWhenOrderIsNotClosed() {
        Order order = pendingOrder();
        CreatePaymentRequest request = new CreatePaymentRequest(
                PaymentMethod.PIX,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.createPayment(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Só é possível pagar pedidos com status CLOSED");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldRejectCreditCardWithInvalidExpirationMonth() {
        Order order = closedOrderWithItem(product(1L, 10, 2), 2);
        CreatePaymentRequest request = new CreatePaymentRequest(
                PaymentMethod.CREDIT_CARD,
                "4111111111111111",
                "ANA SOUZA",
                "13/30",
                "123",
                1,
                null
        );

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(7L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> paymentService.createPayment(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Data de expiração inválida");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidPixConfirmationToken() {
        Payment payment = Payment.builder()
                .id(10L)
                .order(closedOrderWithItem(product(1L, 10, 2), 2))
                .method(PaymentMethod.PIX)
                .status(PaymentStatus.PENDING)
                .confirmationToken("valid-token")
                .build();

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirmPixPayment(10L, "wrong-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token de confirmação inválido");

        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldNotCancelApprovedPayment() {
        Payment payment = Payment.builder()
                .id(10L)
                .order(closedOrderWithItem(product(1L, 10, 2), 2))
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.APPROVED)
                .amount(BigDecimal.valueOf(200))
                .build();

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancelPayment(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Não é possível cancelar pagamento aprovado");

        verify(paymentRepository, never()).save(any());
    }

    private Order pendingOrder() {
        return Order.builder()
                .id(7L)
                .user(user())
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>())
                .itemsAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .freightAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();
    }

    private Order closedOrderWithItem(Product product, int quantity) {
        Order order = Order.builder()
                .id(7L)
                .user(user())
                .status(OrderStatus.CLOSED)
                .items(new ArrayList<>())
                .itemsAmount(BigDecimal.valueOf(200))
                .discountAmount(BigDecimal.ZERO)
                .freightAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(200))
                .build();

        OrderItem item = OrderItem.builder()
                .id(99L)
                .order(order)
                .product(product)
                .quantity(quantity)
                .unitPriceAtMoment(BigDecimal.valueOf(100))
                .subtotal(BigDecimal.valueOf(200))
                .build();

        order.getItems().add(item);
        return order;
    }

    private Product product(Long id, int stockQuantity, int reservedQuantity) {
        return Product.builder()
                .id(id)
                .name("Teclado Mecânico")
                .description("Teclado premium")
                .categoryGroup("Periféricos")
                .category("Teclados")
                .brand("PointClick")
                .price(BigDecimal.valueOf(100))
                .stockQuantity(stockQuantity)
                .reservedQuantity(reservedQuantity)
                .active(true)
                .build();
    }

    private User user() {
        return User.builder()
                .id(2L)
                .fullName("Ana Souza")
                .email("ana@email.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build();
    }

    @Test
    void shouldApproveDebitCardPaymentWithOneInstallment() {
        Product product = mutationProduct(1L, 10, 2);
        Order order = mutationClosedOrderWithItem(product, 2);
        CreatePaymentRequest request = mutationCardRequest(PaymentMethod.DEBIT_CARD, null);

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(7L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(10L);
            }
            return payment;
        });
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.createPayment(7L, request);

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.method()).isEqualTo(PaymentMethod.DEBIT_CARD);
        assertThat(response.installments()).isEqualTo(1);
        assertThat(response.cardLastFourDigits()).isEqualTo("1111");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(product.getStockQuantity()).isEqualTo(8);
        assertThat(product.getReservedQuantity()).isZero();
    }

    @Test
    void shouldCreateBankSlipPaymentAsPendingWithBarcodeAndDigitableLine() {
        Order order = mutationClosedOrderWithItem(mutationProduct(1L, 10, 2), 2);
        CreatePaymentRequest request = new CreatePaymentRequest(PaymentMethod.BANK_SLIP, null, null, null, null, null, " boleto ");

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(7L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(10L);
            }
            return payment;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(codeGeneratorService.generateCode128BarCodeBase64(anyString())).thenReturn("data:image/png;base64,barcode");

        var response = paymentService.createPayment(7L, request);

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.method()).isEqualTo(PaymentMethod.BANK_SLIP);
        assertThat(response.notes()).isEqualTo("boleto");
        assertThat(response.bankSlipBarCode()).hasSize(44);
        assertThat(response.bankSlipBarCodeBase64()).isEqualTo("data:image/png;base64,barcode");
        assertThat(response.bankSlipConfirmationUrl()).startsWith("http://localhost:8080/payments/10/bank-slip-confirmation?token=");
        assertThat(response.digitableLine()).endsWith("00000000010");
        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldConfirmBankSlipPayment() {
        Product product = mutationProduct(1L, 10, 2);
        Order order = mutationClosedOrderWithItem(product, 2);
        Payment payment = mutationPendingPayment(10L, order, PaymentMethod.BANK_SLIP, "valid-token");

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.confirmBankSlipPayment(10L, "valid-token");

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(product.getStockQuantity()).isEqualTo(8);
        assertThat(product.getReservedQuantity()).isZero();
    }

    @Test
    void shouldRejectConfirmPixWhenPaymentMethodIsNotPix() {
        Payment payment = mutationPendingPayment(10L, mutationClosedOrderWithItem(mutationProduct(1L, 10, 2), 2), PaymentMethod.BANK_SLIP, "valid-token");
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirmPixPayment(10L, "valid-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Este pagamento não é do tipo PIX");
    }

    @Test
    void shouldRejectConfirmBankSlipWhenPaymentMethodIsNotBankSlip() {
        Payment payment = mutationPendingPayment(10L, mutationClosedOrderWithItem(mutationProduct(1L, 10, 2), 2), PaymentMethod.PIX, "valid-token");
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirmBankSlipPayment(10L, "valid-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Este pagamento não é do tipo boleto");
    }

    @Test
    void shouldIgnoreConfirmationWhenPaymentIsAlreadyApproved() {
        Product product = mutationProduct(1L, 10, 2);
        Order order = mutationClosedOrderWithItem(product, 2);
        Payment payment = mutationPendingPayment(10L, order, PaymentMethod.PIX, "valid-token");
        payment.setStatus(PaymentStatus.APPROVED);

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.confirmPixPayment(10L, "valid-token");

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
        verify(productRepository, never()).save(any());
        verify(notificationService, never()).notifyPaymentApproved(any());
    }

    @Test
    void shouldRejectConfirmationWhenPaymentIsNotPendingNorApproved() {
        Payment payment = mutationPendingPayment(10L, mutationClosedOrderWithItem(mutationProduct(1L, 10, 2), 2), PaymentMethod.PIX, "valid-token");
        payment.setStatus(PaymentStatus.CANCELLED);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirmPixPayment(10L, "valid-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Só é possível confirmar pagamentos pendentes");
    }

    @Test
    void shouldRejectBlankConfirmationToken() {
        Payment payment = mutationPendingPayment(10L, mutationClosedOrderWithItem(mutationProduct(1L, 10, 2), 2), PaymentMethod.PIX, "valid-token");
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirmPixPayment(10L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token de confirmação é obrigatório");
    }

    @Test
    void shouldRejectCreditCardWhenThereIsAnActivePaymentForOrder() {
        Order order = mutationClosedOrderWithItem(mutationProduct(1L, 10, 2), 2);
        Payment existing = mutationPendingPayment(10L, order, PaymentMethod.PIX, "token");

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(7L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.createPayment(7L, mutationCardRequest(PaymentMethod.CREDIT_CARD, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Já existe um pagamento para este pedido");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldAllowNewPaymentWhenPreviousPaymentWasCancelled() {
        Order order = mutationClosedOrderWithItem(mutationProduct(1L, 10, 2), 2);
        Payment cancelled = mutationPendingPayment(10L, order, PaymentMethod.PIX, "token");
        cancelled.setStatus(PaymentStatus.CANCELLED);

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(7L)).thenReturn(Optional.of(cancelled));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(11L);
            return payment;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(codeGeneratorService.generateQrCodeBase64(anyString())).thenReturn("qr");

        var response = paymentService.createPayment(7L, new CreatePaymentRequest(PaymentMethod.PIX, null, null, null, null, null, null));

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void shouldCancelPendingPayment() {
        Payment payment = mutationPendingPayment(10L, mutationClosedOrderWithItem(mutationProduct(1L, 10, 2), 2), PaymentMethod.PIX, "token");
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.cancelPayment(10L);

        assertThat(response.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(response.cancelledAt()).isNotNull();
        verify(currentUserService).ensureAdminOrPaymentOwner(payment);
    }

    @Test
    void shouldReturnCancelledPaymentWithoutSavingAgain() {
        Payment payment = mutationPendingPayment(10L, mutationClosedOrderWithItem(mutationProduct(1L, 10, 2), 2), PaymentMethod.PIX, "token");
        payment.setStatus(PaymentStatus.CANCELLED);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        var response = paymentService.cancelPayment(10L);

        assertThat(response.status()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldFindPaymentByOrderIdAndCheckPermission() {
        Payment payment = mutationPendingPayment(10L, mutationClosedOrderWithItem(mutationProduct(1L, 10, 2), 2), PaymentMethod.PIX, "token");
        when(paymentRepository.findByOrderId(7L)).thenReturn(Optional.of(payment));

        var response = paymentService.findByOrderId(7L);

        assertThat(response.id()).isEqualTo(10L);
        verify(currentUserService).ensureAdminOrPaymentOwner(payment);
    }

    @Test
    void shouldRejectInconsistentReservationWhenFinalizingPayment() {
        Product product = mutationProduct(1L, 10, 1);
        Order order = mutationClosedOrderWithItem(product, 2);
        CreatePaymentRequest request = mutationCardRequest(PaymentMethod.CREDIT_CARD, 1);

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(7L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> paymentService.createPayment(7L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reserva inconsistente para o produto: Teclado Mecânico");
    }

    @Test
    void shouldRejectInvalidCardDataVariations() {
        Order order = mutationClosedOrderWithItem(mutationProduct(1L, 10, 2), 2);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(7L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> paymentService.createPayment(7L, new CreatePaymentRequest(PaymentMethod.CREDIT_CARD, null, null, null, null, 1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Dados do cartão são obrigatórios");

        assertThatThrownBy(() -> paymentService.createPayment(7L, new CreatePaymentRequest(PaymentMethod.CREDIT_CARD, "123", "ANA SOUZA", "12/30", "123", 1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Número do cartão inválido");

        assertThatThrownBy(() -> paymentService.createPayment(7L, new CreatePaymentRequest(PaymentMethod.CREDIT_CARD, "4111111111111111", "A1", "12/30", "123", 1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nome do titular inválido");

        assertThatThrownBy(() -> paymentService.createPayment(7L, new CreatePaymentRequest(PaymentMethod.CREDIT_CARD, "4111111111111111", "ANA SOUZA", "12/30", "12", 1, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CVV inválido");

        assertThatThrownBy(() -> paymentService.createPayment(7L, new CreatePaymentRequest(PaymentMethod.CREDIT_CARD, "4111111111111111", "ANA SOUZA", "12/30", "123", 13, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Número de parcelas inválido");
    }

    private CreatePaymentRequest mutationCardRequest(PaymentMethod method, Integer installments) {
        return new CreatePaymentRequest(
                method,
                "4111 1111 1111 1111",
                "ANA SOUZA",
                "12/30",
                "123",
                installments,
                null
        );
    }

    private Payment mutationPendingPayment(Long id, Order order, PaymentMethod method, String token) {
        return Payment.builder()
                .id(id)
                .order(order)
                .method(method)
                .status(PaymentStatus.PENDING)
                .amount(order.getTotalAmount())
                .confirmationToken(token)
                .build();
    }

    private Order mutationClosedOrderWithItem(Product product, int quantity) {
        Order order = Order.builder()
                .id(7L)
                .user(mutationUser())
                .status(OrderStatus.CLOSED)
                .items(new ArrayList<>())
                .itemsAmount(BigDecimal.valueOf(200))
                .discountAmount(BigDecimal.ZERO)
                .freightAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(200))
                .build();

        OrderItem item = OrderItem.builder()
                .id(99L)
                .order(order)
                .product(product)
                .quantity(quantity)
                .unitPriceAtMoment(BigDecimal.valueOf(100))
                .subtotal(BigDecimal.valueOf(200))
                .build();

        order.getItems().add(item);
        return order;
    }

    private Product mutationProduct(Long id, int stockQuantity, int reservedQuantity) {
        return Product.builder()
                .id(id)
                .name("Teclado Mecânico")
                .description("Teclado premium")
                .categoryGroup("Periféricos")
                .category("Teclados")
                .brand("PointClick")
                .price(BigDecimal.valueOf(100))
                .stockQuantity(stockQuantity)
                .reservedQuantity(reservedQuantity)
                .active(true)
                .build();
    }

    private User mutationUser() {
        return User.builder()
                .id(2L)
                .fullName("Ana Souza")
                .email("ana@email.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build();
    }
}
