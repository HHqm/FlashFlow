package com.flashflow.trade.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数不合法"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权执行此操作"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "商品分类不存在"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "商品不存在"),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "商品规格不存在"),
    CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "活动不存在"),
    CAMPAIGN_NOT_STARTED(HttpStatus.CONFLICT, "CAMPAIGN_NOT_STARTED", "活动尚未开始"),
    CAMPAIGN_ENDED(HttpStatus.CONFLICT, "CAMPAIGN_ENDED", "活动已结束"),
    CAMPAIGN_UNAVAILABLE(HttpStatus.CONFLICT, "CAMPAIGN_UNAVAILABLE", "活动当前不可参与"),
    SOLD_OUT(HttpStatus.CONFLICT, "SOLD_OUT", "库存已售罄"),
    ALREADY_PURCHASED(HttpStatus.CONFLICT, "ALREADY_PURCHASED", "每位用户限购一件"),
    PURCHASE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "PURCHASE_LIMIT_EXCEEDED", "购买数量超过活动限购"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "请求过于频繁"),
    REQUEST_PROCESSING(HttpStatus.CONFLICT, "REQUEST_PROCESSING", "相同请求正在处理中"),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "收货地址不存在"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在"),
    ORDER_STATUS_INVALID(HttpStatus.CONFLICT, "ORDER_STATUS_INVALID", "当前订单状态不允许此操作"),
    RETURN_NOT_FOUND(HttpStatus.NOT_FOUND, "RETURN_NOT_FOUND", "退款申请不存在"),
    RETURN_ALREADY_EXISTS(HttpStatus.CONFLICT, "RETURN_ALREADY_EXISTS", "该订单已经提交过退款申请"),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "评价不存在"),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS", "该订单已经评价过"),
    INFRASTRUCTURE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "INFRASTRUCTURE_UNAVAILABLE", "服务暂时不可用，请稍后重试"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "系统内部错误");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String code, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
