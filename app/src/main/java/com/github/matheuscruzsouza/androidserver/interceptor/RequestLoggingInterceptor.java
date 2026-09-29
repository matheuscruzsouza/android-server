package com.github.matheuscruzsouza.androidserver.interceptor;

import com.github.matheuscruzsouza.androidserver.log.ServerLogBus;
import com.github.matheuscruzsouza.nanospring.annotation.Interceptor;
import com.github.matheuscruzsouza.nanospring.annotation.Order;
import com.github.matheuscruzsouza.nanospring.handler.HandlerInterceptor;

import fi.iki.elonen.NanoHTTPD;

@Interceptor
@Order(1)
public class RequestLoggingInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(NanoHTTPD.IHTTPSession session, String path) {
        ServerLogBus.recordRequest();
        String method = session.getMethod() != null ? session.getMethod().name() : "HTTP";
        ServerLogBus.log(method + " " + path);
        return true;
    }
}
