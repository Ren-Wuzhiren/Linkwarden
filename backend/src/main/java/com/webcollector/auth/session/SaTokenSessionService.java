package com.webcollector.auth.session;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;

@Service
public class SaTokenSessionService implements SessionTokenService {

    @Override
    public SessionToken login(Long userId) {
        StpUtil.login(userId);

        return new SessionToken(
                StpUtil.getTokenValue(),
                StpUtil.getTokenName(),
                StpUtil.getTokenTimeout()
        );
    }

    @Override
    public Long requireLoginId() {
        StpUtil.checkLogin();
        return StpUtil.getLoginIdAsLong();
    }

    @Override
    public void logout() {
        // [业务] 重复退出应保持幂等，没有有效会话时不抛 500。
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
    }
}
