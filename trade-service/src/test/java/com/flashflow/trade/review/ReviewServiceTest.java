package com.flashflow.trade.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.flashflow.trade.auth.UserRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.order.OrderEntity;
import com.flashflow.trade.order.OrderRepository;
import com.flashflow.trade.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(
                reviewRepository, orderRepository, productRepository, userRepository);
    }

    @Test
    void paidButNotReceivedOrderCannotBeReviewed() {
        OrderEntity order = order();
        order.pay(Instant.now());
        when(orderRepository.findByOrderNo("FF-REVIEW-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.create(
                7L,
                "FF-REVIEW-1",
                new ReviewDtos.CreateReviewRequest(5, "Great product")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ORDER_STATUS_INVALID));
    }

    @Test
    void ownerCanReviewAfterConfirmingReceipt() {
        OrderEntity order = order();
        setId(order, 99L);
        order.pay(Instant.now());
        order.ship("SF Express", "SF123", Instant.now());
        order.receive(Instant.now());
        when(orderRepository.findByOrderNo("FF-REVIEW-1")).thenReturn(Optional.of(order));
        when(reviewRepository.existsByOrderId(99L)).thenReturn(false);
        when(reviewRepository.save(any(ReviewEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        ReviewDtos.ReviewView review = service.create(
                7L,
                "FF-REVIEW-1",
                new ReviewDtos.CreateReviewRequest(5, "Great product"));

        assertThat(review.rating()).isEqualTo(5);
        assertThat(review.content()).isEqualTo("Great product");
    }

    private static OrderEntity order() {
        return new OrderEntity(
                "FF-REVIEW-1",
                7L,
                10L,
                3L,
                4L,
                new BigDecimal("399.00"),
                1,
                "Test product",
                "Black / Standard",
                "review_request_123",
                "Test User",
                "13800000000",
                "Shanghai Pudong Test Road 1");
    }

    private static void setId(OrderEntity order, long id) {
        try {
            Field field = OrderEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
