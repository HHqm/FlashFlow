package com.flashflow.trade.common;

import org.slf4j.MDC;

public final class TraceContext {

    public static final String TRACE_ID = "traceId";

    private TraceContext() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID);
        return traceId == null ? "" : traceId;
    }
}
