package com.github.matheuscruzsouza.androidserver.interceptor;

import com.github.matheuscruzsouza.nanospring.annotation.Interceptor;
import com.github.matheuscruzsouza.nanospring.annotation.Order;
import com.github.matheuscruzsouza.nanospring.handler.HandlerInterceptor;
import fi.iki.elonen.NanoHTTPD;

@Interceptor
@Order(1)
public class SecurityInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(NanoHTTPD.IHTTPSession session, String path) {
        // Bloqueia qualquer acesso para rotas /admin que não tenham token válido
        if (path.startsWith("/admin/")) {
            String token = session.getHeaders().get("authorization");
            if (token == null || !token.equals("Bearer NANO-SPRING-SUPER-SECRET")) {
                return false;
            }
        }
        return true;
    }
}
