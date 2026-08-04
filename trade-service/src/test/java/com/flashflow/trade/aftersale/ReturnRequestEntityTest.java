package com.flashflow.trade.aftersale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.flashflow.trade.common.BusinessException;
import org.junit.jupiter.api.Test;

class ReturnRequestEntityTest {

    @Test
    void recordsApprovalAndPreventsSecondDecision() {
        ReturnRequestEntity request = new ReturnRequestEntity(
                "RT-1", 1L, "FF-1", 9L, "商品存在质量问题");

        request.approve("同意退款", Instant.now());

        assertThat(request.getStatus()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(request.getAdminNote()).isEqualTo("同意退款");
        assertThat(request.getProcessedAt()).isNotNull();
        assertThatThrownBy(() -> request.reject("重复操作", Instant.now()))
                .isInstanceOf(BusinessException.class);
    }
}
