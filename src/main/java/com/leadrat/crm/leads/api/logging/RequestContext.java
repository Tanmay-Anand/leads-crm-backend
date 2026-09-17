package com.leadrat.crm.leads.api.logging;

/** Per-request correlation id, set by {@code RequestIdFilter} and echoed on every error. */
public final class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private RequestContext() {
    }

    public static String getRequestId() {
        return REQUEST_ID.get();
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static void clear() {
        REQUEST_ID.remove();
    }
}
