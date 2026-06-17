package com.minidrive.auth;

import com.minidrive.common.ApiException;
import com.minidrive.common.ErrorCode;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the authenticated user id (the JWT subject) from the security context.
 */
public final class CurrentUser {
    private CurrentUser() {
    }

    public static Long id() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required");
        }
        return userId;
    }
}
