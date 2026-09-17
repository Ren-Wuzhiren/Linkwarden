package com.webcollector.common.web;

/**
 * 保存当前请求的 requestId。
 *
 * <p>由 {@link RequestIdFilter} 在请求开始时写入，并在请求结束时清理。</p>
 */
public final class RequestIdContext {

    // 同一请求的处理线程通过 ThreadLocal 共享请求编号。
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    // 工具类不允许实例化。
    private RequestIdContext() {

    }

    public static void set(String requestId) {
        CURRENT.set(requestId);
    }

    public static String get() {
        return CURRENT.get();
    }

    // 必须使用 remove()，避免线程池复用线程时残留上一次请求的数据。
    public static void clear() {
        CURRENT.remove();
    }
}
