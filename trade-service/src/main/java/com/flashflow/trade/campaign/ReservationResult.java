package com.flashflow.trade.campaign;

public record ReservationResult(Status status, String orderNo) {

    public enum Status {
        RESERVED,
        DUPLICATE,
        SOLD_OUT,
        ALREADY_PURCHASED,
        LIMIT_EXCEEDED
    }

    public static ReservationResult reserved(String orderNo) {
        return new ReservationResult(Status.RESERVED, orderNo);
    }

    public static ReservationResult duplicate(String orderNo) {
        return new ReservationResult(Status.DUPLICATE, orderNo);
    }
}
