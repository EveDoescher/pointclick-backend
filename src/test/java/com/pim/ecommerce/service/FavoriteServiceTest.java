package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Favorite;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.FavoriteRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.dto.response.FavoriteResponse;
import com.pim.ecommerce.dto.response.ProductResponse;
import com.pim.ecommerce.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductService productService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private FavoriteService favoriteService;

    @Test
    void shouldAddFavoriteWhenItDoesNotExist() {
        User user = user(2L);
        Product product = product(1L);
        ProductResponse productResponse = productResponse(product);

        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(user));
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));
        when(favoriteRepository.findByUserIdAndProductId(2L, 1L)).thenReturn(Optional.empty());
        when(favoriteRepository.save(any(Favorite.class))).thenAnswer(invocation -> {
            Favorite favorite = invocation.getArgument(0);
            favorite.setId(10L);
            return favorite;
        });
        when(productService.toResponse(product)).thenReturn(productResponse);

        FavoriteResponse response = favoriteService.addFavorite(1L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.product().id()).isEqualTo(1L);
    }

    @Test
    void shouldReturnExistingFavoriteWithoutDuplicating() {
        User user = user(2L);
        Product product = product(1L);
        Favorite favorite = Favorite.builder().id(10L).user(user).product(product).build();

        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(user));
        when(productRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));
        when(favoriteRepository.findByUserIdAndProductId(2L, 1L)).thenReturn(Optional.of(favorite));
        when(productService.toResponse(product)).thenReturn(productResponse(product));

        FavoriteResponse response = favoriteService.addFavorite(1L);

        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    void shouldRejectFavoriteWhenProductDoesNotExist() {
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(userRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(user(2L)));
        when(productRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.addFavorite(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Produto não encontrado");
    }

    @Test
    void shouldRemoveFavoriteWhenExists() {
        Favorite favorite = Favorite.builder().id(10L).user(user(2L)).product(product(1L)).build();
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(favoriteRepository.findByUserIdAndProductId(2L, 1L)).thenReturn(Optional.of(favorite));

        favoriteService.removeFavorite(1L);

        verify(favoriteRepository).delete(favorite);
    }

    @Test
    void shouldListMyFavorites() {
        User user = user(2L);
        Product product = product(1L);
        Favorite favorite = Favorite.builder().id(10L).user(user).product(product).build();

        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(favoriteRepository.findByUserIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(favorite));
        when(productService.toResponse(product)).thenReturn(productResponse(product));

        List<FavoriteResponse> responses = favoriteService.findMyFavorites();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).product().name()).isEqualTo("Teclado Mecânico");
    }

    @Test
    void shouldCheckFavoriteExistsForCurrentUser() {
        when(currentUserService.getCurrentUserId()).thenReturn(2L);
        when(favoriteRepository.existsByUserIdAndProductId(2L, 1L)).thenReturn(true);

        boolean exists = favoriteService.existsForCurrentUser(1L);

        assertThat(exists).isTrue();
    }

    private User user(Long id) {
        return User.builder()
                .id(id)
                .fullName("Ana Souza")
                .email("ana@email.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build();
    }

    private Product product(Long id) {
        return Product.builder()
                .id(id)
                .name("Teclado Mecânico")
                .description("Descrição")
                .categoryGroup("Periféricos")
                .category("Teclados")
                .brand("PointClick")
                .price(new BigDecimal("100.00"))
                .stockQuantity(10)
                .reservedQuantity(0)
                .active(true)
                .build();
    }

    private ProductResponse productResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategoryGroup(),
                product.getCategory(),
                product.getBrand(),
                product.getModel(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getReservedQuantity(),
                product.getStockQuantity() - product.getReservedQuantity(),
                product.getImageUrl(),
                product.getActive(),
                false,
                true,
                0L,
                0L,
                0.0,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
