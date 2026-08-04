package com.flashflow.trade.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReviewEntityTest {

    @Test
    void supportsModerationAndMerchantReply() {
        ReviewEntity review = new ReviewEntity(
                1L, "FF-1", 7L, 2L, 3L, 5, "商品体验很好");

        review.changeVisibility(false);
        review.reply("感谢支持，欢迎再次选购");

        assertThat(review.isVisible()).isFalse();
        assertThat(review.getMerchantReply()).contains("感谢支持");
        assertThat(review.getRepliedAt()).isNotNull();
    }
}
