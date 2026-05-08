package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Coupon;
import com.pim.ecommerce.domain.entity.enums.DiscountType;
import com.pim.ecommerce.domain.repository.CouponRepository;
import com.pim.ecommerce.dto.request.CreateCouponRequest;
import com.pim.ecommerce.dto.response.CouponResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public CouponResponse create(CreateCouponRequest request) {
        String normalizedCode = normalizeCode(request.code());

        if (couponRepository.existsByCode(normalizedCode)) {
            throw new IllegalArgumentException("Já existe um cupom com este código");
        }

        validateCouponData(request.discountType(), request.discountValue(), request.usageLimit(), request.startsAt(), request.endsAt());

        Coupon coupon = Coupon.builder()
                .code(normalizedCode)
                .description(normalizeOptionalText(request.description()))
                .discountType(request.discountType())
                .discountValue(request.discountValue())
                .minimumOrderValue(request.minimumOrderValue() != null ? request.minimumOrderValue() : BigDecimal.ZERO)
                .active(request.active())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .usageLimit(request.usageLimit())
                .usedCount(0)
                .build();

        return toResponse(couponRepository.save(coupon));
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> findAll(Boolean active) {
        List<Coupon> coupons = active == null
                ? couponRepository.findAllByOrderByCreatedAtDesc()
                : couponRepository.findByActiveOrderByCreatedAtDesc(active);

        return coupons.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponResponse findById(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupom não encontrado"));

        return toResponse(coupon);
    }

    @Transactional
    public CouponResponse update(Long id, CreateCouponRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupom não encontrado"));

        String normalizedCode = normalizeCode(request.code());

        if (couponRepository.existsByCodeAndIdNot(normalizedCode, id)) {
            throw new IllegalArgumentException("Já existe um cupom com este código");
        }

        validateCouponData(request.discountType(), request.discountValue(), request.usageLimit(), request.startsAt(), request.endsAt());

        coupon.setCode(normalizedCode);
        coupon.setDescription(normalizeOptionalText(request.description()));
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());
        coupon.setMinimumOrderValue(request.minimumOrderValue() != null ? request.minimumOrderValue() : BigDecimal.ZERO);
        coupon.setActive(request.active());
        coupon.setStartsAt(request.startsAt());
        coupon.setEndsAt(request.endsAt());
        coupon.setUsageLimit(request.usageLimit());

        return toResponse(couponRepository.save(coupon));
    }

    @Transactional
    public CouponResponse deactivate(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupom não encontrado"));

        coupon.setActive(false);

        return toResponse(couponRepository.save(coupon));
    }

    @Transactional
    public CouponResponse activate(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupom não encontrado"));

        coupon.setActive(true);

        return toResponse(couponRepository.save(coupon));
    }

    @Transactional(readOnly = true)
    public Coupon findValidCouponForOrder(String code, BigDecimal itemsAmount) {
        Coupon coupon = couponRepository.findByCodeAndActiveTrue(normalizeCode(code))
                .orElseThrow(() -> new IllegalArgumentException("Cupom inválido ou inativo"));

        validateCouponForOrder(coupon, itemsAmount);

        return coupon;
    }

    public void validateCouponForOrder(Coupon coupon, BigDecimal itemsAmount) {
        if (coupon == null) {
            throw new IllegalArgumentException("Cupom inválido");
        }

        if (!Boolean.TRUE.equals(coupon.getActive())) {
            throw new IllegalArgumentException("Cupom inativo");
        }

        BigDecimal safeItemsAmount = itemsAmount != null ? itemsAmount : BigDecimal.ZERO;
        BigDecimal minimumOrderValue = coupon.getMinimumOrderValue() != null
                ? coupon.getMinimumOrderValue()
                : BigDecimal.ZERO;

        if (safeItemsAmount.compareTo(minimumOrderValue) < 0) {
            BigDecimal missingAmount = minimumOrderValue.subtract(safeItemsAmount);

            throw new IllegalArgumentException(
                    "Valor mínimo para este cupom: "
                            + formatCurrency(minimumOrderValue)
                            + ". Adicione mais "
                            + formatCurrency(missingAmount)
                            + " em produtos para usar este cupom."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            throw new IllegalArgumentException("Cupom ainda não está válido");
        }

        if (coupon.getEndsAt() != null && now.isAfter(coupon.getEndsAt())) {
            throw new IllegalArgumentException("Cupom expirado");
        }

        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new IllegalArgumentException("Cupom atingiu o limite de uso");
        }
    }

    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal itemsAmount) {
        validateCouponForOrder(coupon, itemsAmount);

        BigDecimal safeItemsAmount = itemsAmount != null ? itemsAmount : BigDecimal.ZERO;

        BigDecimal discount = switch (coupon.getDiscountType()) {
            case PERCENTAGE -> safeItemsAmount
                    .multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> coupon.getDiscountValue();
        };

        if (discount.compareTo(safeItemsAmount) > 0) {
            return safeItemsAmount;
        }

        return discount;
    }

    @Transactional
    public void registerCouponUse(Coupon coupon) {
        if (coupon == null) {
            return;
        }

        Coupon managedCoupon = couponRepository.findById(coupon.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cupom não encontrado"));

        validateCouponForOrder(managedCoupon, BigDecimal.ZERO.max(managedCoupon.getMinimumOrderValue()));

        int usedCount = managedCoupon.getUsedCount() != null ? managedCoupon.getUsedCount() : 0;
        managedCoupon.setUsedCount(usedCount + 1);

        couponRepository.save(managedCoupon);
    }

    private String formatCurrency(BigDecimal value) {
        BigDecimal safeValue = value != null ? value : BigDecimal.ZERO;

        return NumberFormat
                .getCurrencyInstance(new Locale("pt", "BR"))
                .format(safeValue.setScale(2, RoundingMode.HALF_UP));
    }

    private CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMinimumOrderValue(),
                coupon.getActive(),
                coupon.getStartsAt(),
                coupon.getEndsAt(),
                coupon.getUsageLimit(),
                coupon.getUsedCount(),
                isCurrentlyValid(coupon),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt()
        );
    }

    private boolean isCurrentlyValid(Coupon coupon) {
        try {
            validateCouponForOrder(coupon, coupon.getMinimumOrderValue());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void validateCouponData(
            DiscountType discountType,
            BigDecimal discountValue,
            Integer usageLimit,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        if (discountType == null) {
            throw new IllegalArgumentException("Tipo de desconto é obrigatório");
        }

        if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor do desconto deve ser maior que zero");
        }

        if (discountType == DiscountType.PERCENTAGE && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Desconto percentual não pode ser maior que 100%");
        }

        if (usageLimit != null && usageLimit < 1) {
            throw new IllegalArgumentException("Limite de uso deve ser maior que zero");
        }

        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("Data final do cupom não pode ser anterior à data inicial");
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Código do cupom é obrigatório");
        }

        return code.trim().toUpperCase();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}