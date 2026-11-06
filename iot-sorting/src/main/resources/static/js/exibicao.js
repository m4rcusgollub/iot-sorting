/* exibicao.js — dashboard de exibição (somente leitura). ES5. Chart.js local. */
(function () {
  'use strict';

  /* Configuração (não são valores exibidos) */
  var INTERVALO = 5000;     // ms entre consultas — igual ao dashboard.js
  var TEMPO_REALCE = 8000;  // ms de realce da detecção nova
  var LIMITE = 8;           // linhas pedidas a /api/objetos/recentes
  var FUSO = 'America/Sao_Paulo';
  /* Nomes de campo aceitos no JSON, em ordem. Ajuste aqui se a API usar outros. */
  var CAMPOS = {
    hoje: ['objetosHoje', 'totalHoje', 'hoje'],
    geral: ['totalGeral', 'totalObjetos', 'total'],
    esteira: ['esteiraLigada', 'esteira', 'esteiraAtiva'],
    esp32: ['dispositivoOnline', 'esp32Online', 'esp32'],
    id: ['id'],
    hora: ['dataHora', 'horario', 'timestamp', 'criadoEm'],
    cor: ['cor'],
    conf: ['confianca', 'probabilidade'],
    disp: ['dispositivo', 'dispositivoNome', 'dispositivoId', 'deviceId']
  };
  /* GET /api/dashboard devolve um total por cor em campos separados (DashboardResponse):
     objetosHoje, vermelhos, verdes, azuis, amarelos, brancos, pretos, indefinidos. */
  var CORES_API = {
    VERMELHO: ['vermelhos', 'totalVermelho'],
    VERDE: ['verdes', 'totalVerde'],
    AZUL: ['azuis', 'totalAzul'],
    AMARELO: ['amarelos', 'totalAmarelo'],
    BRANCO: ['brancos', 'totalBranco'],
    PRETO: ['pretos', 'totalPreto'],
    INDEFINIDO: ['indefinidos', 'totalIndefinido']
  };

  var cores = [], valores = {}, grafico = null;
  var ultimaLista = [], ultimaChave = null, carregou = false;
  var realceChave = null, realceAte = 0, tRealce = null, emCurso = false, temDados = false;
  var reduzido = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);

  function el(id) { return document.getElementById(id); }
  function css(nome) { return getComputedStyle(document.documentElement).getPropertyValue(nome).trim(); }
  function hora(d) { return d.toLocaleTimeString('pt-BR', { timeZone: FUSO, hour12: false }); }
  function num(v) { return (v === undefined || v === null || isNaN(Number(v))) ? '—' : Number(v).toLocaleString('pt-BR'); }
  /* Número que sobe suave quando aumenta (o painel de contagem). Respeita prefers-reduced-motion. */
  function escreverNumero(destino, v) {
    var alvo = (v === undefined || v === null || isNaN(Number(v))) ? null : Number(v);
    var de = destino._exAtual === undefined ? null : destino._exAtual;
    if (destino._exAnim) { cancelAnimationFrame(destino._exAnim); destino._exAnim = null; }
    if (alvo === null || reduzido || de === null || alvo <= de) {
      destino._exAtual = alvo;
      destino.textContent = alvo === null ? '—' : alvo.toLocaleString('pt-BR');
      return;
    }
    var inicio = performance.now(), DUR = 700;
    var passo = function (t) {
      var k = Math.min(1, (t - inicio) / DUR);
      var n = Math.round(de + (alvo - de) * (k === 1 ? 1 : 1 - Math.pow(1 - k, 3)));
      destino.textContent = n.toLocaleString('pt-BR');
      if (k < 1) { destino._exAnim = requestAnimationFrame(passo); } else { destino._exAtual = alvo; }
    };
    destino._exAnim = requestAnimationFrame(passo);
  }
  function achar(o, chaves) {
    var i, v;
    if (!o || typeof o !== 'object') { return undefined; }
    for (i = 0; i < chaves.length; i++) { v = o[chaves[i]]; if (v !== undefined && v !== null) { return v; } }
    return undefined;
  }
  function ativo(v) {
    if (v && typeof v === 'object') { v = achar(v, ['ativa', 'ativo', 'online', 'estado', 'status']); }
    if (typeof v === 'boolean') { return v; }
    if (v === undefined || v === null) { return null; }
    return /^(ativ|online|ligad|rodando|conectad|true)/i.test(String(v));
  }
  function lerCoresDashboard(d) {
    var mapa = {}, nome, i, k, v;
    for (nome in CORES_API) {
      if (CORES_API.hasOwnProperty(nome)) {
        for (k = 0; k < CORES_API[nome].length; k++) {
          v = achar(d, [CORES_API[nome][k]]);
          if (v !== undefined && v !== null) { mapa[nome] = v; break; }
        }
      }
    }
    return mapa;
  }

  /* ---------- Mensagens visíveis ---------- */
  function conexao(classe, texto) { var e = el('conexao'); e.className = 'ex-estado ' + classe; e.textContent = texto; }
  function detalhe(t) { el('detalhe').textContent = t; }
  function avisoGrafico(t) { var m = el('msg-grafico'); m.textContent = t; m.hidden = false; }
  window.onerror = function (msg) { detalhe('■ Erro na tela: ' + msg); };

  /* ---------- Relógio ---------- */
  function dataHoje() {
    var s = new Date().toLocaleDateString('pt-BR', { timeZone: FUSO, weekday: 'long', year: 'numeric', day: 'numeric', month: 'long' });
    return s.charAt(0).toUpperCase() + s.slice(1);
  }
  function tique() {
    var d = new Date(), r = el('rotulo-data'), data;
    el('relogio').textContent = hora(d);
    if (r) {
      data = dataHoje();
      if (r.textContent !== data) { r.textContent = data; }
    }
  }

  /* ---------- Dashboard ---------- */
  function estadoTexto(e, v, sim, nao) {
    if (v === null) { e.textContent = '—'; e.className = 'ex-valor ex-valor--estado'; return; }
    var r = v ? sim : nao;
    e.textContent = r[0];
    e.className = 'ex-valor ex-valor--estado ' + r[1];
  }
  function desenharGrafico(mapa) {
    var dados = [], txt = [], i, v;
    if (!grafico) { return; }
    for (i = 0; i < cores.length; i++) {
      v = mapa[cores[i]];
      dados.push(v === undefined ? null : Number(v));
      txt.push(cores[i] + ' ' + (v === undefined ? 'sem dado' : v));
    }
    grafico.data.datasets[0].data = dados;
    grafico.update('none');
    el('grafico').setAttribute('aria-label', 'Barras horizontais, objetos de hoje por cor: ' + txt.join(', '));
  }
  function mostrarDashboard(d) {
    var mapa = lerCoresDashboard(d), soma = 0, temCor = false, i, v, hoje, geral;
    for (i = 0; i < cores.length; i++) {
      v = mapa[cores[i]];
      escreverNumero(valores[cores[i]], v);
      if (v !== undefined) { soma += Number(v); temCor = true; }
    }
    /* INDEFINIDO não tem painel na faixa, mas entra no total geral. */
    v = achar(d, CORES_API.INDEFINIDO);
    if (v !== undefined) { soma += Number(v); temCor = true; }
    hoje = achar(d, CAMPOS.hoje);
    if (hoje === undefined && temCor) { hoje = soma; }
    escreverNumero(el('v-hoje'), hoje);
    /* A API nao tem "total geral": calcula pela soma das cores quando o campo abre. */
    geral = achar(d, CAMPOS.geral);
    if (geral === undefined && temCor) { geral = soma; }
    escreverNumero(el('v-geral'), geral);
    estadoTexto(el('v-esteira'), ativo(achar(d, CAMPOS.esteira)), ['● ATIVA', 'ex-ok'], ['■ PARADA', 'ex-espera']);
    estadoTexto(el('v-esp32'), ativo(achar(d, CAMPOS.esp32)), ['● ONLINE', 'ex-ok'], ['■ OFFLINE', 'ex-erro']);
    desenharGrafico(mapa);
  }

  /* ---------- Últimas detecções ---------- */
  function dispDe(o) {
    var d = achar(o, CAMPOS.disp);
    if (d && typeof d === 'object') { d = achar(d, ['nome', 'identificador', 'id']); }
    return d === undefined ? '—' : String(d);
  }
  function horaDe(v) {
    if (v === undefined) { return '—'; }
    var d = new Date(typeof v === 'string' ? v.replace(' ', 'T') : v);
    return isNaN(d.getTime()) ? String(v) : hora(d);
  }
  function pct(v) {
    if (v === undefined) { return '—'; }
    var n = Number(v);
    if (isNaN(n)) { return String(v); }
    return Math.round(n <= 1 ? n * 100 : n) + '%'; // aceita 0–1 ou 0–100
  }
  function chaveDe(o) {
    var id = achar(o, CAMPOS.id);
    return id !== undefined ? 'id:' + id : [achar(o, CAMPOS.hora), achar(o, CAMPOS.cor), dispDe(o)].join('|');
  }
  function td(tr, txt) { var c = document.createElement('td'); c.textContent = txt; tr.appendChild(c); return c; }
  function linhaMensagem(tb, txt) {
    var tr = document.createElement('tr'), c = td(tr, txt);
    c.colSpan = 4;
    tb.appendChild(tr);
  }
  function desenharLista() {
    var tb = el('linhas'), i, o, tr, c, cor, amostra, nome, selo;
    while (tb.firstChild) { tb.removeChild(tb.firstChild); }
    if (!ultimaLista.length) { linhaMensagem(tb, 'Nenhuma detecção registrada ainda.'); return; }
    for (i = 0; i < ultimaLista.length; i++) {
      o = ultimaLista[i];
      tr = document.createElement('tr');
      c = td(tr, horaDe(achar(o, CAMPOS.hora)));
      if (chaveDe(o) === realceChave && Date.now() < realceAte) {
        tr.className = 'nova';
        selo = document.createElement('span');
        selo.className = 'ex-novo';
        selo.textContent = '▶ Novo';
        c.appendChild(selo);
      }
      cor = String(achar(o, CAMPOS.cor) === undefined ? '—' : achar(o, CAMPOS.cor)).toUpperCase();
      c = document.createElement('td');
      nome = document.createElement('span');
      nome.className = 'c-cor';
      amostra = document.createElement('i');
      amostra.className = 'ex-amostra';
      amostra.style.background = 'var(--ex-' + cor.toLowerCase() + ')';
      nome.appendChild(amostra);
      nome.appendChild(document.createTextNode(cor));
      c.appendChild(nome);
      tr.appendChild(c);
      td(tr, pct(achar(o, CAMPOS.conf)));
      td(tr, dispDe(o));
      tb.appendChild(tr);
    }
  }
  function mostrarRecentes(r) {
    var lista = Array.isArray(r) ? r : (achar(r, ['objetos', 'itens', 'content', 'dados']) || []);
    var topo = lista.length ? chaveDe(lista[0]) : null;
    if (carregou && topo !== null && topo !== ultimaChave) {
      realceChave = topo;
      realceAte = Date.now() + TEMPO_REALCE;
      clearTimeout(tRealce);
      tRealce = setTimeout(desenharLista, TEMPO_REALCE + 50);
      el('anuncio').textContent = 'Nova detecção: ' + String(achar(lista[0], CAMPOS.cor)) + ', ' + pct(achar(lista[0], CAMPOS.conf));
    }
    ultimaChave = topo;
    carregou = true;
    ultimaLista = lista;
    desenharLista();
  }

  /* ---------- Requisições ---------- */
  function pegar(url, ok, falha) {
    var x = new XMLHttpRequest();
    x.open('GET', url, true);
    x.timeout = INTERVALO;
    x.onload = function () {
      if (x.status < 200 || x.status >= 300) { falha('HTTP ' + x.status); return; }
      try { ok(JSON.parse(x.responseText)); } catch (e) { falha('resposta inválida (' + e.message + ')'); }
    };
    x.onerror = function () { falha('sem conexão com o servidor'); };
    x.ontimeout = function () { falha('tempo esgotado'); };
    x.send();
  }
  function atualizarTudo() {
    var pendentes = 2, erros = [];
    if (emCurso) { return; }
    emCurso = true;
    function fim() {
      pendentes -= 1;
      if (pendentes > 0) { return; }
      emCurso = false;
      if (erros.length) {
        conexao('ex-erro', '■ OFFLINE — aguardando dados');
        detalhe(erros.join('; ') + ' — nova tentativa automática');
        document.body.setAttribute('data-offline', '');
        if (!temDados) { var tb = el('linhas'); while (tb.firstChild) { tb.removeChild(tb.firstChild); } linhaMensagem(tb, '■ OFFLINE — aguardando dados'); }
      } else {
        temDados = true;
        conexao('ex-ok', '● ONLINE');
        detalhe('Conexão com o servidor');
        el('atualizado').textContent = 'Última atualização às ' + hora(new Date());
        document.body.removeAttribute('data-offline');
      }
    }
    function falhou(onde) { return function (m) { erros.push(onde + ': ' + m); fim(); }; }
    pegar('/api/dashboard', function (d) { mostrarDashboard(d); fim(); }, falhou('dashboard'));
    pegar('/api/objetos/recentes?limite=' + LIMITE, function (r) { mostrarRecentes(r); fim(); }, falhou('detecções'));
  }

  /* ---------- Início ---------- */
  function iniciar() {
    var blocos = document.querySelectorAll('[data-cor]'), i, fundos = [], cv, estilo, op;
    for (i = 0; i < blocos.length; i++) {
      cores.push(blocos[i].getAttribute('data-cor'));
      valores[cores[i]] = blocos[i].querySelector('.ex-valor');
    }
    if (typeof Chart === 'undefined') {
      avisoGrafico('■ Gráfico indisponível — js/vendor/chart.umd.min.js não carregou.');
    } else {
      try {
        cv = el('grafico');
        estilo = getComputedStyle(cv.parentNode);
        Chart.defaults.font.size = parseFloat(estilo.fontSize);
        Chart.defaults.font.family = estilo.fontFamily;
        Chart.defaults.color = css('--ex-texto');
        for (i = 0; i < cores.length; i++) { fundos.push(css('--ex-' + cores[i].toLowerCase())); }
        op = {
          indexAxis: 'y', responsive: true, maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: {
            x: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: css('--ex-borda') } },
            y: { grid: { display: false } }
          }
        };
        if (reduzido) { op.animation = false; }
        grafico = new Chart(cv, { type: 'bar', data: { labels: cores, datasets: [{ data: [], backgroundColor: fundos, borderColor: css('--ex-suave'), borderWidth: 2, borderRadius: 6, maxBarThickness: 44 }] }, options: op });
      } catch (e) { avisoGrafico('■ Erro ao criar o gráfico: ' + e.message); }
    }
    tique();
    setInterval(tique, 1000);
    atualizarTudo();
    setInterval(atualizarTudo, INTERVALO);
  }

  if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', iniciar); } else { iniciar(); }
})();
