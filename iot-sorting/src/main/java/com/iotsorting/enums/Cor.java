package com.iotsorting.enums;

/**
 * Cores que o ESP32-CAM consegue identificar na imagem capturada.
 *
 * <p>Quando o firmware nao consegue classificar a cor com seguranca, deve enviar
 * {@link #INDEFINIDO}.</p>
 */
public enum Cor {

    VERMELHO,
    VERDE,
    AZUL,
    AMARELO,
    BRANCO,
    PRETO,
    INDEFINIDO

}
