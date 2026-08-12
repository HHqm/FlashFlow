package com.flashflow.common.event;

public final class EventTypes {

    public static final String ORDER_CREATED = "order.created.v1";
    public static final String ORDER_PAID = "order.paid.v1";
    public static final String ORDER_SHIPPED = "order.shipped.v1";
    public static final String ORDER_RECEIVED = "order.received.v1";
    public static final String ORDER_CANCELLED = "order.cancelled.v1";
    public static final String ORDER_REFUND_REQUESTED = "order.refund-requested.v1";
    public static final String ORDER_REFUNDED = "order.refunded.v1";
    public static final String ORDER_REFUND_REJECTED = "order.refund-rejected.v1";

    private EventTypes() {
    }
}
