/**
 * IoT Sorting - Simulador do celular (configuracao)
 * ------------------------------------------------------------------------
 * Este e o arquivo que deve ser alterado para apontar o simulador para outro
 * ESP32 ou para outro backend. Nenhum valor aqui e "dado simulado": sao apenas
 * enderecos e parametros de operacao.
 *
 * O arquivo e carregado antes de `simulador.js` e publica a configuracao em
 * `window.IOT_SORTING_SIMULADOR`.
 *
 * Os valores tambem podem ser sobrescritos sem editar o arquivo:
 *
 *   - pela barra de enderecos:
 *     /simulador.html?esp32=http://192.168.1.6&backend=https://iot-sorting.onrender.com&modo=BACKEND
 *
 *   - pelo painel "Configuracoes" da propria tela (fica salvo no navegador).
 */
window.IOT_SORTING_SIMULADOR = {

    // -------------------------------------------------------------------
    // ESP32 (rede local) - mesmo IP mostrado no Monitor Serial do firmware
    // -------------------------------------------------------------------
    ESP32_URL: 'http://192.168.1.6',

    // -------------------------------------------------------------------
    // Backend Java/Spring Boot consumido pelo simulador
    // -------------------------------------------------------------------
    // Vazio ('') = mesma origem da pagina. E o recomendado quando o simulador
    // e aberto pelo proprio backend: http://localhost:8080/simulador.html
    // ou http://<ip-do-notebook>:8080/simulador.html
    //
    // Para abrir a pagina localmente e consumir o backend publicado no Render:
    //   BACKEND_URL: 'https://iot-sorting.onrender.com'
    // (nesse caso o Render precisa liberar a origem da pagina em
    //  iot.sorting.cors.origens-permitidas - veja o README)
    BACKEND_URL: '',

    // -------------------------------------------------------------------
    // Dispositivo da demonstracao ("Esteira Principal", id 1)
    // -------------------------------------------------------------------
    DISPOSITIVO_ID: 1,

    // Confianca enviada em MODO BACKEND (em MODO ESP32 quem define o valor e o firmware).
    CONFIANCA: 90,

    // Modo inicial: 'ESP32' (padrao) ou 'BACKEND'
    MODO_PADRAO: 'ESP32',

    // -------------------------------------------------------------------
    // Tempos (milissegundos)
    // -------------------------------------------------------------------
    // Intervalo do polling de GET /status no ESP32.
    INTERVALO_STATUS_ESP32_MS: 1000,

    // Intervalo do polling de GET /api/dashboard + /api/objetos/recentes.
    INTERVALO_BACKEND_MS: 5000,

    // Timeout das requisicoes simples ao ESP32 (/status e /cor).
    TIMEOUT_ESP32_MS: 6000,

    // Timeout de GET /processar: o ESP32 para a esteira, aguarda ~2s, envia o
    // objeto ao backend e religa a esteira. Se o backend estiver "dormindo"
    // (Render gratuito), o firmware pode demorar para responder.
    TIMEOUT_PROCESSAR_MS: 60000,

    // Timeout das leituras do backend (GET /api/dashboard, GET /api/objetos/recentes).
    TIMEOUT_BACKEND_GET_MS: 12000,

    // Timeout do POST /api/objetos feito pelo simulador em MODO BACKEND.
    TIMEOUT_BACKEND_POST_MS: 60000,

    // Tempo maximo aguardando a confirmacao de que o backend registrou o objeto
    // depois de GET /processar (a confirmacao e feita lendo GET /api/dashboard).
    ESPERA_CONFIRMACAO_MS: 30000
};
