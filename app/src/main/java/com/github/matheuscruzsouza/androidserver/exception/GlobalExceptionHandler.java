package com.github.matheuscruzsouza.androidserver.exception;

import com.github.matheuscruzsouza.nanospring.annotation.ControllerAdvice;
import com.github.matheuscruzsouza.nanospring.annotation.ExceptionHandler;
import com.github.matheuscruzsouza.nanospring.http.HttpStatus;
import com.github.matheuscruzsouza.nanospring.http.ResponseEntity;
import com.github.matheuscruzsouza.androidserver.model.ErrorResponse;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ErrorResponse> handleNumberFormatException(NumberFormatException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("ERRO_FORMATO", "Você enviou um ID inválido que não é um número: " + ex.getMessage()));
    }

    @ExceptionHandler(IndexOutOfBoundsException.class)
    public ResponseEntity<ErrorResponse> handleIndexOutOfBoundsException(IndexOutOfBoundsException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NAO_ENCONTRADO", "O usuário com esse ID não existe na lista."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("DADOS_INVALIDOS", ex.getMessage()));
    }
}
