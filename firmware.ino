#include "esp_camera.h"
#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>

// =====================================================
// PINOS
// =====================================================

#define TRIGGER_PIN 13   // Sensor IR
#define RELE_PIN    15   // IN do módulo relé

// Relé normalmente acionado com LOW
#define RELE_LIGA    LOW
#define RELE_DESLIGA HIGH


// =====================================================
// WI-FI
// =====================================================

// Rede de configuração criada pelo ESP32
const char* AP_SSID = "ESP32-CAM-Config";
const char* AP_PASSWORD = "12345678";

// Servidor web interno para configuração
WebServer server(80);

// Memória interna para salvar SSID e senha
Preferences preferences;

String wifiSSID = "";
String wifiPassword = "";


// =====================================================
// SERVIDOR NA NUVEM (RENDER)
// =====================================================

const char* serverURL = "https://iot-sorting.onrender.com";

// ID do dispositivo no banco do Render (1 = Esteira Principal)
#define DISPOSITIVO_ID 1

// Heartbeat a cada 20 segundos
#define HEARTBEAT_INTERVALO 20000UL

unsigned long ultimoHeartbeat = 0;


// =====================================================
// PINAGEM DA CÂMERA AI-THINKER
// =====================================================

#define PWDN_GPIO_NUM  32
#define RESET_GPIO_NUM -1
#define XCLK_GPIO_NUM  0
#define SIOD_GPIO_NUM  26
#define SIOC_GPIO_NUM  27

#define Y9_GPIO_NUM    35
#define Y8_GPIO_NUM    34
#define Y7_GPIO_NUM    39
#define Y6_GPIO_NUM    36
#define Y5_GPIO_NUM    21
#define Y4_GPIO_NUM    19
#define Y3_GPIO_NUM    18
#define Y2_GPIO_NUM    5

#define VSYNC_GPIO_NUM 25
#define HREF_GPIO_NUM  23
#define PCLK_GPIO_NUM  22

bool releLigado = false;


// =====================================================
// INICIAR CÂMERA
// =====================================================

void iniciarCamera() {

  camera_config_t config;

  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;

  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;

  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;

  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;

  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;

  config.xclk_freq_hz = 20000000;

  config.pixel_format = PIXFORMAT_RGB565;

  config.frame_size = FRAMESIZE_QVGA;
  config.jpeg_quality = 12;
  config.fb_count = 1;

  esp_err_t err = esp_camera_init(&config);

  if (err != ESP_OK) {
    Serial.printf("Falha ao iniciar camera: 0x%x\n", err);
    return;
  }

  Serial.println("Camera iniciada com sucesso!");
}


// =====================================================
// CONTROLE DO RELÉ
// =====================================================

void ligarRele() {
  digitalWrite(RELE_PIN, RELE_LIGA);
  releLigado = true;
  Serial.println("RELE: LIGADO");
}

void desligarRele() {
  digitalWrite(RELE_PIN, RELE_DESLIGA);
  releLigado = false;
  Serial.println("RELE: DESLIGADO");
}


// =====================================================
// PÁGINA DE CONFIGURAÇÃO WI-FI
// =====================================================

void paginaConfig() {

  String pagina = R"rawliteral(
<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>ESP32-CAM - Wi-Fi</title>
<style>
body { font-family: Arial, sans-serif; background: #111827; color: white; margin: 0; padding: 20px; }
.container { max-width: 420px; margin: auto; background: #1f2937; padding: 25px; border-radius: 15px; }
h1 { margin-top: 0; }
p { color: #d1d5db; }
label { display: block; margin-top: 18px; margin-bottom: 6px; }
input { width: 100%; padding: 13px; box-sizing: border-box; border-radius: 8px; border: none; font-size: 16px; }
button { width: 100%; padding: 14px; margin-top: 25px; border: none; border-radius: 8px; background: #7c3aed; color: white; font-size: 16px; font-weight: bold; }
.info { margin-top: 20px; padding: 12px; background: #374151; border-radius: 8px; font-size: 14px; }
</style>
</head>
<body>
<div class="container">
<h1>ESP32-CAM</h1>
<p>Configuração da rede Wi-Fi</p>
<form action="/salvar" method="POST">
<label>Nome da rede Wi-Fi</label>
<input type="text" name="ssid" placeholder="Ex: MinhaInternet" required>
<label>Senha do Wi-Fi</label>
<input type="password" name="password" placeholder="Senha do Wi-Fi">
<button type="submit">Salvar Wi-Fi</button>
</form>
<div class="info">
<b>Rede de configuração:</b> ESP32-CAM-Config<br><br>
<b>IP de acesso:</b> 192.168.4.1
</div>
</div>
</body>
</html>
)rawliteral";

  server.send(200, "text/html", pagina);
}

void salvarWiFi() {
  if (!server.hasArg("ssid")) {
    server.send(400, "text/plain", "SSID nao informado");
    return;
  }

  String novoSSID = server.arg("ssid");
  String novaSenha = server.arg("password");

  Serial.println("\nSALVANDO NOVO WI-FI...");
  Serial.print("SSID: "); Serial.println(novoSSID);

  preferences.begin("wifi", false);
  preferences.putString("ssid", novoSSID);
  preferences.putString("password", novaSenha);
  preferences.end();

  String resposta = "<h2 style='text-align:center;'>Wi-Fi salvo! Reiniciando em 2 segundos...</h2>";
  server.send(200, "text/html", resposta);

  delay(2000);
  ESP.restart();
}


// =====================================================
// CONEXÃO WI-FI
// =====================================================

bool conectarWiFi() {
  preferences.begin("wifi", true);
  wifiSSID = preferences.getString("ssid", "");
  wifiPassword = preferences.getString("password", "");
  preferences.end();

  if (wifiSSID == "") {
    Serial.println("\nNenhum Wi-Fi configurado.");
    return false;
  }

  Serial.println("\nCONECTANDO AO WI-FI...");
  Serial.print("Rede: "); Serial.println(wifiSSID);

  WiFi.mode(WIFI_AP_STA);
  WiFi.begin(wifiSSID.c_str(), wifiPassword.c_str());

  unsigned long inicio = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - inicio < 15000) {
    delay(500);
    Serial.print(".");
  }

  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("Wi-Fi conectado com sucesso!");
    Serial.print("IP Local: "); Serial.println(WiFi.localIP());
    return true;
  }

  Serial.println("Nao foi possivel conectar.");
  return false;
}

void iniciarAP() {
  WiFi.softAP(AP_SSID, AP_PASSWORD);
  Serial.print("AP iniciado! Conecte em: "); Serial.println(AP_SSID);
  Serial.print("IP de config: "); Serial.println(WiFi.softAPIP());
}

void iniciarServidorWeb() {
  server.on("/", HTTP_GET, paginaConfig);
  server.on("/salvar", HTTP_POST, salvarWiFi);
  server.begin();
  Serial.println("Servidor web local ativo.");
}


// =====================================================
// DETECÇÃO DE COR PELA CÂMERA
// =====================================================

String detectarCor(camera_fb_t *fb) {
  uint16_t *pixels = (uint16_t*) fb->buf;
  int w = fb->width;
  int h = fb->height;

  long somaR = 0, somaG = 0, somaB = 0;
  int amostras = 0;

  int cx = w / 2;
  int cy = h / 2;

  for (int y = cy - 10; y < cy + 10; y++) {
    for (int x = cx - 10; x < cx + 10; x++) {
      uint16_t pixel = pixels[y * w + x];

      // RGB565 -> RGB888
      uint8_t r = ((pixel >> 11) & 0x1F) << 3;
      uint8_t g = ((pixel >> 5)  & 0x3F) << 2;
      uint8_t b = (pixel & 0x1F) << 3;

      somaR += r;
      somaG += g;
      somaB += b;
      amostras++;
    }
  }

  uint8_t r = somaR / amostras;
  uint8_t g = somaG / amostras;
  uint8_t b = somaB / amostras;

  Serial.printf("RGB medio: R=%d G=%d B=%d\n", r, g, b);

  if (r > 150 && g < 100 && b < 100) return "VERMELHO";
  if (g > 150 && r < 100 && b < 100) return "VERDE";
  if (b > 150 && r < 100 && g < 100) return "AZUL";
  if (r > 150 && g > 150 && b < 100) return "AMARELO";
  if (r > 180 && g > 180 && b > 180) return "BRANCO";
  if (r < 60  && g < 60  && b < 60)  return "PRETO";

  return "INDEFINIDO";
}


// =====================================================
// ENVIAR DETECÇÃO PARA O SERVIDOR VIA HTTPS (RENDER)
// =====================================================

void enviarParaServidor(String cor) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Wi-Fi desconectado!");
    return;
  }

  WiFiClientSecure client;
  client.setInsecure(); // Permite conexao SSL/HTTPS sem certificado raiz

  HTTPClient http;
  http.begin(client, String(serverURL) + "/api/objetos");
  http.addHeader("Content-Type", "application/json");

  String payload =
    "{\"dispositivoId\":" + String(DISPOSITIVO_ID) +
    ",\"cor\":\"" + cor + "\"" +
    ",\"confianca\":90}";

  int httpCode = http.POST(payload);

  Serial.printf("POST /api/objetos %s -> HTTP %d\n", payload.c_str(), httpCode);

  if (httpCode == 201) {
    Serial.println("Deteccao registrada no servidor!");
  } else if (httpCode > 0) {
    Serial.println("Resposta: " + http.getString());
  }

  http.end();
}


// =====================================================
// HEARTBEAT VIA HTTPS (RENDER)
// =====================================================

void enviarHeartbeat() {
  if (WiFi.status() != WL_CONNECTED) return;

  WiFiClientSecure client;
  client.setInsecure(); // Permite conexao SSL/HTTPS

  HTTPClient http;
  http.begin(
    client,
    String(serverURL) + "/api/dispositivos/" + String(DISPOSITIVO_ID) + "/heartbeat"
  );
  http.addHeader("Content-Type", "application/json");

  String payload =
    "{\"esteiraLigada\":" + String(releLigado ? "true" : "false") + "}";

  int httpCode = http.POST(payload);
  Serial.printf("Heartbeat HTTPS -> HTTP %d\n", httpCode);

  http.end();
}


// =====================================================
// SETUP
// =====================================================

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("\n==============================");
  Serial.println(" ESP32-CAM + NUVEM (RENDER) ");
  Serial.println("==============================");

  pinMode(TRIGGER_PIN, INPUT);
  pinMode(RELE_PIN, OUTPUT);
  desligarRele();

  iniciarCamera();

  WiFi.mode(WIFI_AP_STA);
  iniciarAP();

  bool conectado = conectarWiFi();

  if (conectado) {
    Serial.println("Conexao com a internet confirmada!");
  }

  iniciarServidorWeb();

  ligarRele();

  // Primeiro heartbeat para ativar o dashboard imediatamente
  ultimoHeartbeat = millis();
  enviarHeartbeat();
}


// =====================================================
// LOOP
// =====================================================

void loop() {
  server.handleClient();

  // Heartbeat a cada 20 segundos
  if (millis() - ultimoHeartbeat >= HEARTBEAT_INTERVALO) {
    ultimoHeartbeat = millis();
    enviarHeartbeat();
  }

  // Leitura do Sensor IR
  if (digitalRead(TRIGGER_PIN) == LOW) {
    Serial.println("\nOBJETO DETECTADO!");

    desligarRele();
    delay(200); // Aguarda motor parar

    camera_fb_t *fb = esp_camera_fb_get();
    if (fb) {
      Serial.println("Foto capturada!");
      String cor = detectarCor(fb);
      Serial.print("COR: "); Serial.println(cor);

      enviarParaServidor(cor);
      esp_camera_fb_return(fb);
    } else {
      Serial.println("ERRO: Falha ao capturar foto.");
    }

    ligarRele();
    delay(1000); // Anti-duplicação
  }
}
