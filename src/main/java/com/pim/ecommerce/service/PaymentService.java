package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.OrderItem;
import com.pim.ecommerce.domain.entity.Payment;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.entity.enums.PaymentMethod;
import com.pim.ecommerce.domain.entity.enums.PaymentStatus;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.PaymentRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.dto.request.CreatePaymentRequest;
import com.pim.ecommerce.dto.response.PaymentResponse;
import com.pim.ecommerce.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final CurrentUserService currentUserService;
    private final CodeGeneratorService codeGeneratorService;
    private final NotificationService notificationService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Transactional
    public PaymentResponse createPayment(Long orderId, CreatePaymentRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado"));

        currentUserService.ensureAdminOrOrderOwner(order);

        if (order.getStatus() != OrderStatus.CLOSED) {
            throw new IllegalArgumentException("Só é possível pagar pedidos com status CLOSED");
        }

        paymentRepository.findByOrderId(orderId).ifPresent(existingPayment -> {
            if (existingPayment.getStatus() == PaymentStatus.CANCELLED || existingPayment.getStatus() == PaymentStatus.FAILED) {
                return;
            }

            throw new IllegalArgumentException("Já existe um pagamento para este pedido");
        });

        String normalizedNotes = request.notes() != null && !request.notes().isBlank()
                ? request.notes().trim()
                : null;

        Payment payment = Payment.builder()
                .order(order)
                .method(request.paymentMethod())
                .amount(order.getTotalAmount())
                .notes(normalizedNotes)
                .status(PaymentStatus.PENDING)
                .build();

        order.setNotes(normalizedNotes);

        Payment savedPayment = paymentRepository.save(payment);

        switch (request.paymentMethod()) {
            case CREDIT_CARD -> handleCreditCard(savedPayment, request);
            case DEBIT_CARD -> handleDebitCard(savedPayment, request);
            case PIX -> handlePix(savedPayment);
            case BANK_SLIP -> handleBankSlip(savedPayment);
        }

        orderRepository.save(order);

        Payment updatedPayment = paymentRepository.save(savedPayment);
        return toResponse(updatedPayment);
    }

    @Transactional
    public PaymentResponse confirmPixPayment(Long paymentId, String token) {
        Payment payment = findPaymentById(paymentId);

        validateConfirmationToken(payment, token);

        if (payment.getMethod() != PaymentMethod.PIX) {
            throw new IllegalArgumentException("Este pagamento não é do tipo PIX");
        }

        approvePendingPayment(payment);

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse confirmBankSlipPayment(Long paymentId, String token) {
        Payment payment = findPaymentById(paymentId);

        validateConfirmationToken(payment, token);

        if (payment.getMethod() != PaymentMethod.BANK_SLIP) {
            throw new IllegalArgumentException("Este pagamento não é do tipo boleto");
        }

        approvePendingPayment(payment);

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse cancelPayment(Long paymentId) {
        Payment payment = findPaymentById(paymentId);

        currentUserService.ensureAdminOrPaymentOwner(payment);

        if (payment.getStatus() == PaymentStatus.APPROVED) {
            throw new IllegalArgumentException("Não é possível cancelar pagamento aprovado");
        }

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            return toResponse(payment);
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCancelledAt(LocalDateTime.now());

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse findByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado para este pedido"));

        currentUserService.ensureAdminOrPaymentOwner(payment);

        return toResponse(payment);
    }

    private void handleCreditCard(Payment payment, CreatePaymentRequest request) {
        validateCardData(request, true);

        payment.setInstallments(request.installments());
        payment.setCardLastFourDigits(extractLastFourDigits(request.cardNumber()));
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setConfirmedAt(LocalDateTime.now());

        finalizeOrderPayment(payment.getOrder(), payment.getMethod());
    }

    private void handleDebitCard(Payment payment, CreatePaymentRequest request) {
        validateCardData(request, false);

        payment.setInstallments(1);
        payment.setCardLastFourDigits(extractLastFourDigits(request.cardNumber()));
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setConfirmedAt(LocalDateTime.now());

        finalizeOrderPayment(payment.getOrder(), payment.getMethod());
    }

    private void handlePix(Payment payment) {
        String token = UUID.randomUUID().toString();

        String pixCode = "PIX-SIMULADO-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();

        String confirmationUrl = buildPixConfirmationUrl(payment.getId(), token);
        String qrCodeBase64 = codeGeneratorService.generateQrCodeBase64(confirmationUrl);

        payment.setStatus(PaymentStatus.PENDING);
        payment.setConfirmationToken(token);
        payment.setPixCode(pixCode);
        payment.setPixConfirmationUrl(confirmationUrl);
        payment.setPixQrCodeBase64(qrCodeBase64);
    }

    private void handleBankSlip(Payment payment) {
        String token = UUID.randomUUID().toString();

        String confirmationUrl = buildBankSlipConfirmationUrl(payment.getId(), token);
        String barCodeBase64 = codeGeneratorService.generateCode128BarCodeBase64(confirmationUrl);

        payment.setStatus(PaymentStatus.PENDING);
        payment.setConfirmationToken(token);
        payment.setDigitableLine(generateDigitableLine(payment.getId()));
        payment.setBankSlipBarCode(generateBarCode(payment.getId()));
        payment.setBankSlipConfirmationUrl(confirmationUrl);
        payment.setBankSlipBarCodeBase64(barCodeBase64);
    }

    private void approvePendingPayment(Payment payment) {
        if (payment.getStatus() == PaymentStatus.APPROVED) {
            return;
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalArgumentException("Só é possível confirmar pagamentos pendentes");
        }

        payment.setStatus(PaymentStatus.APPROVED);
        payment.setConfirmedAt(LocalDateTime.now());

        finalizeOrderPayment(payment.getOrder(), payment.getMethod());
    }

    private void finalizeOrderPayment(Order order, PaymentMethod paymentMethod) {
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }

        if (order.getStatus() != OrderStatus.CLOSED) {
            throw new IllegalArgumentException("Só é possível concluir pagamento para pedidos CLOSED");
        }

        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

            if (product.getReservedQuantity() < item.getQuantity()) {
                throw new IllegalArgumentException("Reserva inconsistente para o produto: " + product.getName());
            }

            if (product.getStockQuantity() < item.getQuantity()) {
                throw new IllegalArgumentException("Estoque insuficiente para o produto: " + product.getName());
            }

            product.setReservedQuantity(product.getReservedQuantity() - item.getQuantity());
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());

            productRepository.save(product);
        }

        order.setPaymentMethod(paymentMethod);
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setReservationExpiresAt(null);

        Order savedOrder = orderRepository.save(order);

        notificationService.notifyPaymentApproved(savedOrder);
    }

    private void validateConfirmationToken(Payment payment, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token de confirmação é obrigatório");
        }

        if (payment.getConfirmationToken() == null || !payment.getConfirmationToken().equals(token)) {
            throw new IllegalArgumentException("Token de confirmação inválido");
        }
    }

    private void validateCardData(CreatePaymentRequest request, boolean creditCard) {
        if (request.cardNumber() == null || request.cardHolderName() == null
                || request.expirationDate() == null || request.cvv() == null) {
            throw new IllegalArgumentException("Dados do cartão são obrigatórios");
        }

        String normalizedCardNumber = normalizeCardNumber(request.cardNumber());

        if (!normalizedCardNumber.matches("\\d{16}")) {
            throw new IllegalArgumentException("Número do cartão inválido");
        }

        if (!request.cardHolderName().trim().matches("[A-Za-zÀ-ÿ\\s]{3,120}")) {
            throw new IllegalArgumentException("Nome do titular inválido");
        }

        if (!request.expirationDate().matches("(0[1-9]|1[0-2])/\\d{2}")) {
            throw new IllegalArgumentException("Data de expiração inválida");
        }

        if (!request.cvv().matches("\\d{3,4}")) {
            throw new IllegalArgumentException("CVV inválido");
        }

        if (creditCard && (request.installments() == null || request.installments() < 1 || request.installments() > 12)) {
            throw new IllegalArgumentException("Número de parcelas inválido");
        }
    }

    private String normalizeCardNumber(String cardNumber) {
        return cardNumber.replaceAll("\\s+", "");
    }

    private String extractLastFourDigits(String cardNumber) {
        String normalized = normalizeCardNumber(cardNumber);
        return normalized.substring(normalized.length() - 4);
    }

    private String buildPixConfirmationUrl(Long paymentId, String token) {
        return appBaseUrl + "/payments/" + paymentId + "/pix-confirmation?token=" + token;
    }

    private String buildBankSlipConfirmationUrl(Long paymentId, String token) {
        return appBaseUrl + "/payments/" + paymentId + "/bank-slip-confirmation?token=" + token;
    }

    private String generateDigitableLine(Long paymentId) {
        String suffix = String.format("%011d", paymentId);
        return "34191.79001 01043.510047 91020.150008 5 " + suffix;
    }

    private String generateBarCode(Long paymentId) {
        return "34195" + String.format("%039d", paymentId);
    }

    private Payment findPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado"));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getInstallments(),
                payment.getCardLastFourDigits(),
                payment.getPixCode(),
                payment.getPixQrCodeBase64(),
                payment.getPixConfirmationUrl(),
                payment.getBankSlipBarCode(),
                payment.getBankSlipBarCodeBase64(),
                payment.getBankSlipConfirmationUrl(),
                payment.getDigitableLine(),
                payment.getNotes(),
                payment.getCreatedAt(),
                payment.getConfirmedAt(),
                payment.getCancelledAt()
        );
    }
}