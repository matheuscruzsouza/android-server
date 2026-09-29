# AndroidServer • Showcase & Referência do nano-spring

> **A aplicação oficial de referência ("PetClinic") do micro-framework [nano-spring](https://github.com/matheuscruzsouza/nano-spring) no Android.**

O **AndroidServer** demonstra como transformar um smartphone físico Android (testado e otimizado para o **Motorola Moto G4 Play / Android 7.1.1 API 25**) em um servidor web moderno, completo e de baixo consumo de recursos, executando endpoints RESTful, páginas dinâmicas renderizadas no dispositivo (SSR Mustache), streaming em tempo real (Server-Sent Events), uploads de arquivos (Multipart) e persistência local com SQLite via Android Room.

---

## 🚀 Funcionalidades Demonstradas

| Funcionalidade | Descrição | Componente nano-spring / Android |
| :--- | :--- | :--- |
| **Página Inicial Interativa** | Dashboard dark-mode com métricas e catálogo de APIs em `GET /` e `GET /web` | `@RestController("")`, `ModelAndView("index")` |
| **REST API com JSON** | Endpoints serializados automaticamente para JSON | `@RestController("/api/users")`, `@RequestBody`, `@PathVariable` |
| **Server-Side Rendering (SSR)** | Páginas HTML dinâmicas com JMustache compiladas no dispositivo | `ModelAndView("user_profile")`, assets HTML/CSS/JS |
| **Server-Sent Events (SSE)** | Streaming contínuo de telemetria (Uptime, requisições, memória) em tempo real | `SseEmitter` em `/api/events` (`text/event-stream`) |
| **Upload de Arquivos Multipart** | Envio de documentos diretamente para o armazenamento do Android | `@UploadedFile("documento")`, `FileService` |
| **Download de Arquivos** | Download binário de arquivos persistidos no aparelho | `ResponseEntity.ok(file)` (`application/octet-stream`) |
| **Injeção de Configurações** | Leitura de propriedades de configuração em tempo de execução | `@Value("${app.welcome.message}")`, `application.properties` |
| **Persistência Local (Room)** | Banco de dados SQLite relacional com seeding automático | `AppDatabase` (Room v3), `UserDao`, `UserFileDao` |
| **Serviço em Foreground** | Servidor persistente na porta `9090` com notificação interativa em segundo plano | Android Foreground Service (`MyBackendService`) |

---

## 📱 Ambiente e Compatibilidade

- **Aparelho Alvo:** Motorola Moto G4 Play (`harpia`)
- **Android OS:** 7.1.1 Nougat (API 25)
- **Porta Padrão:** `9090` (definida em `assets/application.properties`)
- **Gradle:** 8.6
- **Android Gradle Plugin (AGP):** 8.2.2 (estável e compatível com Java 17)
- **Java:** OpenJDK 17 (`JavaVersion.VERSION_11` bytecode target)

---

## 🛠️ Como Executar

### 1. Pré-requisitos
- Dispositivo Android com Depuração USB ativada ou Emulador Android.
- Android SDK com plataforma instalada (`compileSdk = 36`, `minSdk = 25`).
- Java 17 configurado no ambiente.

### 2. Compilação e Instalação

```bash
# Compilar o APK em modo Debug
./gradlew assembleDebug

# Instalar no dispositivo via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Iniciar o aplicativo
adb shell am start -n com.github.matheuscruzsouza.androidserver/.MainActivity
```

### 3. Acesso e Testes Locais

Para acessar o servidor a partir do computador conectado via USB:

```bash
# Redirecionar a porta 9090 do Android para o computador
adb forward tcp:9090 tcp:9090
```

Abra seu navegador em:
👉 **[http://localhost:9090](http://localhost:9090)**

---

## 📡 Catálogo de Endpoints e Exemplos de Uso

### 1. Dashboard e Web (SSR)
- **`GET /` ou `GET /web`**: Landing page com telemetria ao vivo, catálogo interativo e formulário de upload.
- **`GET /web/profile/:id`**: Página de perfil com lista de arquivos do usuário e formulário de upload.
- **`GET /web/download/:fileId`**: Download direto do arquivo salvo.

### 2. REST API (JSON)
- **`GET /health`**: Verificação de integridade e injeção de `@Value`.
  ```bash
  curl -i http://localhost:9090/health
  ```
- **`GET /api/users`**: Listar todos os usuários do banco Room.
  ```bash
  curl -i http://localhost:9090/api/users
  ```
- **`GET /api/users/:id`**: Buscar usuário por ID.
  ```bash
  curl -i http://localhost:9090/api/users/1
  ```
- **`GET /api/users/search?q=matheus`**: Filtrar usuários por nome ou username.
  ```bash
  curl -i "http://localhost:9090/api/users/search?q=matheus"
  ```
- **`POST /api/users`**: Cadastrar novo usuário.
  ```bash
  curl -i -X POST http://localhost:9090/api/users \
       -H "Content-Type: application/json" \
       -d '{"name":"Novo Usuario","username":"novouser"}'
  ```

### 3. Server-Sent Events (SSE)
- **`GET /api/events`**: Stream em tempo real de telemetria do servidor.
  ```bash
  curl -N http://localhost:9090/api/events
  ```

### 4. Upload de Arquivo Multipart
- **`POST /web/upload`**:
  ```bash
  curl -i -F "userId=1" -F "documento=@meu_arquivo.pdf" http://localhost:9090/web/upload
  ```

---

## 🏛️ Arquitetura do Sistema

```
┌─────────────────────────────────────────────────────────────────┐
│                    Navegador Web / Cliente HTTP                 │
└───────────────┬───────────────────────────────▲─────────────────┘
                │ Requisição HTTP (:9090)       │ Resposta (HTML / JSON / SSE)
┌───────────────▼───────────────────────────────┴─────────────────┐
│                   MyBackendService (Android Service)            │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │             nano-spring Server (NanoHTTPD)                │  │
│  │  - Roteamento e Injeção (@RestController, @Autowired)     │  │
│  │  - JMustache Template Engine (assets/templates/)          │  │
│  │  - SseEmitter (Streaming de Eventos / Telemetria)         │  │
│  │  - Handler de Arquivos Estáticos (assets/static/)         │  │
│  └────────────────┬───────────────────────────┬──────────────┘  │
│                   │                           │                 │
│         ┌─────────▼────────┐        ┌─────────▼────────┐        │
│         │   UserService    │        │   FileService    │        │
│         └─────────┬────────┘        └─────────┬────────┘        │
│                   │                           │                 │
│         ┌─────────▼───────────────────────────▼────────┐        │
│         │         AppDatabase (Android Room v3)        │        │
│         │           - UserDao   - UserFileDao          │        │
│         └──────────────────────────────────────────────┘        │
│  Notificação Foreground: Uptime, IP, Requiçoes em tempo real    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📄 Licença e Créditos

Desenvolvido por **Matheus Cruz de Souza** como implementação de referência da biblioteca [nano-spring](https://github.com/matheuscruzsouza/nano-spring).
