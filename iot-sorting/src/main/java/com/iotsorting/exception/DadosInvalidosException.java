package com.iotsorting.exception;

/**
 * Lancada quando os dados informados violam uma regra de negocio (HTTP 400).
 */
public class DadosInvalidosException extends RuntimeException {

    public DadosInvalidosException(String mensagem) {
        super(mensagem);
    }

}
