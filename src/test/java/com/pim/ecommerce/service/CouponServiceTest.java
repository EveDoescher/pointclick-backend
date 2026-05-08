package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Coupon;
import com.pim.ecommerce.domain.entity.enums.DiscountType;
import com.pim.ecommerce.domain.repository.CouponRepository;
import com.pim.ecommerce.dto.request.CreateCouponRequest;
import com.pim.ecommerce.dto.response.CouponResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CouponService couponService;

    @Test
    void shouldCreateCouponWithNormalizedCodeAndDefaults() {
        CreateCouponRequest request = new CreateCouponRequest(
                " point10 ",
                " Cupom de boas-vindas ",
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                null,
                true,
                null,
                null,
                null
        );

        when(couponRepository.existsByCode("POINT10")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> {
            Coupon coupon = invocation.getArgument(0);
            coupon.setId(1L);
            return coupon;
        });

        CouponResponse response = couponService.create(request);

        assertThat(response.code()).isEqualTo("POINT10");
        assertThat(response.description()).isEqualTo("Cupom de boas-vindas");
        assertThat(response.minimumOrderValue()).isEqualByComparingTo("0.00");
        assertThat(response.usedCount()).isZero();
        assertThat(response.currentlyValid()).isTrue();
    }

    @Test
    void shouldRejectDuplicatedCode() {
        CreateCouponRequest request = new CreateCouponRequest(
                "POINT10",
                null,
                DiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                true,
                null,
                null,
                null
        );

        when(couponRepository.existsByCode("POINT10")).thenReturn(true);

        assertThatThrownBy(() -> couponService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Já existe um cupom com este código");
    }

    @Test
    void shouldRejectPercentageAboveOneHundred() {
        CreateCouponRequest request = new CreateCouponRequest(
                "POINT110",
                null,
                DiscountType.PERCENTAGE,
                new BigDecimal("110.00"),
                BigDecimal.ZERO,
                true,
                null,
                null,
                null
        );

        when(couponRepository.existsByCode("POINT110")).thenReturn(false);

        assertThatThrownBy(() -> couponService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Desconto percentual não pode ser maior que 100%");
    }

    @Test
    void shouldCalculatePercentageDiscount() {
        Coupon coupon = coupon("POINT10", DiscountType.PERCENTAGE, new BigDecimal("10.00"));

        BigDecimal discount = couponService.calculateDiscount(coupon, new BigDecimal("799.80"));

        assertThat(discount).isEqualByComparingTo("79.98");
    }

    @Test
    void shouldCapFixedDiscountAtItemsAmount() {
        Coupon coupon = coupon("POINT999", DiscountType.FIXED_AMOUNT, new BigDecimal("999.00"));

        BigDecimal discount = couponService.calculateDiscount(coupon, new BigDecimal("399.90"));

        assertThat(discount).isEqualByComparingTo("399.90");
    }

    @Test
    void shouldRejectExpiredCoupon() {
        Coupon coupon = coupon("OLD10", DiscountType.PERCENTAGE, new BigDecimal("10.00"));
        coupon.setEndsAt(LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> couponService.validateCouponForOrder(coupon, new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cupom expirado");
    }

    @Test
    void shouldRejectMinimumOrderValueNotReached() {
        Coupon coupon = coupon("MIN1000", DiscountType.PERCENTAGE, new BigDecimal("10.00"));
        coupon.setMinimumOrderValue(new BigDecimal("1000.00"));

        assertThatThrownBy(() -> couponService.validateCouponForOrder(coupon, new BigDecimal("399.90")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Valor mínimo para este cupom")
                .hasMessageContaining("Adicione mais");
    }

    @Test
    void shouldRegisterCouponUse() {
        Coupon coupon = coupon("POINT10", DiscountType.PERCENTAGE, new BigDecimal("10.00"));
        coupon.setId(1L);
        coupon.setUsedCount(2);
        coupon.setMinimumOrderValue(BigDecimal.ZERO);

        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        couponService.registerCouponUse(coupon);

        assertThat(coupon.getUsedCount()).isEqualTo(3);
        verify(couponRepository).save(coupon);
    }

    private Coupon coupon(String code, DiscountType type, BigDecimal value) {
        return Coupon.builder()
                .id(1L)
                .code(code)
                .discountType(type)
                .discountValue(value)
                .minimumOrderValue(BigDecimal.ZERO)
                .active(true)
                .usedCount(0)
                .build();
    }

    @Test
    void shouldFindAllCouponsWhenActiveFilterIsNull() {
        Coupon active = mutationCoupon("ACTIVE", true);
        Coupon inactive = mutationCoupon("INACTIVE", false);
        when(couponRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(active, inactive));

        var response = couponService.findAll(null);

        assertThat(response).hasSize(2);
        assertThat(response).extracting("code").containsExactly("ACTIVE", "INACTIVE");
    }

    @Test
    void shouldFindCouponsByActiveFilter() {
        Coupon active = mutationCoupon("ACTIVE", true);
        when(couponRepository.findByActiveOrderByCreatedAtDesc(true)).thenReturn(List.of(active));

        var response = couponService.findAll(true);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().code()).isEqualTo("ACTIVE");
        assertThat(response.getFirst().currentlyValid()).isTrue();
    }

    @Test
    void shouldFindCouponById() {
        Coupon coupon = mutationCoupon("POINT10", true);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

        var response = couponService.findById(1L);

        assertThat(response.code()).isEqualTo("POINT10");
    }

    @Test
    void shouldRejectFindByIdWhenCouponDoesNotExist() {
        when(couponRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.findById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cupom não encontrado");
    }

    @Test
    void shouldUpdateCouponWithNormalizedData() {
        Coupon coupon = mutationCoupon("OLD", true);
        CreateCouponRequest request = mutationRequest(
                " new10 ",
                " Nova descrição ",
                DiscountType.FIXED_AMOUNT,
                new BigDecimal("25.00"),
                new BigDecimal("100.00"),
                true,
                null,
                null,
                10
        );

        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(couponRepository.existsByCodeAndIdNot("NEW10", 1L)).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = couponService.update(1L, request);

        assertThat(response.code()).isEqualTo("NEW10");
        assertThat(response.description()).isEqualTo("Nova descrição");
        assertThat(response.discountType()).isEqualTo(DiscountType.FIXED_AMOUNT);
        assertThat(response.discountValue()).isEqualByComparingTo("25.00");
        assertThat(response.minimumOrderValue()).isEqualByComparingTo("100.00");
        assertThat(response.usageLimit()).isEqualTo(10);
    }

    @Test
    void shouldRejectUpdateWhenCodeBelongsToAnotherCoupon() {
        Coupon coupon = mutationCoupon("OLD", true);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(couponRepository.existsByCodeAndIdNot("POINT10", 1L)).thenReturn(true);

        assertThatThrownBy(() -> couponService.update(1L, mutationRequest("POINT10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Já existe um cupom com este código");

        verify(couponRepository, never()).save(any());
    }

    @Test
    void shouldActivateAndDeactivateCoupon() {
        Coupon coupon = mutationCoupon("POINT10", false);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(couponService.activate(1L).active()).isTrue();
        assertThat(couponService.deactivate(1L).active()).isFalse();
    }

    @Test
    void shouldFindValidCouponForOrderUsingNormalizedCode() {
        Coupon coupon = mutationCoupon("POINT10", true);
        coupon.setMinimumOrderValue(new BigDecimal("100.00"));
        when(couponRepository.findByCodeAndActiveTrue("POINT10")).thenReturn(Optional.of(coupon));

        Coupon result = couponService.findValidCouponForOrder(" point10 ", new BigDecimal("150.00"));

        assertThat(result).isSameAs(coupon);
    }

    @Test
    void shouldRejectInactiveCouponWhenValidatingForOrder() {
        Coupon coupon = mutationCoupon("POINT10", false);

        assertThatThrownBy(() -> couponService.validateCouponForOrder(coupon, new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cupom inativo");
    }

    @Test
    void shouldRejectCouponBeforeStartDate() {
        Coupon coupon = mutationCoupon("FUTURE", true);
        coupon.setStartsAt(LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> couponService.validateCouponForOrder(coupon, new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cupom ainda não está válido");
    }

    @Test
    void shouldRejectCouponWhenUsageLimitWasReached() {
        Coupon coupon = mutationCoupon("LIMIT", true);
        coupon.setUsageLimit(3);
        coupon.setUsedCount(3);

        assertThatThrownBy(() -> couponService.validateCouponForOrder(coupon, new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cupom atingiu o limite de uso");
    }

    @Test
    void shouldRejectInvalidCouponCreationData() {
        assertThatThrownBy(() -> couponService.create(mutationRequest("POINT10", DiscountType.PERCENTAGE, BigDecimal.ZERO, null, true, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valor do desconto deve ser maior que zero");

        assertThatThrownBy(() -> couponService.create(mutationRequest("POINT10", DiscountType.FIXED_AMOUNT, new BigDecimal("10.00"), null, true, null, null, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Limite de uso deve ser maior que zero");

        assertThatThrownBy(() -> couponService.create(mutationRequest("POINT10", DiscountType.FIXED_AMOUNT, new BigDecimal("10.00"), null, true, LocalDateTime.now(), LocalDateTime.now().minusDays(1), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Data final do cupom não pode ser anterior à data inicial");
    }

    @Test
    void shouldReturnZeroDiscountWhenItemsAmountIsNull() {
        Coupon coupon = mutationCoupon("POINT10", true);

        BigDecimal discount = couponService.calculateDiscount(coupon, null);

        assertThat(discount).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldIgnoreNullCouponUseRegistration() {
        couponService.registerCouponUse(null);

        verify(couponRepository, never()).save(any());
    }


    @Test
    void shouldRejectBlankCouponCode() {
        assertThatThrownBy(() -> couponService.create(mutationRequest("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código do cupom é obrigatório");

        verify(couponRepository, never()).save(any());
    }

    @Test
    void shouldRejectNullDiscountType() {
        assertThatThrownBy(() -> couponService.create(mutationRequest("POINT10", null, new BigDecimal("10.00"), BigDecimal.ZERO, true, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tipo de desconto é obrigatório");

        verify(couponRepository, never()).save(any());
    }

    @Test
    void shouldRejectUpdateWhenCouponDoesNotExist() {
        when(couponRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.update(99L, mutationRequest("POINT10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cupom não encontrado");

        verify(couponRepository, never()).existsByCodeAndIdNot(any(), any());
        verify(couponRepository, never()).save(any());
    }

    @Test
    void shouldRejectFindValidCouponWhenCodeIsNotFound() {
        when(couponRepository.findByCodeAndActiveTrue("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.findValidCouponForOrder(" missing ", new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cupom inválido ou inativo");
    }

    @Test
    void shouldRejectNullCouponValidation() {
        assertThatThrownBy(() -> couponService.validateCouponForOrder(null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cupom inválido");
    }

    @Test
    void shouldCalculateFixedDiscountWhenItIsLowerThanItemsAmount() {
        Coupon coupon = mutationCoupon("FIXED25", true);
        coupon.setDiscountType(DiscountType.FIXED_AMOUNT);
        coupon.setDiscountValue(new BigDecimal("25.00"));

        BigDecimal discount = couponService.calculateDiscount(coupon, new BigDecimal("100.00"));

        assertThat(discount).isEqualByComparingTo("25.00");
    }

    @Test
    void shouldRegisterCouponUseWhenUsedCountIsNull() {
        Coupon coupon = mutationCoupon("POINT10", true);
        coupon.setUsedCount(null);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        couponService.registerCouponUse(coupon);

        assertThat(coupon.getUsedCount()).isEqualTo(1);
        verify(couponRepository).save(coupon);
    }

    @Test
    void shouldRejectRegisterCouponUseWhenCouponDoesNotExist() {
        Coupon coupon = mutationCoupon("POINT10", true);
        when(couponRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.registerCouponUse(coupon))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cupom não encontrado");

        verify(couponRepository, never()).save(any());
    }

    @Test
    void shouldReportInactiveCouponAsNotCurrentlyValidInResponse() {
        Coupon coupon = mutationCoupon("INACTIVE", false);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

        CouponResponse response = couponService.findById(1L);

        assertThat(response.currentlyValid()).isFalse();
    }

    private CreateCouponRequest mutationRequest(String code) {
        return mutationRequest(code, DiscountType.PERCENTAGE, new BigDecimal("10.00"), BigDecimal.ZERO, true, null, null, null);
    }

    private CreateCouponRequest mutationRequest(String code, DiscountType discountType, BigDecimal discountValue, BigDecimal minimumOrderValue, Boolean active, LocalDateTime startsAt, LocalDateTime endsAt, Integer usageLimit) {
        return mutationRequest(code, "Descrição", discountType, discountValue, minimumOrderValue, active, startsAt, endsAt, usageLimit);
    }

    private CreateCouponRequest mutationRequest(String code, String description, DiscountType discountType, BigDecimal discountValue, BigDecimal minimumOrderValue, Boolean active, LocalDateTime startsAt, LocalDateTime endsAt, Integer usageLimit) {
        return new CreateCouponRequest(
                code,
                description,
                discountType,
                discountValue,
                minimumOrderValue,
                active,
                startsAt,
                endsAt,
                usageLimit
        );
    }

    private Coupon mutationCoupon(String code, Boolean active) {
        return Coupon.builder()
                .id(1L)
                .code(code)
                .description("Descrição")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10.00"))
                .minimumOrderValue(BigDecimal.ZERO)
                .active(active)
                .usedCount(0)
                .build();
    }
}
