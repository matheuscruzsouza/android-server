package com.github.matheuscruzsouza.androidserver.controller;

import com.github.matheuscruzsouza.nanospring.annotation.GetMethod;
import com.github.matheuscruzsouza.nanospring.annotation.RestController;
import com.github.matheuscruzsouza.nanospring.annotation.Value;
import com.github.matheuscruzsouza.nanospring.openapi.annotation.Hidden;
import com.github.matheuscruzsouza.androidserver.model.TestObj;

@Hidden
@RestController("/health")
public class HealthController {

    @Value("${app.welcome.message:Mensagem padrao se falhar}")
    private String welcomeMessage;

    @GetMethod("")
    public TestObj getStatus() {
        return new TestObj("UP - " + welcomeMessage);
    }

    @GetMethod("/full")
    public TestObj getFullStatus() {
        return new TestObj("FULL");
    }
}
