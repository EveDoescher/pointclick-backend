package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.Product;
import com.pim.ecommerce.domain.entity.Review;
import com.pim.ecommerce.domain.entity.ReviewImage;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.OrderStatus;
import com.pim.ecommerce.domain.repository.OrderRepository;
import com.pim.ecommerce.domain.repository.ProductRepository;
import com.pim.ecommerce.domain.repository.ReviewImageRepository;
import com.pim.ecommerce.domain.repository.ReviewRepository;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.dto.response.ReviewImageResponse;
import com.pim.ecommerce.dto.response.ReviewResponse;
import com.pim.ecommerce.dto.response.ReviewSummaryResponse;
import com.pim.ecommerce.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_REVIEW_IMAGES = 5;

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;

    @Transactional
    public ReviewResponse create(
            Long orderId,
            Long productId,
            Integer rating,
            String comment,
            List<MultipartFile> images
    ) {
        validateRating(rating);
        validateImages(images);

        Long currentUserId = currentUserService.getCurrentUserId();

        User user = userRepository.findByIdAndActiveTrue(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado"));

        if (!order.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Você não tem permissão para avaliar este pedido");
        }

        if (order.getStatus() != OrderStatus.FINISHED) {
            throw new IllegalArgumentException("Só é possível avaliar produtos de compras finalizadas");
        }

        boolean productBelongsToOrder = order.getItems()
                .stream()
                .anyMatch(item -> item.getProduct().getId().equals(productId));

        if (!productBelongsToOrder) {
            throw new IllegalArgumentException("Este produto não pertence ao pedido informado");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        if (reviewRepository.existsByUserIdAndOrderIdAndProductIdAndActiveTrue(
                currentUserId,
                orderId,
                productId
        )) {
            throw new IllegalArgumentException("Você já avaliou este produto neste pedido");
        }

        String normalizedComment = comment == null ? "" : comment.trim();

        Review review = Review.builder()
                .user(user)
                .order(order)
                .product(product)
                .rating(rating)
                .comment(normalizedComment)
                .active(true)
                .build();

        Review savedReview = reviewRepository.save(review);

        saveReviewImages(savedReview, images);

        Review reloadedReview = reviewRepository.findByIdAndActiveTrue(savedReview.getId())
                .orElse(savedReview);

        return toResponse(reloadedReview);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findByProductId(
            Long productId,
            Integer rating,
            Boolean withComment,
            Boolean withMedia
    ) {
        productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        return reviewRepository.searchByProduct(
                        productId,
                        rating,
                        Boolean.TRUE.equals(withComment),
                        Boolean.TRUE.equals(withMedia)
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummaryByProductId(Long productId) {
        productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        Double averageRating = reviewRepository.averageRatingByProductId(productId);

        return new ReviewSummaryResponse(
                averageRating,
                reviewRepository.countByProductIdAndActiveTrue(productId),
                reviewRepository.countByProductIdAndRating(productId, 5),
                reviewRepository.countByProductIdAndRating(productId, 4),
                reviewRepository.countByProductIdAndRating(productId, 3),
                reviewRepository.countByProductIdAndRating(productId, 2),
                reviewRepository.countByProductIdAndRating(productId, 1),
                reviewRepository.countWithCommentsByProductId(productId),
                reviewRepository.countWithMediaByProductId(productId)
        );
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> findMyReviews() {
        Long currentUserId = currentUserService.getCurrentUserId();

        return reviewRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long reviewId) {
        Review review = reviewRepository.findByIdAndActiveTrue(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Avaliação não encontrada"));

        if (!currentUserService.isAdmin()
                && !review.getUser().getId().equals(currentUserService.getCurrentUserId())) {
            throw new AccessDeniedException("Você não tem permissão para remover esta avaliação");
        }

        review.setActive(false);
        reviewRepository.save(review);
    }

    private void validateRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Nota deve estar entre 1 e 5");
        }
    }

    private void validateImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return;
        }

        if (images.size() > MAX_REVIEW_IMAGES) {
            throw new IllegalArgumentException("É permitido anexar no máximo 5 imagens");
        }
    }

    private void saveReviewImages(Review review, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return;
        }

        int displayOrder = 0;

        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                continue;
            }

            String imageUrl = fileStorageService.storeReviewImage(image);

            ReviewImage reviewImage = ReviewImage.builder()
                    .review(review)
                    .imageUrl(imageUrl)
                    .displayOrder(displayOrder)
                    .build();

            reviewImageRepository.save(reviewImage);
            displayOrder++;
        }
    }

    private ReviewResponse toResponse(Review review) {
        List<ReviewImageResponse> images = review.getImages()
                .stream()
                .sorted(Comparator.comparing(ReviewImage::getDisplayOrder))
                .map(this::toImageResponse)
                .toList();

        return new ReviewResponse(
                review.getId(),
                review.getOrder().getId(),
                review.getProduct().getId(),
                review.getProduct().getName(),
                review.getUser().getId(),
                review.getUser().getFullName(),
                review.getUser().getAvatarUrl(),
                review.getRating(),
                review.getComment(),
                images.size(),
                images,
                review.getActive(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }

    private ReviewImageResponse toImageResponse(ReviewImage image) {
        return new ReviewImageResponse(
                image.getId(),
                image.getImageUrl(),
                image.getDisplayOrder()
        );
    }
}