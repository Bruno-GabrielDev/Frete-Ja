package br.edu.ifsp.freteja.nucleo.web;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> pedidoInvalido(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("erro", "Pedido inválido: " + e.getMessage()));
    }
}
