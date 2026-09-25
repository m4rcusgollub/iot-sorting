package com.iotsorting.enums;

/**
 * Situacao de comunicacao de um dispositivo ESP32-CAM.
 *
 * <p>O status e controlado pelo backend: um dispositivo passa para {@link #ONLINE}
 * quando envia uma requisicao (deteccao ou heartbeat) e volta para {@link #OFFLINE}
 * quando fica mais de 60 segundos sem se comunicar.</p>
 */
public enum StatusDispositivo {

    ONLINE,
    OFFLINE

}
