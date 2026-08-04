package com.flashflow.trade.review;

import java.util.List;

import com.flashflow.trade.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/products/{productId}/reviews")
    ApiResponse<List<ReviewDtos.ReviewView>> listPublic(@PathVariable long productId) {
        return ApiResponse.success(reviewService.listPublic(productId));
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    ApiResponse<List<ReviewDtos.ReviewView>> listMine(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(reviewService.listMine(userId(jwt)));
    }

    @PostMapping("/orders/{orderNo}/reviews")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    ApiResponse<ReviewDtos.ReviewView> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String orderNo,
            @Valid @RequestBody ReviewDtos.CreateReviewRequest request
    ) {
        return ApiResponse.success(reviewService.create(userId(jwt), orderNo, request));
    }

    @GetMapping("/admin/reviews")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<List<ReviewDtos.ReviewView>> listAdmin() {
        return ApiResponse.success(reviewService.listAdmin());
    }

    @PatchMapping("/admin/reviews/{reviewId}/visibility")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<ReviewDtos.ReviewView> changeVisibility(
            @PathVariable long reviewId,
            @Valid @RequestBody ReviewDtos.ChangeVisibilityRequest request
    ) {
        return ApiResponse.success(reviewService.changeVisibility(reviewId, request));
    }

    @PatchMapping("/admin/reviews/{reviewId}/reply")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<ReviewDtos.ReviewView> reply(
            @PathVariable long reviewId,
            @Valid @RequestBody ReviewDtos.ReplyReviewRequest request
    ) {
        return ApiResponse.success(reviewService.reply(reviewId, request));
    }

    private static long userId(Jwt jwt) {
        Number userId = jwt.getClaim("uid");
        return userId.longValue();
    }
}
