package com.flashflow.trade.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashflow.trade.order.OrderCommandService;
import com.flashflow.trade.order.OrderController;
import com.flashflow.trade.order.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderCommandService commandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderController(orderService, commandService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void missingIdempotencyKeyHeaderIsClientError() throws Exception {
        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/campaigns/1/orders"))
                .andReturn();

        assertThat(result.getResolvedException())
                .isInstanceOf(MissingRequestHeaderException.class);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void nonNumericPathVariableIsClientError() throws Exception {
        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/campaigns/abc/orders")
                                .header("Idempotency-Key", "abcdefgh12345678"))
                .andReturn();

        assertThat(result.getResolvedException())
                .isInstanceOf(MethodArgumentTypeMismatchException.class);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void unsupportedHttpMethodIsClientError() throws Exception {
        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders.delete("/api/v1/orders"))
                .andReturn();

        assertThat(result.getResolvedException())
                .isInstanceOf(HttpRequestMethodNotSupportedException.class);
        assertThat(result.getResponse().getStatus()).isEqualTo(405);
    }
}
