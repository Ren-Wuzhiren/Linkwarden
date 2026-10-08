package com.webcollector.auth.session;

public record SessionToken (
        String token,
        String tokenName,
        long timeoutSeconds
) {
}