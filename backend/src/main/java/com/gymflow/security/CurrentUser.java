package com.gymflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acceso al id del usuario autenticado (claim "sub" del JWT). */
public final class CurrentUser {

    private CurrentUser() {}

    public static long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }
}
