# IoT Sorting

**Sistema IoT de monitoramento e classificação de objetos em uma esteira utilizando ESP32-CAM, Java, Spring Boot e PostgreSQL.**

O ESP32-CAM detecta a chegada de um objeto (sensor IR), para a esteira, captura a imagem, identifica a cor e envia o resultado para a API Java. O backend valida os dados, persiste no PostgreSQL e disponibiliza um dashboard web em tempo real com os indicadores da operação.

> Nesta versão (V1) o backend **não recebe nem processa imagens**: o ESP32-CAM continua responsável por capturar, analisar e identificar a cor. O sistema recebe apenas o resultado (`cor`, `confiança` e `dispositivo`), o que mantém a arquitetura simples e reduz o tráfego de rede.

---

## Sobre

| Item | Descrição |
| --- | --- |
| Nome | IoT Sorting |
| Versão | V1 (executável localmente, preparada para servidor) |
| Objetivo | Monitorar e registrar a classificação de objetos em uma esteira controlada por ESP32-CAM |
| Backend | Java 21 + Spring Boot 3.5 + Spring Data JPA + Bean Validation |
| Banco de dados | PostgreSQL (Docker) |
| Dashboard | HTML5 + CSS3 + JavaScript puro + Fetch API + Chart.js (dark mode, responsiva) |

### O que a V1 entrega

1. Cadastro de dispositivos ESP32-CAM (API e formulário no dashboard);
2. Recebimento de detecções (`POST /api/objetos`) com validação de cor e confiança;
3. Registro automático de data/hora e associação da detecção ao dispositivo;
4. Monitoramento de status do dispositivo (ONLINE/OFFLINE) calculado pelo backend;
5. Endpoint de heartbeat para o ESP32 informar que está conectado;
6. Dashboard com cards, gráfico de distribuição das cores, informações do dispositivo e últimas detecções;
7. Tratamento global de erros com respostas JSON padronizadas;
8. Testes automatizados das regras de negócio, da API e das consultas do banco.

---

## Arquitetura

```text
ESP32-CAM (sensor IR + câmera + detecção de cor + relé)
        │  HTTP/JSON
        ▼
REST API (/api/objetos · /api/dispositivos · /api/dashboard)
        ▼
Spring Boot (Controller → Service → Repository)
        ▼
PostgreSQL (tabelas dispositivo e objeto)
        ▼
Dashboard (http://localhost:8080)
```

### Camadas do backend

| Camada | Pacote | Responsabilidade |
| --- | --- | --- |
| Controller | `com.iotsorting.controller` | Receber requisições, validar DTOs e devolver HTTP |
| Service | `com.iotsorting.service` | Regras de negócio (validações, status, dashboard) |
| Repository | `com.iotsorting.repository` | Acesso ao banco com Spring Data JPA (consultas derivadas) |
| Entity | `com.iotsorting.entity` | Mapeamento JPA (`Dispositivo`, `Objeto`) |
| DTO | `com.iotsorting.dto` | Contratos de entrada e saída da API (records) |
| Enums | `com.iotsorting.enums` | `Cor`, `StatusDispositivo` |
| Exception | `com.iotsorting.exception` | Exceções de domínio e tratamento global |
| Config | `com.iotsorting.config` | CORS e inicialização do dispositivo padrão |

### Fluxo de uma detecção

```text
1. Objeto chega na esteira            → sensor IR detecta
2. ESP32-CAM para a esteira e captura → identifica a cor
3. POST /api/objetos                  → { dispositivoId, cor, confianca }
4. ObjetoService                      → valida dispositivo, cor e confiança
5. dataHora registrada pelo backend   → salva no PostgreSQL
6. Dispositivo atualizado             → status ONLINE + ultimaConexao
7. HTTP 201 com a detecção criada     → dashboard mostra em até 5 segundos
```

---

## Tecnologias

**Backend**

- Java 21
- Spring Boot 3.5.16
- Spring Web (API REST)
- Spring Data JPA / Hibernate
- Bean Validation (Jakarta Validation)
- PostgreSQL 16
- Maven (com Maven Wrapper)
- Logback (logs da aplicação)
- JUnit 5, Mockito, AssertJ e MockMvc (testes)

**Frontend**

- HTML5 semântico
- CSS3 (variáveis, grid, dark mode, responsivo)
- JavaScript puro (ES5 compatível, Fetch API)
- Chart.js 4.4.7 (arquivo local em `src/main/resources/static/js/vendor/chart.umd.min.js`, sem dependência de CDN)

**Infraestrutura**

- Docker + Docker Compose (somente o PostgreSQL)
- O backend roda direto na JVM (`mvnw spring-boot:run`), sem container nesta versão

> Lombok não foi utilizado: o projeto mantém o código explícito (getters/setters nas entidades, records nos DTOs) para reduzir dependências e funcionar sem processamento de anotações.

---

## Requisitos

| Ferramenta | Versão mínima | Observação |
| --- | --- | --- |
| Java (JDK) | 21 | `java -version` deve mostrar 21 ou superior |
| Maven | 3.9 | Opcional: o projeto inclui o Maven Wrapper (`mvnw` / `mvnw.cmd`) |
| Docker | 20+ | Necessário para subir o PostgreSQL |
| Docker Compose | v2 | Utilizado via `docker compose` |
| PostgreSQL | 16 | Fornecido pelo Docker Compose |

---

## Instalação

### 1. Clonar/abrir o projeto

```bash
cd iot-sorting
```

### 2. Subir o PostgreSQL

```bash
docker compose up -d
```

O `docker-compose.yml` cria o banco `iot_sorting` (usuário `postgres`, senha `postgres`, porta `5432`) com volume persistente `iot_sorting_data`.

Para conferir se o banco está saudável:

```bash
docker compose ps
```

Para parar (mantendo os dados):

```bash
docker compose down
```

Para apagar também os dados:

```bash
docker compose down -v
```


### 3. Rodar o backend

Linux / macOS:

```bash
./mvnw spring-boot:run
```

Windows (Prompt de Comando ou PowerShell):

```bat
mvnw.cmd spring-boot:run
```

A aplicação fica disponível em `http://localhost:8080`.

Na primeira execução o backend:

1. cria as tabelas (`spring.jpa.hibernate.ddl-auto=update`);
2. cria o dispositivo de exemplo **Esteira Principal / ESP32CAM-001 / 192.168.1.100** (sem duplicar registros nas próximas execuções).

Para desativar a criação automática do dispositivo de exemplo:

```properties
iot.sorting.inicializacao.criar-dispositivo-padrao=false
```

### 4. Rodar os testes

```bash
./mvnw test
```

```bat
mvnw.cmd test
```

Os testes usam banco H2 em memória e não precisam do PostgreSQL nem do Docker.

### 5. Gerar o pacote executável (opcional)

```bash
./mvnw clean package
java -jar target/iot-sorting-1.0.0.jar
```

---

## Configuração

Arquivo: `src/main/resources/application.properties`

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/iot_sorting
spring.datasource.username=postgres
spring.datasource.password=postgres

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

server.port=8080

# Tempo sem comunicação para o dispositivo ficar OFFLINE
iot.sorting.dispositivo.tempo-limite-offline-segundos=60

# Intervalo da verificação automática de status
iot.sorting.monitor.intervalo-verificacao-ms=5000

# Cria o dispositivo de exemplo na primeira execução
iot.sorting.inicializacao.criar-dispositivo-padrao=true

# Origens liberadas para um frontend separado (vazio = mesma origem)
iot.sorting.cors.origens-permitidas=
```

Para apontar para outro banco sem alterar o arquivo:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://localhost:5433/iot_sorting"
```

---

## Dashboard

Acesse **http://localhost:8080** (a página é servida pela própria aplicação Spring Boot a partir de `src/main/resources/static`).

A dashboard possui:

- **Header** com o nome do sistema, o subtítulo, o indicador `● SISTEMA ONLINE` (reflete se a API está respondendo) e o seletor de dispositivo;
- **Cards**: Objetos hoje, Vermelhos, Verdes, Azuis, Estado da esteira e Estado do ESP32;
- **Gráfico** de distribuição das cores (Chart.js) alimentado por `GET /api/dashboard`;
- **Painel do dispositivo** com nome, código, IP, status, última conexão, última detecção e total de dispositivos cadastrados;
- **Formulário** para cadastrar novos dispositivos;
- **Tabela** das últimas detecções (`Horário · Cor · Confiança · Dispositivo`).

A atualização é feita por **polling a cada 5 segundos** (`GET /api/dashboard` e `GET /api/objetos/recentes`). Nenhum número é fixado no JavaScript: todos os valores vêm da API. O código concentra a atualização na função `atualizarTudo()` do arquivo `dashboard.js`, o que permite trocar o polling por WebSocket no futuro sem reescrever a tela.

Detalhes de acessibilidade e responsividade:

- status indicados por **símbolo + texto** (`● ONLINE`, `■ OFFLINE`), sem depender apenas de cor;
- tabela com `caption`, `scope="col"` e rolagem horizontal em telas pequenas;
- gráfico com descrição textual equivalente (resumo das cores);
- cards, painéis e header se reorganizam em desktop, tablet e celular;
- suporte a `prefers-reduced-motion` e foco visível para navegação por teclado.

---

## API

Base: `/api` · Formato: `application/json` · Sem autenticação na V1.

### Objetos (detecções)

| Método | Endpoint | Descrição | Respostas |
| --- | --- | --- | --- |
| `POST` | `/api/objetos` | Registra uma detecção enviada pelo ESP32-CAM | `201`, `400`, `404` |
| `GET` | `/api/objetos` | Lista todas as detecções (mais recentes primeiro) | `200` |
| `GET` | `/api/objetos?cor=VERMELHO` | Lista as detecções filtrando por cor | `200`, `400` |
| `GET` | `/api/objetos/{id}` | Detalhe de uma detecção | `200`, `404` |
| `GET` | `/api/objetos/recentes?limite=10&dispositivoId=1` | Últimas detecções (padrão 10, máximo 100) | `200`, `400` |

**Requisição**

```http
POST /api/objetos
Content-Type: application/json

{
  "dispositivoId": 1,
  "cor": "VERMELHO",
  "confianca": 92
}
```

**Resposta `201 Created`** (com cabeçalho `Location: /api/objetos/15`)

```json
{
  "id": 15,
  "dispositivoId": 1,
  "dispositivoNome": "Esteira Principal",
  "cor": "VERMELHO",
  "confianca": 92,
  "dataHora": "2026-09-24T22:51:32"
}
```

Ao receber uma detecção o backend: valida o dispositivo, valida cor e confiança (0 a 100), registra `dataHora`, salva no PostgreSQL, atualiza `ultimaConexao` e coloca o dispositivo como `ONLINE`.

### Dispositivos

| Método | Endpoint | Descrição | Respostas |
| --- | --- | --- | --- |
| `GET` | `/api/dispositivos` | Lista os dispositivos cadastrados | `200` |
| `GET` | `/api/dispositivos/{id}` | Detalhe do dispositivo | `200`, `404` |
| `POST` | `/api/dispositivos` | Cadastra um novo ESP32-CAM | `201`, `400`, `409` |
| `POST` | `/api/dispositivos/{id}/heartbeat` | ESP32 informa que está conectado | `200`, `400`, `404` |

**Cadastro**

```http
POST /api/dispositivos
Content-Type: application/json

{
  "nome": "Esteira Principal",
  "codigo": "ESP32CAM-001",
  "ip": "192.168.1.25"
}
```

**Heartbeat** (corpo opcional)

```http
POST /api/dispositivos/1/heartbeat
Content-Type: application/json

{
  "ip": "192.168.1.25",
  "esteiraLigada": true
}
```

```json
{
  "id": 1,
  "nome": "Esteira Principal",
  "codigo": "ESP32CAM-001",
  "ip": "192.168.1.25",
  "status": "ONLINE",
  "ultimaConexao": "2026-09-24T22:55:10",
  "criadoEm": "2026-09-24T20:00:00"
}
```

- `ip` é opcional: quando não informado o backend registra o IP de origem da requisição (considerando `X-Forwarded-For`);
- `esteiraLigada` é opcional e existe para o firmware informar o estado real do relé da esteira.

### Dashboard

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `GET` | `/api/dashboard` | Resumo do dia para a dashboard |
| `GET` | `/api/dashboard?dispositivoId=1` | Mesmo resumo, filtrado por dispositivo |

```json
{
  "objetosHoje": 147,
  "vermelhos": 52,
  "verdes": 38,
  "azuis": 41,
  "amarelos": 16,
  "brancos": 5,
  "pretos": 4,
  "indefinidos": 7,
  "dispositivoOnline": true,
  "esteiraLigada": true,
  "ultimaDeteccao": "2026-09-24T22:51:32",
  "totalDispositivos": 1,
  "dispositivo": { "id": 1, "nome": "Esteira Principal", "codigo": "ESP32CAM-001" }
}
```

Sem o parâmetro `dispositivoId` os totais consideram todos os dispositivos e os indicadores de status representam o conjunto (basta um dispositivo online).

### Erros

Todos os erros usam o mesmo formato JSON (sem stack trace):

```json
{
  "timestamp": "2026-09-24T22:50:00",
  "status": 400,
  "message": "A confiança deve estar entre 0 e 100",
  "campos": {
    "confianca": "A confiança deve estar entre 0 e 100"
  }
}
```

| Status | Quando acontece |
| --- | --- |
| `400` | Validação de campos, cor inexistente, JSON inválido, limite inválido |
| `404` | Dispositivo ou objeto não encontrado |
| `405` / `415` | Método HTTP ou `Content-Type` não suportado |
| `409` | Código de dispositivo duplicado |
| `500` | Erro inesperado (detalhes ficam apenas no log da aplicação) |

### CORS

O dashboard é servido pela própria aplicação, então CORS fica desabilitado por padrão. Para um frontend separado, informe as origens permitidas:

```properties
iot.sorting.cors.origens-permitidas=http://localhost:3000,http://192.168.1.50
```

---

## Status dos dispositivos

O status **não depende de um campo enviado pelo ESP32**:

| Situação | Comportamento |
| --- | --- |
| Recebeu detecção ou heartbeat | `status = ONLINE` e `ultimaConexao = agora` |
| Mais de 60 segundos sem comunicação | `status = OFFLINE` (verificação automática a cada 5 segundos via `@Scheduled`) |
| Sem comunicação | a esteira é exibida como `DESLIGADA`, pois o backend não consegue confirmar o estado do relé |

O limite de 60 segundos e o intervalo da verificação são configuráveis em `application.properties` (`iot.sorting.dispositivo.tempo-limite-offline-segundos` e `iot.sorting.monitor.intervalo-verificacao-ms`).

### Estado da esteira (V1)

O card **Estado da esteira** usa o campo `esteiraLigada` do dispositivo:

- o heartbeat pode informá-lo (`"esteiraLigada": true|false`);
- quando não informado, o backend considera a esteira ligada ao receber comunicação (heartbeat ou detecção), pois se houve objeto na esteira o motor estava em operação;
- quando o dispositivo fica OFFLINE, o estado passa para desligado por não haver confirmação.

Essa estrutura já está pronta para o firmware reportar o estado real do relé em uma próxima versão.


---

## Exemplo ESP32

O ESP32-CAM precisa conhecer o **ID do dispositivo** cadastrado (`GET /api/dispositivos` retorna o `id` de cada `codigo`). O firmware atual pode ser adaptado para enviar a detecção logo após identificar a cor.

**Detecção de objeto**

```json
{
  "dispositivoId": 1,
  "cor": "VERMELHO",
  "confianca": 92
}
```

**Heartbeat (a cada 30 segundos, por exemplo)**

```json
{
  "ip": "192.168.1.25",
  "esteiraLigada": true
}
```

**Teste rápido via linha de comando**

```bash
curl -X POST http://localhost:8080/api/objetos \
  -H "Content-Type: application/json" \
  -d "{\"dispositivoId\":1,\"cor\":\"VERMELHO\",\"confianca\":92}"

curl -X POST http://localhost:8080/api/dispositivos/1/heartbeat \
  -H "Content-Type: application/json" \
  -d "{\"ip\":\"192.168.1.25\",\"esteiraLigada\":true}"
```

Valores aceitos em `cor`: `VERMELHO`, `VERDE`, `AZUL`, `AMARELO`, `BRANCO`, `PRETO`, `INDEFINIDO` (a comparação ignora maiúsculas/minúsculas). `confianca` deve estar entre `0` e `100`.

---

## Estrutura do projeto

```text
iot-sorting/
├── .mvn/wrapper/maven-wrapper.properties
├── docker-compose.yml
├── mvnw
├── mvnw.cmd
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/iotsorting/
    │   │   ├── IoTSortingApplication.java
    │   │   ├── config/
    │   │   │   ├── CorsConfig.java
    │   │   │   └── InicializacaoDados.java
    │   │   ├── controller/
    │   │   │   ├── DashboardController.java
    │   │   │   ├── DispositivoController.java
    │   │   │   └── ObjetoController.java
    │   │   ├── dto/
    │   │   │   ├── DashboardResponse.java
    │   │   │   ├── DispositivoRequest.java
    │   │   │   ├── DispositivoResponse.java
    │   │   │   ├── ErroResponse.java
    │   │   │   ├── HeartbeatRequest.java
    │   │   │   ├── ObjetoRequest.java
    │   │   │   └── ObjetoResponse.java
    │   │   ├── entity/
    │   │   │   ├── Dispositivo.java
    │   │   │   └── Objeto.java
    │   │   ├── enums/
    │   │   │   ├── Cor.java
    │   │   │   └── StatusDispositivo.java
    │   │   ├── exception/
    │   │   │   ├── ConflitoException.java
    │   │   │   ├── DadosInvalidosException.java
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── RecursoNaoEncontradoException.java
    │   │   ├── repository/
    │   │   │   ├── DispositivoRepository.java
    │   │   │   └── ObjetoRepository.java
    │   │   └── service/
    │   │       ├── DashboardService.java
    │   │       ├── DispositivoService.java
    │   │       ├── MonitorDispositivosService.java
    │   │       └── ObjetoService.java
    │   └── resources/
    │       ├── application.properties
    │       └── static/
    │           ├── index.html
    │           ├── css/style.css
    │           └── js/
    │               ├── dashboard.js
    │               └── vendor/chart.umd.min.js
    └── test/java/com/iotsorting/
        ├── IoTSortingApplicationTests.java
        ├── controller/ObjetoControllerTest.java
        ├── repository/ObjetoRepositoryTest.java
        └── service/
            ├── DispositivoServiceTest.java
            └── ObjetoServiceTest.java
```


---

## Banco de dados

```text
dispositivo
├── id              bigint (PK)
├── nome            varchar(120)  not null
├── codigo          varchar(60)   not null (único)
├── ip              varchar(45)
├── status          varchar(20)   not null  -- ONLINE | OFFLINE
├── ultima_conexao  timestamp
├── esteira_ligada  boolean
└── criado_em       timestamp     not null

objeto
├── id              bigint (PK)
├── dispositivo_id  bigint (FK → dispositivo.id) not null
├── cor             varchar(20)   not null  -- VERMELHO ... INDEFINIDO
├── confianca       integer       not null  -- 0 a 100
└── data_hora       timestamp     not null  (índice: idx_objeto_data_hora)
```

As tabelas são criadas/atualizadas automaticamente pelo Hibernate (`ddl-auto=update`).

> Em modo `update` o Hibernate pode registrar avisos ao sincronizar o schema em bancos já existentes (por exemplo, sobre a restrição única `uk_dispositivo_codigo`). São avisos inofensivos: a restrição permanece criada no banco.

---

## Testes

| Arquivo | Cobre |
| --- | --- |
| `ObjetoServiceTest` | Criação da detecção, dispositivo inexistente, confiança inválida, cor ausente, limite de recentes, conversão para DTO |
| `DispositivoServiceTest` | Atualização do dispositivo na detecção, heartbeat com IP e relé, cálculo de ONLINE/OFFLINE (60 s), verificação automática, código duplicado, cadastro |
| `ObjetoControllerTest` | `POST` válido (201), validações (400), cor inexistente, `GET` com e sem filtro, `GET /recentes`, 404 em JSON |
| `ObjetoRepositoryTest` | Consultas derivadas da dashboard e do histórico (H2) |
| `IoTSortingApplicationTests` | Subida da aplicação, dispositivo padrão sem duplicação, fluxo detecção → dashboard, arquivos estáticos |

---

## Próximos passos

- **Autenticação e autorização** com Spring Security (as camadas já estão separadas para receber os filtros);
- **Múltiplos usuários** e perfis de acesso (operador, supervisor, administrador);
- **Múltiplos dispositivos** com visão consolidada da linha de produção (a API já aceita `dispositivoId`);
- **WebSocket** para atualização em tempo real no lugar do polling (substitui apenas a chamada de `atualizarTudo()`);
- **MQTT** para comunicação com o ESP32 em redes com muitos dispositivos;
- **Armazenamento de imagens** das detecções (disco local ou MinIO/S3) com vínculo ao registro do objeto;
- **IA / visão computacional** no backend para validar ou substituir a classificação feita no ESP32;
- **Relatórios** por período (CSV/PDF) e exportação do histórico;
- **Alertas** de dispositivo offline (e-mail/Telegram) e de baixa confiança de detecção;
- **Deploy em nuvem** com Docker (incluindo o backend), credenciais por variáveis de ambiente e backup do banco.

---

## Observações da V1

- O backend **não recebe imagens**: apenas o resultado da classificação (`cor`, `confiança`, `dispositivo`);
- Não há autenticação, login, JWT ou permissões nesta versão;
- O frontend é JavaScript puro (sem React, Angular, Vue ou Next.js), com Chart.js servido localmente (sem CDN);
- O backend roda direto na JVM nesta versão (não é dockerizado); apenas o PostgreSQL usa Docker.

