package com.iotsorting.exception;

/**
 * Lancada quando a requisicao conflita com dados ja cadastrados (HTTP 409),
 * por exemplo ao cadastrar dois dispositivos com o mesmo codigo.
 */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }

}
