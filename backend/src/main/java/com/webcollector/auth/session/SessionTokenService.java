package com.webcollector.auth.session;

public interface SessionTokenService {

    SessionToken login(Long userId);

    Long requireLoginId();

    void logout();
}
