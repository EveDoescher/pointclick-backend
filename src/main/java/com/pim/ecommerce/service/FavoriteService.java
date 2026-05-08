package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Favorite;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.repository.FavoriteRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.dto.response.FavoriteResponse;
import com.pim.ecommerce.dto.response.ProductResponse;
import com.pim.ecommerce.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final CurrentUserService currentUserService;

    @Transactional
    public FavoriteResponse addFavorite(Long productId) {
        Long currentUserId = currentUserService.getCurrentUserId();

        User user = userRepository.findByIdAndActiveTrue(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        Product product = productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        return favoriteRepository.findByUserIdAndProductId(currentUserId, productId)
                .map(this::toResponse)
                .orElseGet(() -> {
                    Favorite favorite = Favorite.builder()
                            .user(user)
                            .product(product)
                            .build();

                    return toResponse(favoriteRepository.save(favorite));
                });
    }

    @Transactional
    public void removeFavorite(Long productId) {
        Long currentUserId = currentUserService.getCurrentUserId();

        favoriteRepository.findByUserIdAndProductId(currentUserId, productId)
                .ifPresent(favoriteRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponse> findMyFavorites() {
        Long currentUserId = currentUserService.getCurrentUserId();

        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean existsForCurrentUser(Long productId) {
        Long currentUserId = currentUserService.getCurrentUserId();

        return favoriteRepository.existsByUserIdAndProductId(currentUserId, productId);
    }

    private FavoriteResponse toResponse(Favorite favorite) {
        ProductResponse productResponse = productService.toResponse(favorite.getProduct());

        return new FavoriteResponse(
                favorite.getId(),
                favorite.getUser().getId(),
                productResponse,
                favorite.getCreatedAt()
        );
    }
}