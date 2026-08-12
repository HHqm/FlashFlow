package com.flashflow.trade.review;

import java.util.List;

import com.flashflow.trade.auth.UserRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.order.OrderEntity;
import com.flashflow.trade.order.OrderRepository;
import com.flashflow.trade.order.OrderStatus;
import com.flashflow.trade.product.ProductEntity;
import com.flashflow.trade.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            OrderRepository orderRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ReviewDtos.ReviewView create(
            long userId,
            String orderNo,
            ReviewDtos.CreateReviewRequest request
    ) {
        OrderEntity order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getUserId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (order.getStatus() != OrderStatus.RECEIVED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "只有确认收货后才能评价");
        }
        if (reviewRepository.existsByOrderId(order.getId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        ReviewEntity review = reviewRepository.save(new ReviewEntity(
                order.getId(),
                order.getOrderNo(),
                userId,
                order.getProductId(),
                order.getVariantId(),
                request.rating(),
                request.content().trim()));
        return toView(review, order, false);
    }

    @Transactional(readOnly = true)
    public List<ReviewDtos.ReviewView> listPublic(long productId) {
        productRepository.findById(productId)
                .filter(ProductEntity::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return reviewRepository.findTop100ByProductIdAndVisibleTrueOrderByCreatedAtDesc(productId)
                .stream()
                .map(review -> toView(review, orderById(review.getOrderId()), false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewDtos.ReviewView> listMine(long userId) {
        return reviewRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(review -> toView(review, orderById(review.getOrderId()), false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewDtos.ReviewView> listAdmin() {
        return reviewRepository.findTop200ByOrderByCreatedAtDesc().stream()
                .map(review -> toView(review, orderById(review.getOrderId()), true))
                .toList();
    }

    @Transactional
    public ReviewDtos.ReviewView changeVisibility(
            long reviewId,
            ReviewDtos.ChangeVisibilityRequest request
    ) {
        ReviewEntity review = reviewById(reviewId);
        review.changeVisibility(request.visible());
        return toView(review, orderById(review.getOrderId()), true);
    }

    @Transactional
    public ReviewDtos.ReviewView reply(
            long reviewId,
            ReviewDtos.ReplyReviewRequest request
    ) {
        ReviewEntity review = reviewById(reviewId);
        review.reply(request.reply().trim());
        return toView(review, orderById(review.getOrderId()), true);
    }

    private ReviewEntity reviewById(long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }

    private OrderEntity orderById(long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private ReviewDtos.ReviewView toView(
            ReviewEntity review,
            OrderEntity order,
            boolean admin
    ) {
        String username = userRepository.findById(review.getUserId())
                .map(user -> user.getUsername())
                .orElse("用户" + review.getUserId());
        String reviewerName = admin ? username : mask(username);
        return new ReviewDtos.ReviewView(
                review.getId(),
                review.getOrderNo(),
                review.getUserId(),
                reviewerName,
                review.getProductId(),
                order.getProductName(),
                order.getVariantLabel(),
                review.getRating(),
                review.getContent(),
                review.isVisible(),
                review.getMerchantReply(),
                review.getRepliedAt(),
                review.getCreatedAt());
    }

    private static String mask(String username) {
        if (username == null || username.isBlank()) {
            return "匿名用户";
        }
        return username.substring(0, 1) + "***";
    }
}
