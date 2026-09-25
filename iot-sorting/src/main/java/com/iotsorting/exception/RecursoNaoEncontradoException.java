package com.iotsorting.exception;

/**
 * Lancada quando um recurso solicitado nao existe (HTTP 404).
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

    public static RecursoNaoEncontradoException de(String recurso, Object identificador) {
        return new RecursoNaoEncontradoException(recurso + " não encontrado: identificador " + identificador);
    }

}
