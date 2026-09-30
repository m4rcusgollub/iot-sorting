/**
 * IoT Sorting - Simulador do celular (Controle da Esteira)
 * ------------------------------------------------------------------------
 * Este arquivo executa SOMENTE requisicoes HTTP reais. Nao existe mock, nem
 * resposta inventada: quando o ESP32 ou o backend nao respondem, a tela mostra
 * a falha (codigo HTTP, tempo esgotado ou falha de rede) e o log registra a
 * tentativa.
 *
 * MODO ESP32 (padrao):
 *   GET  {ESP32}/cor?valor=VERMELHO
 *   GET  {ESP32}/processar          -> o ESP32 para a esteira, aguarda ~2s,
 *                                      envia POST /api/objetos e religa a esteira
 *   GET  {ESP32}/status             -> polling ~1s: { cor, esteira, processando }
 *   GET  {BACKEND}/api/dashboard    -> confirmacao do registro + contadores
 *
 * MODO BACKEND (teste sem ESP32):
 *   POST {BACKEND}/api/objetos      -> { dispositivoId, cor, confianca }
 *   GET  {BACKEND}/api/dashboard
 *
 * Os enderecos ficam em `simulador.config.js` e podem ser sobrescritos pelo
 * painel "Configuracoes" da tela ou pela barra de enderecos.
 */
(function () {
    'use strict';

    // ====================================================================
    // 1. Configuracao
    // ====================================================================

    var PADRAO = window.IOT_SORTING_SIMULADOR || {};
    var CHAVE_ARMAZENAMENTO = 'iotSorting.simulador';
    var CAMPOS_CONTADORES = ['objetosHoje', 'vermelhos', 'verdes', 'azuis', 'amarelos', 'brancos', 'pretos', 'indefinidos'];
    var CLASSES_COR = {
        VERMELHO: 'vermelho',
        VERDE: 'verde',
        AZUL: 'azul',
        AMARELO: 'amarelo',
        BRANCO: 'branco',
        PRETO: 'preto',
        INDEFINIDO: 'indefinido'
    };
    var MAX_LINHAS_LOG = 80;

    var config = carregarConfig();

    var estado = {
        corSelecionada: null,
        processando: false,
        esp32: { online: null, cor: null, esteira: null, processando: null, ultimoErro: null },
        backend: { online: null, ultimoErro: null, contadores: null, ultimaDeteccao: null },
        temporizadorEsp32: null,
        temporizadorBackend: null,
        pollEsp32Pausado: false,
        atualizandoBackend: false,
        promessaBackend: null
    };

    function lerArmazenamento() {
        try {
            return JSON.parse(window.localStorage.getItem(CHAVE_ARMAZENAMENTO)) || {};
        } catch (erro) {
            return {};
        }
    }

    function gravarArmazenamento(valor) {
        try {
            window.localStorage.setItem(CHAVE_ARMAZENAMENTO, JSON.stringify(valor));
            return true;
        } catch (erro) {
            return false;
        }
    }

    function normalizarUrl(url) {
        return String(url === null || url === undefined ? '' : url).trim().replace(/\/+$/, '');
    }

    function numeroOu(valor, padrao) {
        var numero = parseInt(valor, 10);
        return isNaN(numero) ? padrao : numero;
    }

    function limitarNumero(valor, minimo, maximo, padrao) {
        var numero = numeroOu(valor, padrao);
        return Math.min(maximo, Math.max(minimo, numero));
    }

    /**
     * Ordem de precedencia: query string > configuracao salva na tela > simulador.config.js.
     * O arquivo de configuracao continua sendo o ponto unico de ajuste, sem impedir
     * um ajuste rapido durante a apresentacao.
     */
    function carregarConfig() {
        var salvo = lerArmazenamento();
        var parametros = new window.URLSearchParams(window.location.search);
        var modo = String(parametros.get('modo') || salvo.modo || PADRAO.MODO_PADRAO || 'ESP32').toUpperCase();

        return {
            modo: modo === 'BACKEND' ? 'BACKEND' : 'ESP32',
            esp32Url: normalizarUrl(parametros.get('esp32') || salvo.esp32Url || PADRAO.ESP32_URL || ''),
            backendUrl: normalizarUrl(parametros.get('backend') || salvo.backendUrl || PADRAO.BACKEND_URL || ''),
            dispositivoId: limitarNumero(parametros.get('dispositivoId') || salvo.dispositivoId, 1, 999999, numeroOu(PADRAO.DISPOSITIVO_ID, 1)),
            confianca: limitarNumero(parametros.get('confianca') || salvo.confianca, 0, 100, numeroOu(PADRAO.CONFIANCA, 90)),
            intervaloEsp32: limitarNumero(PADRAO.INTERVALO_STATUS_ESP32_MS, 500, 10000, 1000),
            intervaloBackend: limitarNumero(PADRAO.INTERVALO_BACKEND_MS, 2000, 60000, 5000),
            timeoutEsp32: limitarNumero(PADRAO.TIMEOUT_ESP32_MS, 1000, 60000, 6000),
            timeoutProcessar: limitarNumero(PADRAO.TIMEOUT_PROCESSAR_MS, 3000, 180000, 60000),
            timeoutBackendGet: limitarNumero(PADRAO.TIMEOUT_BACKEND_GET_MS, 1000, 120000, 12000),
            timeoutBackendPost: limitarNumero(PADRAO.TIMEOUT_BACKEND_POST_MS, 3000, 180000, 60000),
            esperaConfirmacao: limitarNumero(PADRAO.ESPERA_CONFIRMACAO_MS, 3000, 120000, 30000)
        };
    }

    // ====================================================================
    // 2. Utilitarios de interface
    // ====================================================================

    function el(id) {
        return document.getElementById(id);
    }

    function baseBackend() {
        return config.backendUrl || (window.location.origin === 'null' ? '' : window.location.origin);
    }

    function descricaoBackend() {
        var base = baseBackend();
        return config.backendUrl ? base : base + ' (mesma origem da página)';
    }

    /** Atualiza um indicador no formato "simbolo + texto" usado no sistema. */
    function definirIndicador(id, texto, simbolo, situacao) {
        var elemento = el(id);
        if (!elemento) {
            return;
        }
        if (situacao) {
            elemento.setAttribute('data-estado', situacao);
        }
        var icone = elemento.querySelector('.indicador-simbolo');
        if (icone) {
            icone.textContent = simbolo;
        }
        var rotulo = elemento.querySelector('.indicador-texto');
        if (rotulo) {
            rotulo.textContent = texto;
        }
    }

    function definirRotuloCor(elemento, cor) {
        if (!elemento) {
            return;
        }
        var classe = CLASSES_COR[cor] || 'indefinido';
        elemento.className = 'rotulo-cor rotulo-cor--' + classe;
        elemento.textContent = cor || 'SEM DADOS';
    }

    function definirMensagem(texto, tipo) {
        var elemento = el('mensagem-processamento');
        if (!elemento) {
            return;
        }
        elemento.textContent = texto || '';
        elemento.setAttribute('data-tipo', tipo || '');
    }

    function definirMensagemConfig(texto, tipo) {
        var elemento = el('mensagem-config');
        if (!elemento) {
            return;
        }
        elemento.textContent = texto || '';
        elemento.setAttribute('data-tipo', tipo || '');
    }

    function formatarHora(valor) {
        var data = (valor instanceof Date) ? valor : new Date(valor);
        return isNaN(data.getTime()) ? '—' : data.toLocaleTimeString('pt-BR', { hour12: false });
    }

    /** O backend devolve LocalDateTime; alguns navegadores nao aceitam fracoes com mais de 3 digitos. */
    function normalizarIso(valor) {
        if (!valor) {
            return '';
        }
        return String(valor).replace(/(\.\d{3})\d+/, '$1');
    }

    function formatarDataHora(valor) {
        if (!valor) {
            return '—';
        }
        var data = new Date(normalizarIso(valor));
        return isNaN(data.getTime())
            ? String(valor)
            : data.toLocaleDateString('pt-BR') + ' ' + data.toLocaleTimeString('pt-BR', { hour12: false });
    }

    function formatarNumero(valor) {
        return Number(valor || 0).toLocaleString('pt-BR');
    }

    function interpretarBooleano(valor) {
        if (typeof valor === 'boolean') {
            return valor;
        }
        if (typeof valor === 'number') {
            return valor !== 0;
        }
        if (typeof valor === 'string') {
            var texto = valor.trim().toLowerCase();
            if (texto === 'true' || texto === '1' || texto === 'ligada' || texto === 'on') {
                return true;
            }
            if (texto === 'false' || texto === '0' || texto === 'parada' || texto === 'off') {
                return false;
            }
        }
        return null;
    }

    function esperar(ms) {
        return new Promise(function (resolver) {
            window.setTimeout(resolver, ms);
        });
    }

    // ====================================================================
    // 3. Log das requisicoes (registro da comunicacao real)
    // ====================================================================

    function registrarLog(tipo, texto) {
        var lista = el('lista-log');
        if (!lista) {
            return;
        }
        var vazio = lista.querySelector('.log-vazio');
        if (vazio) {
            lista.removeChild(vazio);
        }

        var item = document.createElement('li');
        item.className = 'log-item';
        item.setAttribute('data-tipo', tipo);

        var hora = document.createElement('span');
        hora.className = 'log-hora';
        hora.textContent = formatarHora(new Date());

        var mensagem = document.createElement('span');
        mensagem.textContent = texto;

        item.appendChild(hora);
        item.appendChild(mensagem);
        lista.appendChild(item);

        while (lista.children.length > MAX_LINHAS_LOG) {
            lista.removeChild(lista.firstChild);
        }
        lista.scrollTop = lista.scrollHeight;

        if (tipo === 'erro') {
            console.error('IoT Sorting (simulador): ' + texto);
        } else {
            console.log('IoT Sorting (simulador): ' + texto);
        }
    }

    function limparLog() {
        var lista = el('lista-log');
        if (!lista) {
            return;
        }
        lista.innerHTML = '';
        var vazio = document.createElement('li');
        vazio.className = 'log-vazio';
        vazio.textContent = 'Nenhuma requisição registrada ainda.';
        lista.appendChild(vazio);
    }

    // ====================================================================
    // 4. Camada HTTP
    // ====================================================================

    function mensagemErroHttp(status, dados) {
        var detalhe = dados && dados.message ? ' — ' + dados.message : '';
        return 'Erro HTTP ' + status + detalhe;
    }

    /** Evita repetir no log a mesma falha do polling (ex.: /status a cada segundo). */
    var ultimoErroPorUrl = {};

    function registrarErro(url, mensagem, silenciarRepetido) {
        if (silenciarRepetido) {
            if (ultimoErroPorUrl[url] === mensagem) {
                return;
            }
            ultimoErroPorUrl[url] = mensagem;
        }
        registrarLog('erro', mensagem);
    }

    function limparErroRegistrado(url) {
        delete ultimoErroPorUrl[url];
    }

    /**
     * Executa uma requisicao HTTP real.
     *
     * @returns promessa com { status, dados, texto, url, duracaoMs } em caso de
     *          sucesso; em caso de falha a promessa e rejeitada com
     *          { tipo: 'http' | 'timeout' | 'rede', status, url, mensagem }.
     */
    function requisitar(url, opcoes, timeoutMs) {
        if (!url) {
            return Promise.reject({ tipo: 'configuracao', url: url, mensagem: 'Endereço não configurado' });
        }

        var silenciarRepetido = !!(opcoes && opcoes.silenciarErroRepetido);
        var silenciarLog = !!(opcoes && opcoes.silenciarLog);
        var controlador = new window.AbortController();
        var inicio = Date.now();
        var metodo = (opcoes && opcoes.method) || 'GET';
        var temporizador = window.setTimeout(function () {
            controlador.abort();
        }, timeoutMs);

        var configuracao = Object.assign({ cache: 'no-store', signal: controlador.signal }, opcoes || {});
        delete configuracao.silenciarErroRepetido;
        delete configuracao.silenciarLog;
        configuracao.headers = Object.assign({ 'Accept': 'application/json' }, (opcoes && opcoes.headers) || {});

        if (!silenciarLog) {
            registrarLog('enviada', metodo + ' ' + url);
        }

        return window.fetch(url, configuracao)
            .then(function (resposta) {
                return resposta.text().then(function (texto) {
                    var duracao = Date.now() - inicio;
                    var dados = null;
                    if (texto) {
                        try {
                            dados = JSON.parse(texto);
                        } catch (erro) {
                            dados = null;
                        }
                    }

                    if (!resposta.ok) {
                        throw {
                            tipo: 'http',
                            status: resposta.status,
                            url: url,
                            dados: dados,
                            metodo: metodo,
                            mensagem: mensagemErroHttp(resposta.status, dados)
                        };
                    }

                    limparErroRegistrado(url);
                    if (!silenciarLog) {
                        registrarLog('recebida', metodo + ' ' + url + ' → HTTP ' + resposta.status + ' · ' + duracao + ' ms');
                    }
                    return { status: resposta.status, dados: dados, texto: texto, url: url, duracaoMs: duracao };
                });
            })
            .catch(function (erro) {
                var duracao = Date.now() - inicio;
                if (erro && erro.tipo === 'http') {
                    registrarErro(url, metodo + ' ' + url + ' → ' + erro.mensagem + ' · ' + duracao + ' ms', silenciarRepetido);
                    throw erro;
                }

                var abortado = erro && (erro.name === 'AbortError' || erro.code === 20);
                var mensagem = abortado
                    ? 'Tempo esgotado (' + timeoutMs + ' ms) sem resposta de ' + metodo + ' ' + url
                    : 'Falha de rede em ' + metodo + ' ' + url + ' (endereço, Wi-Fi ou CORS)';
                registrarErro(url, mensagem, silenciarRepetido);
                throw { tipo: abortado ? 'timeout' : 'rede', url: url, metodo: metodo, mensagem: mensagem };
            })
            .finally(function () {
                window.clearTimeout(temporizador);
            });
    }

    // ====================================================================
    // 5. ESP32 (GET /status, GET /cor, GET /processar)
    // ====================================================================

    function urlEsp32(caminho) {
        return config.esp32Url + caminho;
    }

    /** GET /status - le a cor selecionada no ESP32 e o estado da esteira. */
    function consultarStatusEsp32(opcoes) {
        var silenciarLog = !!(opcoes && opcoes.silenciarLog);

        if (!config.esp32Url) {
            var pendencia = {
                tipo: 'configuracao',
                url: '',
                mensagem: 'URL do ESP32 não configurada (veja simulador.config.js ou o painel Configurações)'
            };
            estado.esp32.online = false;
            estado.esp32.ultimoErro = pendencia;
            renderizarEsp32();
            return Promise.reject(pendencia);
        }

        return requisitar(urlEsp32('/status'), {
            method: 'GET',
            silenciarErroRepetido: true,
            silenciarLog: silenciarLog
        }, config.timeoutEsp32)
            .then(function (resposta) {
                var dados = resposta.dados || {};
                var anterior = {
                    cor: estado.esp32.cor,
                    esteira: estado.esp32.esteira,
                    processando: estado.esp32.processando
                };

                estado.esp32.online = true;
                estado.esp32.ultimoErro = null;
                estado.esp32.cor = dados.cor ? String(dados.cor).toUpperCase() : null;
                estado.esp32.esteira = interpretarBooleano(dados.esteira);
                estado.esp32.processando = interpretarBooleano(dados.processando);
                renderizarEsp32();

                var mudou = anterior.cor !== estado.esp32.cor
                    || anterior.esteira !== estado.esp32.esteira
                    || anterior.processando !== estado.esp32.processando;
                if (silenciarLog && mudou) {
                    registrarLog('recebida', 'GET ' + urlEsp32('/status') + ' → cor=' + (estado.esp32.cor || '—') +
                        ' · esteira=' + estado.esp32.esteira + ' · processando=' + estado.esp32.processando);
                }

                return estado.esp32;
            })
            .catch(function (erro) {
                estado.esp32.online = false;
                estado.esp32.ultimoErro = erro;
                estado.esp32.esteira = null;
                estado.esp32.processando = null;
                renderizarEsp32();
                throw erro;
            });
    }

    /** GET /cor?valor=COR - grava a cor selecionada no ESP32. */
    function enviarCorParaEsp32(cor) {
        return requisitar(urlEsp32('/cor?valor=' + encodeURIComponent(cor)), { method: 'GET' }, config.timeoutEsp32)
            .then(function () {
                registrarLog('aviso', 'ESP32 confirmou a cor ' + cor + ' (GET /cor?valor=' + cor + ')');
                return true;
            });
    }

    /**
     * GET /processar - o firmware para a esteira, aguarda ~2s, envia
     * POST /api/objetos e religa a esteira. Enquanto o ESP32 executa esse ciclo
     * o servidor web dele fica ocupado, por isso o polling de /status e pausado.
     */
    function comandarProcessamentoEsp32() {
        return requisitar(urlEsp32('/processar'), { method: 'GET' }, config.timeoutProcessar)
            .then(function (resposta) {
                if (resposta.dados) {
                    registrarLog('aviso', 'Resposta de /processar: ' + JSON.stringify(resposta.dados));
                } else if (resposta.texto) {
                    registrarLog('aviso', 'Resposta de /processar: ' + resposta.texto.trim().slice(0, 200));
                }
                return resposta;
            });
    }

    function pausarPollEsp32(pausado) {
        estado.pollEsp32Pausado = pausado;
    }

    function iniciarMonitorEsp32() {
        pararMonitorEsp32();

        if (config.modo !== 'ESP32' || !config.esp32Url) {
            return;
        }

        consultarStatusEsp32().catch(function () {
            // A falha ja foi registrada no log e refletida na tela.
        });

        estado.temporizadorEsp32 = window.setInterval(function () {
            if (document.hidden || estado.processando || estado.pollEsp32Pausado) {
                return;
            }
            consultarStatusEsp32({ silenciarLog: true }).catch(function () {
                // A falha ja foi registrada no log; o proximo ciclo tenta novamente.
            });
        }, config.intervaloEsp32);
    }

    function pararMonitorEsp32() {
        if (estado.temporizadorEsp32) {
            window.clearInterval(estado.temporizadorEsp32);
            estado.temporizadorEsp32 = null;
        }
    }

    // ====================================================================
    // 6. Backend (GET /api/dashboard, GET /api/objetos/recentes, POST /api/objetos)
    // ====================================================================

    /**
     * Le o resumo do dashboard e a ultima deteccao.
     *
     * @returns promessa com { contadores, ultimaDeteccao } - sempre vindos da API.
     */
    function atualizarBackend(opcoes) {
        if (estado.atualizandoBackend) {
            return estado.promessaBackend || Promise.resolve(null);
        }

        var silenciarLog = !!(opcoes && opcoes.silenciarLog);
        estado.atualizandoBackend = true;
        var base = baseBackend();

        var promessa = requisitar(base + '/api/dashboard', {
            method: 'GET',
            silenciarErroRepetido: true,
            silenciarLog: silenciarLog
        }, config.timeoutBackendGet)
            .then(function (resposta) {
                estado.backend.online = true;
                estado.backend.ultimoErro = null;
                estado.backend.contadores = resposta.dados || null;
                renderizarBackend();

                return requisitar(base + '/api/objetos/recentes?limite=1', {
                    method: 'GET',
                    silenciarLog: silenciarLog
                }, config.timeoutBackendGet)
                    .then(function (respostaRecentes) {
                        var lista = Array.isArray(respostaRecentes.dados) ? respostaRecentes.dados : [];
                        estado.backend.ultimaDeteccao = lista.length > 0 ? lista[0] : null;
                        renderizarUltimaDeteccao();
                        return { contadores: estado.backend.contadores, ultimaDeteccao: estado.backend.ultimaDeteccao };
                    })
                    .catch(function (erro) {
                        registrarLog('erro', 'Falha ao ler GET ' + base + '/api/objetos/recentes: ' + erro.mensagem);
                        estado.backend.ultimaDeteccao = null;
                        renderizarUltimaDeteccao();
                        return { contadores: estado.backend.contadores, ultimaDeteccao: null };
                    });
            })
            .catch(function (erro) {
                estado.backend.online = false;
                estado.backend.ultimoErro = erro;
                renderizarBackend();
                throw erro;
            })
            .finally(function () {
                estado.atualizandoBackend = false;
                estado.promessaBackend = null;
            });

        estado.promessaBackend = promessa;
        return promessa;
    }

    function iniciarMonitorBackend() {
        pararMonitorBackend();
        atualizarBackend().catch(function () {
            // A falha ja foi registrada no log e refletida na tela.
        });
        estado.temporizadorBackend = window.setInterval(function () {
            if (document.hidden || estado.processando) {
                return;
            }
            atualizarBackend({ silenciarLog: true }).catch(function () {
                // A falha ja foi registrada no log; o proximo ciclo tenta novamente.
            });
        }, config.intervaloBackend);
    }

    function pararMonitorBackend() {
        if (estado.temporizadorBackend) {
            window.clearInterval(estado.temporizadorBackend);
            estado.temporizadorBackend = null;
        }
    }

    /** Fotografia dos contadores antes do ciclo, usada para confirmar o registro. */
    function capturarReferenciaBackend() {
        var contadores = estado.backend.contadores || {};
        return {
            ultimaDeteccao: contadores.ultimaDeteccao || null,
            objetosHoje: Number(contadores.objetosHoje || 0)
        };
    }

    /**
     * Verifica se o backend registrou o objeto do ciclo de /processar.
     * Quem envia o POST e o ESP32, portanto a unica forma honesta de confirmar
     * e observar o dashboard: ultimaDeteccao muda ou objetosHoje aumenta.
     */
    function confirmarRegistroNoBackend(referencia) {
        var limite = Date.now() + config.esperaConfirmacao;

        function tentar() {
            return atualizarBackend({ silenciarLog: true })
                .then(function (resumo) {
                    var contadores = (resumo && resumo.contadores) || {};
                    var deteccaoMudou = !!contadores.ultimaDeteccao && contadores.ultimaDeteccao !== referencia.ultimaDeteccao;
                    var contagemAumentou = Number(contadores.objetosHoje || 0) > Number(referencia.objetosHoje || 0);

                    if (deteccaoMudou || contagemAumentou) {
                        return {
                            confirmado: true,
                            contadores: contadores,
                            ultimaDeteccao: estado.backend.ultimaDeteccao
                        };
                    }
                    if (Date.now() >= limite) {
                        return { confirmado: false, contadores: contadores };
                    }
                    return esperar(1500).then(tentar);
                })
                .catch(function () {
                    if (Date.now() >= limite) {
                        return { confirmado: false, contadores: null };
                    }
                    return esperar(1500).then(tentar);
                });
        }

        return tentar();
    }

    // ====================================================================
    // 7. Fluxo de PROCESSAR OBJETO
    // ====================================================================

    function bloquearBotaoProcessar(bloqueado, texto) {
        var botao = el('botao-processar');
        if (!botao) {
            return;
        }
        botao.disabled = bloqueado;
        botao.setAttribute('data-estado', bloqueado ? 'processando' : 'pronto');
        var rotulo = el('botao-processar-texto');
        if (rotulo) {
            rotulo.textContent = texto;
        }
    }

    /** Estado exibido durante o ciclo: a esteira e parada pelo ESP32 dentro de /processar. */
    function exibirCicloEsp32EmAndamento(cor) {
        definirIndicador('valor-esteira', 'PARADA', '■', 'desligada');
        el('apoio-esteira').textContent = 'Esteira parada pelo ESP32 no ciclo de GET /processar';
        definirIndicador('valor-status', 'PROCESSANDO', '■', 'alerta');
        el('apoio-status').textContent = 'Aguardando a resposta de GET /processar (0 s)';
        el('valor-cor-esp32').textContent = cor;
        el('apoio-cor-esp32').textContent = 'Enviada agora em GET /cor?valor=' + cor;
    }

    function processarModoEsp32(cor) {
        var referencia = capturarReferenciaBackend();
        var inicio = Date.now();

        pausarPollEsp32(true);
        exibirCicloEsp32EmAndamento(cor);

        var contadorCiclo = window.setInterval(function () {
            var segundos = Math.round((Date.now() - inicio) / 1000);
            el('apoio-status').textContent = 'Aguardando a resposta de GET /processar (' + segundos + ' s)';
        }, 500);

        return enviarCorParaEsp32(cor)
            .then(function () {
                definirMensagem('Cor enviada ao ESP32. Comandando a esteira (GET /processar)…', 'info');
                el('apoio-status').textContent = 'GET /processar em andamento';
                return comandarProcessamentoEsp32();
            })
            .then(function (resposta) {
                window.clearInterval(contadorCiclo);
                pausarPollEsp32(false);
                registrarLog('aviso', 'Ciclo do ESP32 concluído em ' + resposta.duracaoMs + ' ms (HTTP ' + resposta.status + ')');
                definirMensagem('ESP32 concluiu o ciclo (HTTP ' + resposta.status + ' em ' + resposta.duracaoMs + ' ms). Confirmando o registro no backend…', 'info');
                return consultarStatusEsp32().catch(function () {
                    return null;
                }).then(function () {
                    return confirmarRegistroNoBackend(referencia);
                });
            })
            .catch(function (erro) {
                window.clearInterval(contadorCiclo);
                pausarPollEsp32(false);
                throw erro;
            })
            .then(function (resultado) {
                if (resultado && resultado.confirmado) {
                    var ultima = resultado.ultimaDeteccao || {};
                    var hora = ultima.dataHora ? formatarHora(normalizarIso(ultima.dataHora)) : formatarHora(new Date());
                    registrarLog('recebida', 'Registro confirmado no backend: cor=' + (ultima.cor || cor) + ' id=' + (ultima.id || '—') + ' confianca=' + (ultima.confianca || '—') + ' dataHora=' + (ultima.dataHora || '—'));
                    definirMensagem('Objeto registrado no backend pelo ESP32: ' + (ultima.cor || cor) + ' às ' + hora + '. Contadores atualizados.', 'sucesso');
                } else if (resultado) {
                    definirMensagem('O ESP32 respondeu, mas o backend consultado (' + descricaoBackend() + ') não mostrou a nova detecção em ' + Math.round(config.esperaConfirmacao / 1000) + ' s. Confira se o firmware aponta para o mesmo backend.', 'erro');
                    registrarLog('erro', 'Registro não confirmado em ' + descricaoBackend() + ' (ultimaDeteccao e objetosHoje não mudaram)');
                }
                return resultado;
            });
    }

    function processarModoBackend(cor) {
        var corpo = {
            dispositivoId: config.dispositivoId,
            cor: cor,
            confianca: config.confianca
        };
        var url = baseBackend() + '/api/objetos';

        definirIndicador('valor-esteira', 'NÃO CONTROLADA', '■', 'desconhecido');
        el('apoio-esteira').textContent = 'MODO BACKEND: o simulador não aciona o relé do ESP32';
        definirIndicador('valor-status', 'ENVIANDO', '■', 'alerta');
        el('apoio-status').textContent = 'POST ' + url + ' em andamento';

        return requisitar(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(corpo)
        }, config.timeoutBackendPost)
            .then(function (resposta) {
                var objeto = resposta.dados || {};
                registrarLog('aviso', 'Backend registrou o objeto: id=' + objeto.id + ' cor=' + objeto.cor + ' confianca=' + objeto.confianca + ' dataHora=' + objeto.dataHora);
                definirMensagem('Objeto registrado no backend (HTTP ' + resposta.status + '): ' + (objeto.cor || cor) + ' · id ' + (objeto.id || '—') + ' · ' + formatarDataHora(objeto.dataHora), 'sucesso');
                definirIndicador('valor-status', 'CONCLUÍDO', '●', 'online');
                el('apoio-status').textContent = 'Último POST /api/objetos concluído pelo simulador (MODO BACKEND)';
                return atualizarBackend();
            });
    }

    /** Traduz a falha da requisicao na mensagem pedida pelo projeto. */
    function tratarErroProcessamento(erro) {
        var mensagem = (erro && erro.mensagem) ? erro.mensagem : 'Falha desconhecida na comunicação';
        var tipo = erro ? erro.tipo : 'rede';
        var origem = erro && erro.url && config.esp32Url && erro.url.indexOf(config.esp32Url) === 0 ? 'ESP32' : 'BACKEND';

        if (tipo === 'http' || tipo === 'timeout' || tipo === 'configuracao') {
            definirMensagem(mensagem, 'erro');
        } else if (origem === 'ESP32') {
            definirMensagem('ESP32 não conectado — ' + mensagem, 'erro');
            definirIndicador('valor-esteira', 'DESCONHECIDA', '■', 'desconhecido');
            el('apoio-esteira').textContent = 'Sem resposta de GET ' + config.esp32Url + '/status';
            definirIndicador('valor-status', 'SEM RESPOSTA', '■', 'offline');
            el('apoio-status').textContent = 'O ESP32 não respondeu ao simulador';
        } else {
            definirMensagem('Backend indisponível — ' + mensagem, 'erro');
            definirIndicador('valor-status', 'SEM RESPOSTA', '■', 'offline');
            el('apoio-status').textContent = 'O backend não respondeu ao simulador';
        }

        registrarLog('erro', 'Falha no processamento (' + origem + '): ' + mensagem);

        if (origem === 'ESP32' && config.modo === 'ESP32') {
            // Mostra na tela o estado real do ESP32 (ou confirma que ele nao responde).
            consultarStatusEsp32().catch(function () {
                // Ja tratado acima.
            });
        }
    }

    /** Acoes do botao PROCESSAR OBJETO. */
    function processar() {
        if (estado.processando) {
            registrarLog('aviso', 'Clique ignorado: já existe um processamento em andamento');
            return;
        }

        var cor = estado.corSelecionada;
        if (!cor) {
            definirMensagem('Selecione uma cor antes de processar o objeto.', 'erro');
            return;
        }
        if (config.modo === 'ESP32' && !config.esp32Url) {
            definirMensagem('Informe a URL do ESP32 no painel Configurações para processar em MODO ESP32.', 'erro');
            return;
        }
        if (!baseBackend()) {
            definirMensagem('Endereço do backend não identificado. Abra o simulador pelo próprio backend (http://localhost:8080/simulador.html) ou informe a URL em Configurações.', 'erro');
            return;
        }

        estado.processando = true;
        bloquearBotaoProcessar(true, 'PROCESSANDO...');
        definirMensagem('Processando objeto...', 'info');
        registrarLog('aviso', 'Processando ' + cor + ' em ' + (config.modo === 'ESP32' ? 'MODO ESP32' : 'MODO BACKEND'));

        var concluido = false;
        var fluxo = (config.modo === 'ESP32') ? processarModoEsp32(cor) : processarModoBackend(cor);

        fluxo
            .then(function () {
                concluido = true;
            })
            .catch(function (erro) {
                tratarErroProcessamento(erro);
            })
            .then(function () {
                estado.processando = false;
                bloquearBotaoProcessar(false, 'PROCESSAR OBJETO');
                return concluido;
            })
            .then(function (sucesso) {
                // Em caso de falha o estado do ESP32 ja foi atualizado em tratarErroProcessamento.
                if (sucesso && config.modo === 'ESP32') {
                    consultarStatusEsp32().catch(function () {
                        // Estado de erro ja refletido na tela.
                    });
                }
            });
    }

    // ====================================================================
    // 8. Renderizacao
    // ====================================================================

    /** Indicador do cabecalho: nunca esconde o modo em uso. */
    function renderizarIndicadorModo() {
        if (config.modo === 'BACKEND') {
            definirIndicador('indicador-modo', 'BACKEND DIRETO', '■', 'alerta');
            return;
        }
        if (estado.esp32.online === true) {
            definirIndicador('indicador-modo', 'ESP32 CONECTADO', '●', 'online');
        } else if (estado.esp32.online === false) {
            definirIndicador('indicador-modo', 'ESP32 OFFLINE', '■', 'offline');
        } else {
            definirIndicador('indicador-modo', 'CONECTANDO', '■', 'desconhecido');
        }
    }

    function renderizarModo() {
        var modoEsp32 = config.modo === 'ESP32';

        el('botao-modo-esp32').setAttribute('aria-pressed', modoEsp32 ? 'true' : 'false');
        el('botao-modo-backend').setAttribute('aria-pressed', modoEsp32 ? 'false' : 'true');
        el('etiqueta-modo').textContent = modoEsp32 ? 'ESP32' : 'BACKEND DIRETO';
        el('descricao-modo').textContent = modoEsp32
            ? 'A cor é enviada ao ESP32 (GET /cor) e o ciclo é comandado por GET /processar: o próprio ESP32 para a esteira, envia POST /api/objetos e religa a esteira.'
            : 'Modo de teste: o simulador envia POST /api/objetos direto ao backend. O ESP32 não é acionado — use apenas quando ele não estiver conectado.';

        el('dado-esp32-url').textContent = config.esp32Url || 'não configurado';
        el('dado-backend-url').textContent = descricaoBackend();
        el('dado-dispositivo').textContent = 'ID ' + config.dispositivoId + ' · confiança ' + config.confianca + '%';
        el('rodape-detalhe').textContent = 'modo=' + config.modo + ' · esp32=' + (config.esp32Url || '—') + ' · backend=' + (baseBackend() || '—');

        renderizarIndicadorModo();
    }

    /** Cartoes "Esteira", "Status" e "Cor no ESP32" (dados de GET /status). */
    function renderizarEsp32() {
        var esp32 = estado.esp32;

        if (esp32.online === false) {
            definirIndicador('valor-esteira', 'SEM RESPOSTA', '■', 'desconhecido');
            el('apoio-esteira').textContent = config.esp32Url
                ? 'ESP32 não conectado em ' + config.esp32Url
                : 'URL do ESP32 não configurada';
            definirIndicador('valor-status', 'ESP32 NÃO CONECTADO', '■', 'offline');
            el('apoio-status').textContent = esp32.ultimoErro ? esp32.ultimoErro.mensagem : 'Sem resposta de GET /status';
            el('valor-cor-esp32').textContent = '—';
            el('apoio-cor-esp32').textContent = 'Sem GET /status: não foi possível ler a cor do ESP32';

        } else if (esp32.online === true) {
            if (esp32.esteira === true) {
                definirIndicador('valor-esteira', 'LIGADA', '●', 'ligada');
                el('apoio-esteira').textContent = 'Relé acionado (GET /status: esteira = true)';
            } else if (esp32.esteira === false) {
                definirIndicador('valor-esteira', 'PARADA', '■', 'desligada');
                el('apoio-esteira').textContent = 'Relé desligado (GET /status: esteira = false)';
            } else {
                definirIndicador('valor-esteira', 'SEM DADOS', '■', 'desconhecido');
                el('apoio-esteira').textContent = 'O ESP32 não informou o campo "esteira"';
            }

            if (esp32.processando === true) {
                definirIndicador('valor-status', 'PROCESSANDO', '■', 'alerta');
                el('apoio-status').textContent = 'ESP32 executando o ciclo de processamento';
            } else if (esp32.processando === false) {
                definirIndicador('valor-status', 'PRONTO', '●', 'online');
                el('apoio-status').textContent = 'ESP32 livre para o próximo objeto';
            } else {
                definirIndicador('valor-status', 'SEM DADOS', '■', 'desconhecido');
                el('apoio-status').textContent = 'O ESP32 não informou o campo "processando"';
            }

            el('valor-cor-esp32').textContent = esp32.cor || '—';
            el('apoio-cor-esp32').textContent = esp32.cor
                ? 'Lida agora em GET /status'
                : 'O ESP32 respondeu GET /status sem cor definida';
        } else {
            definirIndicador('valor-esteira', 'SEM DADOS', '■', 'desconhecido');
            definirIndicador('valor-status', 'SEM DADOS', '■', 'desconhecido');
            el('valor-cor-esp32').textContent = '—';
        }

        renderizarIndicadorModo();
    }

    /** Cartao "Backend" + area de contadores. */
    function renderizarBackend() {
        var backend = estado.backend;

        if (backend.online === true) {
            definirIndicador('valor-backend', 'ONLINE', '●', 'online');
            el('apoio-backend').textContent = 'GET /api/dashboard respondendo em ' + descricaoBackend();
        } else if (backend.online === false) {
            definirIndicador('valor-backend', 'OFFLINE', '■', 'offline');
            el('apoio-backend').textContent = backend.ultimoErro ? backend.ultimoErro.mensagem : 'Sem resposta de GET /api/dashboard';
        } else {
            definirIndicador('valor-backend', 'VERIFICANDO', '■', 'desconhecido');
            el('apoio-backend').textContent = 'Consultando GET /api/dashboard';
        }

        renderizarContadores();
    }

    /** Contadores: todos os valores vem de GET /api/dashboard. */
    function renderizarContadores() {
        var dados = estado.backend.contadores;
        if (!dados) {
            return;
        }

        CAMPOS_CONTADORES.forEach(function (campo) {
            var elemento = el('contador-' + campo);
            if (elemento) {
                elemento.textContent = formatarNumero(dados[campo]);
            }
        });

        el('contadores-atualizado').textContent = 'atualizado ' + formatarHora(new Date());
        el('contadores-detalhe').textContent =
            'Fonte: GET ' + descricaoBackend() + '/api/dashboard · dispositivo online: ' +
            (dados.dispositivoOnline ? 'sim' : 'não') + ' · esteira segundo o backend: ' +
            (dados.esteiraLigada ? 'ligada' : 'parada') + ' · dispositivos cadastrados: ' +
            formatarNumero(dados.totalDispositivos);
    }

    /** Ultima deteccao: horario de /api/dashboard e cor de /api/objetos/recentes. */
    function renderizarUltimaDeteccao() {
        var ultima = estado.backend.ultimaDeteccao;
        var contadores = estado.backend.contadores || {};
        var cor = ultima && ultima.cor ? String(ultima.cor).toUpperCase() : null;
        var dataHora = (ultima && ultima.dataHora) || contadores.ultimaDeteccao || null;

        definirRotuloCor(el('ultima-cor'), cor);
        el('ultima-hora').textContent = dataHora ? formatarHora(normalizarIso(dataHora)) : '—';

        if (ultima) {
            el('ultima-apoio').textContent =
                'ID ' + ultima.id + ' · confiança ' + ultima.confianca + '% · ' +
                (ultima.dispositivoNome || 'dispositivo ' + ultima.dispositivoId) + ' · ' +
                formatarDataHora(dataHora) + ' (GET /api/objetos/recentes?limite=1)';
        } else if (dataHora) {
            el('ultima-apoio').textContent =
                'Horário de ultimaDeteccao: ' + formatarDataHora(dataHora) +
                ' (a lista de detecções recentes não retornou registros)';
        } else {
            el('ultima-apoio').textContent =
                'Nenhuma detecção registrada no backend consultado (ultimaDeteccao = null)';
        }
    }

    // ====================================================================
    // 9. Interacao
    // ====================================================================

    function selecionarCor(cor) {
        estado.corSelecionada = cor;

        Array.prototype.forEach.call(document.querySelectorAll('.botao-cor'), function (botao) {
            botao.setAttribute('aria-pressed', botao.getAttribute('data-cor') === cor ? 'true' : 'false');
        });

        var texto = el('texto-cor-selecionada');
        texto.innerHTML = '';
        texto.appendChild(document.createTextNode('Cor selecionada: '));
        var destaque = document.createElement('strong');
        destaque.textContent = cor;
        texto.appendChild(destaque);

        el('etiqueta-cor').textContent = cor;
        el('botao-processar').disabled = estado.processando;
        definirMensagem('', '');

        registrarLog('aviso', 'Cor selecionada na tela: ' + cor +
            (config.modo === 'ESP32' ? ' (será gravada no ESP32 em GET /cor)' : ' (será enviada ao backend em POST /api/objetos)'));
    }

    function definirModo(modo) {
        if (estado.processando) {
            definirMensagem('Aguarde o processamento atual terminar para trocar de modo.', 'erro');
            return;
        }

        config.modo = (modo === 'BACKEND') ? 'BACKEND' : 'ESP32';
        var preferencias = lerArmazenamento();
        preferencias.modo = config.modo;
        gravarArmazenamento(preferencias);

        renderizarModo();
        registrarLog('aviso', 'Modo alterado para ' + config.modo);

        if (config.modo === 'ESP32') {
            iniciarMonitorEsp32();
        } else {
            pararMonitorEsp32();
            estado.esp32.online = null;
            estado.esp32.esteira = null;
            estado.esp32.processando = null;
            estado.esp32.cor = null;
            definirIndicador('valor-esteira', 'NÃO CONTROLADA', '■', 'desconhecido');
            el('apoio-esteira').textContent = 'MODO BACKEND: o simulador não aciona o relé do ESP32';
            definirIndicador('valor-status', 'SEM DADOS', '■', 'desconhecido');
            el('apoio-status').textContent = 'MODO BACKEND: o ESP32 não é monitorado';
            el('valor-cor-esp32').textContent = '—';
            el('apoio-cor-esp32').textContent = 'Use "Testar ESP32" nas Configurações para uma leitura pontual';
        }
    }

    function preencherFormulario() {
        el('campo-esp32').value = config.esp32Url;
        el('campo-backend').value = config.backendUrl;
        el('campo-dispositivo').value = config.dispositivoId;
        el('campo-confianca').value = config.confianca;
    }

    /** Valida e aplica a configuracao informada na tela (salva no navegador). */
    function salvarConfiguracoes(evento) {
        evento.preventDefault();

        var esp32 = normalizarUrl(el('campo-esp32').value);
        var backend = normalizarUrl(el('campo-backend').value);
        var dispositivoId = parseInt(el('campo-dispositivo').value, 10);
        var confianca = parseInt(el('campo-confianca').value, 10);

        if (esp32 && !/^https?:\/\/.+/i.test(esp32)) {
            definirMensagemConfig('URL do ESP32 inválida: use o formato http://192.168.1.6', 'erro');
            return;
        }
        if (backend && !/^https?:\/\/.+/i.test(backend)) {
            definirMensagemConfig('URL do backend inválida: use o formato https://iot-sorting.onrender.com', 'erro');
            return;
        }
        if (isNaN(dispositivoId) || dispositivoId < 1) {
            definirMensagemConfig('O ID do dispositivo deve ser um número maior que zero.', 'erro');
            return;
        }
        if (isNaN(confianca) || confianca < 0 || confianca > 100) {
            definirMensagemConfig('A confiança deve estar entre 0 e 100.', 'erro');
            return;
        }

        config.esp32Url = esp32;
        config.backendUrl = backend;
        config.dispositivoId = dispositivoId;
        config.confianca = confianca;

        var salvo = gravarArmazenamento({
            modo: config.modo,
            esp32Url: config.esp32Url,
            backendUrl: config.backendUrl,
            dispositivoId: config.dispositivoId,
            confianca: config.confianca
        });

        definirMensagemConfig(
            salvo
                ? 'Configuração aplicada e salva neste navegador.'
                : 'Configuração aplicada (o navegador não permitiu salvar).',
            'sucesso'
        );

        renderizarModo();
        avisoHttps();
        iniciarMonitorEsp32();
        atualizarBackend().catch(function () {
            // A falha ja foi registrada no log.
        });
        registrarLog('aviso', 'Configuração atualizada: esp32=' + (config.esp32Url || '—') +
            ' backend=' + (baseBackend() || '—') + ' dispositivoId=' + config.dispositivoId +
            ' confianca=' + config.confianca);
    }

    /** Volta aos valores de simulador.config.js. */
    function restaurarConfiguracoes() {
        try {
            window.localStorage.removeItem(CHAVE_ARMAZENAMENTO);
        } catch (erro) {
            // Sem armazenamento disponivel: apenas recarrega os padroes.
        }
        config = carregarConfig();
        preencherFormulario();
        definirMensagemConfig('Valores restaurados a partir de simulador.config.js.', 'sucesso');
        renderizarModo();
        avisoHttps();
        iniciarMonitorEsp32();
        atualizarBackend().catch(function () {
            // A falha ja foi registrada no log.
        });
        registrarLog('aviso', 'Configuração restaurada a partir de simulador.config.js');
    }

    function testarEsp32() {
        definirMensagemConfig('Consultando GET ' + (config.esp32Url || '—') + '/status …', 'info');
        consultarStatusEsp32()
            .then(function (esp32) {
                definirMensagemConfig(
                    'ESP32 respondeu: cor=' + (esp32.cor || '—') + ' · esteira=' + esp32.esteira +
                    ' · processando=' + esp32.processando,
                    'sucesso'
                );
            })
            .catch(function (erro) {
                definirMensagemConfig('ESP32 não conectado: ' + erro.mensagem, 'erro');
            });
    }

    function testarBackend() {
        definirMensagemConfig('Consultando GET ' + descricaoBackend() + '/api/dashboard …', 'info');
        atualizarBackend()
            .then(function () {
                var contadores = estado.backend.contadores || {};
                definirMensagemConfig(
                    'Backend respondeu: objetosHoje=' + contadores.objetosHoje +
                    ' · dispositivoOnline=' + contadores.dispositivoOnline +
                    ' · ultimaDeteccao=' + (contadores.ultimaDeteccao || 'null'),
                    'sucesso'
                );
            })
            .catch(function (erro) {
                definirMensagemConfig('Backend indisponível: ' + erro.mensagem, 'erro');
            });
    }

    // ====================================================================
    // 10. Inicializacao
    // ====================================================================

    /** Explica na tela os bloqueios que o navegador impoe (HTTPS, file://, CORS). */
    function avisoHttps() {
        var caixa = el('aviso-https');
        if (!caixa) {
            return;
        }

        var protocolo = window.location.protocol;
        var base = baseBackend();

        if (protocolo === 'file:') {
            caixa.textContent = 'A página foi aberta como arquivo (file://). Abra o simulador pelo endereço do backend — por exemplo http://localhost:8080/simulador.html — para que as requisições ao ESP32 e à API não sejam bloqueadas pelo navegador.';
        } else if (protocolo === 'https:' && /^http:\/\//i.test(config.esp32Url || '')) {
            caixa.textContent = 'Esta página está em HTTPS e o ESP32 responde em HTTP (' + config.esp32Url + '). O navegador bloqueia requisições HTTP feitas a partir de páginas HTTPS (conteúdo misto): abra o simulador pelo endereço local do backend, por exemplo http://localhost:8080/simulador.html, e deixe o dashboard no Render para a plateia.';
        } else if (base && base !== window.location.origin) {
            caixa.textContent = 'O simulador está consultando outro backend (' + base + '). Se aparecer "Backend indisponível" com o backend ligado, libere a origem ' + window.location.origin + ' na propriedade iot.sorting.cors.origens-permitidas do backend (no Render, variável IOT_SORTING_CORS_ORIGENS_PERMITIDAS).';
        } else {
            caixa.hidden = true;
            return;
        }

        caixa.hidden = false;
    }

    function configurarEventos() {
        Array.prototype.forEach.call(document.querySelectorAll('.botao-cor'), function (botao) {
            botao.addEventListener('click', function () {
                selecionarCor(botao.getAttribute('data-cor'));
            });
        });

        el('botao-processar').addEventListener('click', processar);
        el('botao-modo-esp32').addEventListener('click', function () {
            definirModo('ESP32');
        });
        el('botao-modo-backend').addEventListener('click', function () {
            definirModo('BACKEND');
        });
        el('botao-limpar-log').addEventListener('click', limparLog);
        el('form-config').addEventListener('submit', salvarConfiguracoes);
        el('botao-restaurar-config').addEventListener('click', restaurarConfiguracoes);
        el('botao-testar-esp32').addEventListener('click', testarEsp32);
        el('botao-testar-backend').addEventListener('click', testarBackend);

        // Ao voltar para a aba, atualiza imediatamente em vez de esperar o ciclo.
        document.addEventListener('visibilitychange', function () {
            if (document.hidden) {
                return;
            }
            atualizarBackend().catch(function () {
                // A falha ja foi registrada no log.
            });
            if (config.modo === 'ESP32') {
                consultarStatusEsp32().catch(function () {
                    // A falha ja foi registrada no log.
                });
            }
        });
    }

    function iniciar() {
        preencherFormulario();
        renderizarModo();
        renderizarEsp32();
        renderizarUltimaDeteccao();
        avisoHttps();
        configurarEventos();

        registrarLog('aviso', 'Simulador iniciado · modo=' + config.modo + ' · backend=' + (baseBackend() || 'não identificado'));
        if (config.modo === 'ESP32') {
            registrarLog('aviso', 'Monitorando GET ' + (config.esp32Url || '(endereço não configurado)') + '/status a cada ' + config.intervaloEsp32 + ' ms');
        } else {
            registrarLog('aviso', 'MODO BACKEND: o ESP32 não será acionado; o POST /api/objetos é feito pelo simulador');
        }

        iniciarMonitorBackend();
        iniciarMonitorEsp32();
    }

    document.addEventListener('DOMContentLoaded', iniciar);
})();
