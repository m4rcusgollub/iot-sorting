/**
 * IoT Sorting - Dashboard
 * ------------------------------------------------------------------------
 * Consome a API REST da aplicacao (nenhum valor e fixado no codigo) e mantem a
 * tela atualizada por polling a cada 5 segundos.
 *
 * Para adicionar WebSocket no futuro basta substituir o polling por uma
 * chamada de `atualizarTudo()` a cada mensagem recebida do servidor: toda a
 * atualizacao de tela esta concentrada nessa funcao.
 */
(function () {
    'use strict';

    // ====================================================================
    // Configuracao
    // ====================================================================

    var INTERVALO_ATUALIZACAO_MS = 5000;
    var LIMITE_DETECOES_RECENTES = 10;

    var ENDPOINTS = {
        dashboard: '/api/dashboard',
        recentes: '/api/objetos/recentes',
        dispositivos: '/api/dispositivos'
    };

    /** Cores do grafico, na mesma ordem do enum Cor do backend. */
    var CORES = [
        { chave: 'vermelhos', rotulo: 'Vermelho', cor: '#ef4444', classe: 'vermelho' },
        { chave: 'verdes', rotulo: 'Verde', cor: '#2ecc71', classe: 'verde' },
        { chave: 'azuis', rotulo: 'Azul', cor: '#3b82f6', classe: 'azul' },
        { chave: 'amarelos', rotulo: 'Amarelo', cor: '#eab308', classe: 'amarelo' },
        { chave: 'brancos', rotulo: 'Branco', cor: '#d7dee9', classe: 'branco' },
        { chave: 'pretos', rotulo: 'Preto', cor: '#64748b', classe: 'preto' },
        { chave: 'indefinidos', rotulo: 'Indefinido', cor: '#a78bfa', classe: 'indefinido' }
    ];

    // ====================================================================
    // Estado da tela
    // ====================================================================

    var graficoCores = null;
    var idDispositivoSelecionado = '';
    var assinaturaDispositivos = '';
    var temporizador = null;
    var atualizando = false;

    function el(id) {
        return document.getElementById(id);
    }

    // ====================================================================
    // Utilitarios
    // ====================================================================

    function buscarJson(url, opcoes) {
        var configuracao = Object.assign({ cache: 'no-store', headers: { 'Accept': 'application/json' } }, opcoes || {});
        return fetch(url, configuracao).then(function (resposta) {
            if (!resposta.ok) {
                throw new Error('HTTP ' + resposta.status);
            }
            return resposta.json();
        });
    }

    function comParametroDispositivo(url) {
        if (!idDispositivoSelecionado) {
            return url;
        }
        var separador = url.indexOf('?') === -1 ? '?' : '&';
        return url + separador + 'dispositivoId=' + encodeURIComponent(idDispositivoSelecionado);
    }

    function formatarHora(iso) {
        var data = new Date(iso);
        return isNaN(data.getTime()) ? '—' : data.toLocaleTimeString('pt-BR', { hour12: false });
    }

    function formatarDataHora(iso) {
        if (!iso) {
            return '—';
        }
        var data = new Date(iso);
        return isNaN(data.getTime())
            ? '—'
            : data.toLocaleDateString('pt-BR') + ' ' + data.toLocaleTimeString('pt-BR', { hour12: false });
    }

    function formatarNumero(valor) {
        return Number(valor || 0).toLocaleString('pt-BR');
    }

    function rotuloCor(cor) {
        if (!cor) {
            return { texto: '—', classe: 'indefinido' };
        }
        var texto = String(cor).charAt(0).toUpperCase() + String(cor).slice(1).toLowerCase();
        return { texto: texto, classe: String(cor).toLowerCase() };
    }

    function definirMensagemFormulario(texto, tipo) {
        var elemento = el('mensagem-formulario');
        elemento.textContent = texto || '';
        if (tipo) {
            elemento.setAttribute('data-tipo', tipo);
        } else {
            elemento.removeAttribute('data-tipo');
        }
    }

    function definirStatusSistema(online, detalhe) {
        var indicador = el('status-sistema');
        indicador.classList.toggle('indicador--online', online === true);
        indicador.classList.toggle('indicador--offline', online === false);
        indicador.classList.toggle('indicador--neutro', online === null);
        indicador.querySelector('.indicador-simbolo').textContent = online === false ? '■' : '●';
        indicador.querySelector('.indicador-texto').textContent = online === true
            ? 'SISTEMA ONLINE'
            : (online === false ? 'SISTEMA OFFLINE' : 'CONECTANDO');
        if (detalhe) {
            indicador.setAttribute('title', detalhe);
        }
    }

    function definirCartaoStatus(idValor, idApoio, estado, texto, apoio) {
        var elemento = el(idValor);
        elemento.setAttribute('data-estado', estado);
        elemento.querySelector('.indicador-simbolo').textContent = estado === 'desconhecido' ? '■' : '●';
        elemento.querySelector('.indicador-texto').textContent = texto;
        el(idApoio).textContent = apoio;
    }

    // ====================================================================
    // Grafico (Chart.js)
    // ====================================================================

    function posicaoLegenda() {
        return window.innerWidth < 720 ? 'bottom' : 'right';
    }

    function criarGrafico() {
        var canvas = el('grafico-cores');
        if (!canvas) {
            return;
        }

        if (typeof window.Chart === 'undefined') {
            canvas.parentElement.innerHTML = '<p class="texto-apoio">Biblioteca de gráficos não carregada '
                + '(/js/vendor/chart.umd.min.js). Os números continuam disponíveis nos cartões e no resumo abaixo.</p>';
            return;
        }

        graficoCores = new window.Chart(canvas.getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: CORES.map(function (item) { return item.rotulo; }),
                datasets: [{
                    data: CORES.map(function () { return 0; }),
                    backgroundColor: CORES.map(function (item) { return item.cor; }),
                    borderColor: '#111c30',
                    borderWidth: 2,
                    hoverOffset: 6
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '58%',
                animation: { duration: 300 },
                plugins: {
                    legend: {
                        position: posicaoLegenda(),
                        labels: {
                            color: '#c6d3e8',
                            boxWidth: 12,
                            boxHeight: 12,
                            usePointStyle: true,
                            pointStyle: 'rectRounded',
                            font: { size: 12 }
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: function (contexto) {
                                return ' ' + contexto.label + ': ' + formatarNumero(contexto.parsed) + ' objeto(s)';
                            }
                        }
                    }
                }
            }
        });
    }

    function ajustarLegenda() {
        if (!graficoCores) {
            return;
        }
        var novaPosicao = posicaoLegenda();
        if (graficoCores.options.plugins.legend.position !== novaPosicao) {
            graficoCores.options.plugins.legend.position = novaPosicao;
            graficoCores.update();
        }
    }

    function atualizarGrafico(resumo) {
        var total = Number(resumo.objetosHoje || 0);

        el('grafico-total').textContent = 'Total: ' + formatarNumero(total);
        el('grafico-resumo').textContent = CORES.map(function (item) {
            return item.rotulo + ': ' + formatarNumero(resumo[item.chave]);
        }).join(' · ');
        el('grafico-vazio').hidden = total !== 0;

        if (!graficoCores) {
            return;
        }

        graficoCores.data.datasets[0].data = CORES.map(function (item) {
            return Number(resumo[item.chave] || 0);
        });
        graficoCores.update();
    }


    // ====================================================================
    // Indicadores e painel do dispositivo
    // ====================================================================

    function atualizarIndicadores() {
        return buscarJson(comParametroDispositivo(ENDPOINTS.dashboard)).then(function (resumo) {
            el('valor-objetos-hoje').textContent = formatarNumero(resumo.objetosHoje);
            el('valor-vermelhos').textContent = formatarNumero(resumo.vermelhos);
            el('valor-verdes').textContent = formatarNumero(resumo.verdes);
            el('valor-azuis').textContent = formatarNumero(resumo.azuis);

            var apoioDispositivo = resumo.dispositivo
                ? 'Última conexão: ' + formatarDataHora(resumo.dispositivo.ultimaConexao)
                : 'Nenhum dispositivo cadastrado';

            definirCartaoStatus('valor-dispositivo', 'apoio-dispositivo',
                resumo.dispositivoOnline ? 'online' : 'offline',
                resumo.dispositivoOnline ? 'ONLINE' : 'OFFLINE',
                apoioDispositivo);

            definirCartaoStatus('valor-esteira', 'apoio-esteira',
                resumo.esteiraLigada ? 'ligada' : 'desligada',
                resumo.esteiraLigada ? 'LIGADA' : 'DESLIGADA',
                resumo.esteiraLigada
                    ? 'Esteira em operação'
                    : (resumo.dispositivoOnline
                        ? 'Esteira parada (relé desligado)'
                        : 'Sem comunicação com o dispositivo'));

            atualizarGrafico(resumo);
            atualizarPainelDispositivo(resumo);
        });
    }

    function atualizarPainelDispositivo(resumo) {
        var dispositivo = resumo.dispositivo;

        el('dado-total-dispositivos').textContent = formatarNumero(resumo.totalDispositivos);
        el('dado-ultima-deteccao').textContent = formatarDataHora(resumo.ultimaDeteccao);
        el('etiqueta-recentes').textContent = 'Últimas ' + LIMITE_DETECOES_RECENTES + ' detecções';

        if (!dispositivo) {
            el('etiqueta-dispositivo').textContent = 'Nenhum dispositivo';
            el('dado-nome').textContent = '—';
            el('dado-codigo').textContent = '—';
            el('dado-ip').textContent = '—';
            el('dado-status').textContent = '—';
            el('dado-ultima-conexao').textContent = '—';
            return;
        }

        el('etiqueta-dispositivo').textContent = dispositivo.codigo;
        el('dado-nome').textContent = dispositivo.nome;
        el('dado-codigo').textContent = dispositivo.codigo;
        el('dado-ip').textContent = dispositivo.ip || '—';
        el('dado-status').textContent = resumo.dispositivoOnline ? 'ONLINE' : 'OFFLINE';
        el('dado-ultima-conexao').textContent = formatarDataHora(dispositivo.ultimaConexao);
    }

    // ====================================================================
    // Tabela de ultimas deteccoes
    // ====================================================================

    function atualizarRecentes() {
        var url = comParametroDispositivo(ENDPOINTS.recentes + '?limite=' + LIMITE_DETECOES_RECENTES);

        return buscarJson(url).then(function (deteccoes) {
            var corpo = el('corpo-tabela');
            corpo.textContent = '';

            if (!deteccoes || deteccoes.length === 0) {
                var linhaVazia = document.createElement('tr');
                var celulaVazia = document.createElement('td');
                celulaVazia.colSpan = 4;
                celulaVazia.className = 'tabela-mensagem';
                celulaVazia.textContent = 'Nenhuma detecção registrada ainda.';
                linhaVazia.appendChild(celulaVazia);
                corpo.appendChild(linhaVazia);
                return;
            }

            deteccoes.forEach(function (deteccao) {
                corpo.appendChild(criarLinhaDeteccao(deteccao));
            });
        });
    }

    function criarLinhaDeteccao(deteccao) {
        var linha = document.createElement('tr');

        var celulaHorario = document.createElement('td');
        celulaHorario.className = 'coluna-horario';
        celulaHorario.textContent = formatarHora(deteccao.dataHora);
        celulaHorario.title = formatarDataHora(deteccao.dataHora);

        var celulaCor = document.createElement('td');
        var rotulo = rotuloCor(deteccao.cor);
        var marca = document.createElement('span');
        marca.className = 'rotulo-cor rotulo-cor--' + rotulo.classe;
        marca.textContent = rotulo.texto;
        celulaCor.appendChild(marca);

        var celulaConfianca = document.createElement('td');
        celulaConfianca.className = 'coluna-confianca';
        celulaConfianca.textContent = (deteccao.confianca === null || deteccao.confianca === undefined)
            ? '—'
            : deteccao.confianca + '%';

        var celulaDispositivo = document.createElement('td');
        celulaDispositivo.textContent = deteccao.dispositivoNome || '—';
        celulaDispositivo.title = 'ID do dispositivo: ' + deteccao.dispositivoId;

        linha.appendChild(celulaHorario);
        linha.appendChild(celulaCor);
        linha.appendChild(celulaConfianca);
        linha.appendChild(celulaDispositivo);
        return linha;
    }


    // ====================================================================
    // Seletor de dispositivos
    // ====================================================================

    function atualizarDispositivos() {
        return buscarJson(ENDPOINTS.dispositivos).then(function (dispositivos) {
            var assinatura = dispositivos.map(function (item) {
                return item.id + ':' + item.codigo + ':' + item.nome;
            }).join('|');

            // Nada mudou: mantem a lista e a selecao atual do usuario.
            if (assinatura === assinaturaDispositivos) {
                return;
            }
            assinaturaDispositivos = assinatura;

            var seletor = el('seletor-dispositivo');
            var valorAnterior = idDispositivoSelecionado || seletor.value;
            seletor.textContent = '';

            if (dispositivos.length === 0) {
                var opcaoVazia = document.createElement('option');
                opcaoVazia.value = '';
                opcaoVazia.textContent = 'Nenhum dispositivo cadastrado';
                seletor.appendChild(opcaoVazia);
                idDispositivoSelecionado = '';
                return;
            }

            dispositivos.forEach(function (dispositivo) {
                var opcao = document.createElement('option');
                opcao.value = String(dispositivo.id);
                opcao.textContent = dispositivo.nome + ' — ' + dispositivo.codigo;
                seletor.appendChild(opcao);
            });

            var anteriorExiste = dispositivos.some(function (dispositivo) {
                return String(dispositivo.id) === String(valorAnterior);
            });
            idDispositivoSelecionado = anteriorExiste ? String(valorAnterior) : String(dispositivos[0].id);
            seletor.value = idDispositivoSelecionado;
        });
    }

    // ====================================================================
    // Cadastro de dispositivo (POST /api/dispositivos)
    // ====================================================================

    function cadastrarDispositivo(evento) {
        evento.preventDefault();

        var formulario = evento.target;
        var botao = el('botao-cadastrar');
        var dados = {
            nome: formulario.nome.value.trim(),
            codigo: formulario.codigo.value.trim(),
            ip: formulario.ip.value.trim()
        };

        if (!dados.nome || !dados.codigo) {
            definirMensagemFormulario('Informe o nome e o código do dispositivo.', 'erro');
            return;
        }

        botao.disabled = true;
        definirMensagemFormulario('Enviando cadastro…', null);

        fetch(ENDPOINTS.dispositivos, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify(dados)
        }).then(function (resposta) {
            if (resposta.status === 201) {
                return resposta.json().then(function (criado) {
                    formulario.reset();
                    definirMensagemFormulario('Dispositivo ' + criado.codigo + ' cadastrado com sucesso.', 'sucesso');
                    assinaturaDispositivos = '';
                    return atualizarTudo();
                });
            }
            return resposta.json()
                .catch(function () { return null; })
                .then(function (erro) {
                    definirMensagemFormulario(mensagemDeErro(erro), 'erro');
                });
        }).catch(function () {
            definirMensagemFormulario('Não foi possível conectar à API para cadastrar o dispositivo.', 'erro');
        }).finally(function () {
            botao.disabled = false;
        });
    }

    function mensagemDeErro(erro) {
        if (!erro) {
            return 'Não foi possível cadastrar o dispositivo.';
        }
        var detalhes = erro.campos
            ? Object.keys(erro.campos).map(function (campo) { return erro.campos[campo]; }).join(' ')
            : '';
        return (erro.message || 'Não foi possível cadastrar o dispositivo.') + (detalhes ? ' ' + detalhes : '');
    }


    // ====================================================================
    // Atualizacao periodica (ponto unico para futura troca por WebSocket)
    // ====================================================================

    function atualizarTudo() {
        if (atualizando) {
            return Promise.resolve();
        }
        atualizando = true;

        var falhas = [];

        return atualizarDispositivos()
            .catch(function (erro) {
                falhas.push(erro);
            })
            .then(function () {
                return Promise.allSettled([atualizarIndicadores(), atualizarRecentes()]);
            })
            .then(function (resultados) {
                resultados.forEach(function (resultado) {
                    if (resultado.status === 'rejected') {
                        falhas.push(resultado.reason);
                    }
                });

                if (falhas.length === 0) {
                    definirStatusSistema(true, 'API respondendo normalmente');
                    el('ultima-atualizacao').textContent = 'Última atualização: ' + formatarHora(new Date().toISOString());
                } else {
                    definirStatusSistema(false, falhas.map(String).join(' | '));
                    el('ultima-atualizacao').textContent = 'Falha na atualização: ' + formatarHora(new Date().toISOString());
                    console.error('IoT Sorting: falha ao atualizar a dashboard', falhas);
                }
            })
            .finally(function () {
                atualizando = false;
            });
    }

    function configurarEventos() {
        el('seletor-dispositivo').addEventListener('change', function (evento) {
            idDispositivoSelecionado = evento.target.value;
            atualizarTudo();
        });

        el('botao-atualizar').addEventListener('click', function () {
            atualizarTudo();
        });

        el('form-dispositivo').addEventListener('submit', cadastrarDispositivo);

        // Ao voltar para a aba, atualiza imediatamente em vez de esperar o ciclo.
        document.addEventListener('visibilitychange', function () {
            if (!document.hidden) {
                atualizarTudo();
            }
        });

        var redimensionamento;
        window.addEventListener('resize', function () {
            window.clearTimeout(redimensionamento);
            redimensionamento = window.setTimeout(ajustarLegenda, 250);
        });
    }

    function iniciarPolling() {
        window.clearInterval(temporizador);
        temporizador = window.setInterval(atualizarTudo, INTERVALO_ATUALIZACAO_MS);
    }

    function iniciar() {
        criarGrafico();
        configurarEventos();
        definirStatusSistema(null);
        atualizarTudo();
        iniciarPolling();
    }

    document.addEventListener('DOMContentLoaded', iniciar);
})();

