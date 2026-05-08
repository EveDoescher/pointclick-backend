package com.pim.ecommerce.domain.repository;

import com.pim.ecommerce.domain.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {

    List<ReviewImage> findByReviewIdOrderByDisplayOrderAsc(Long reviewId);
}