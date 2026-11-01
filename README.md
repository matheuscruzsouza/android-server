# S4 (Simple Shared Storage Service) • nano-spring 2.4.0 Showcase

> **Serviço de Armazenamento de Objetos e Servidor de Arquivos com compatibilidade S3 e interface Desktop Explorer no Android.**  
> Implementação oficial de referência ("PetClinic") do micro-framework [nano-spring](https://github.com/matheuscruzsouza/nano-spring) modular.

O **S4 (Simple Shared Storage Service)** (anteriormente denominado *AndroidServer*) transforma um smartphone físico Android (testado e otimizado para o **Motorola Moto G4 Play / Android 7.1.1 API 25**) em um servidor de armazenamento de objetos leve, moderno e robusto.

O projeto agora tem foco central em **Storage Compartilhado (File Server & Object Storage)**:
1. **Desktop Explorer Web UI**: Interface web inspirada no Windows 11 Explorer e Copyparty, construída com HTMX, Tailwind CSS, SSE (Server-Sent Events) para atualizações em tempo real, suporte a temas Claro/Escuro, visualizador e player de mídia integrado e upload drag & drop.
2. **S3 Protocol & Compatibility Gateway**: Compatibilidade nativa com o protocolo S3 na raiz (`/` e `/{bucket}/{key}`) e alias legado `/s3`, permitindo o uso direto com ferramentas oficiais como AWS CLI, MinIO Client (`mc`), rclone e SDKs S3 sem qualquer adaptação.
3. **AWS Signature Version 4 (SigV4) & Presigned URLs**: Validação criptográfica de assinaturas SigV4 (`AWS4-HMAC-SHA256`) em headers HTTP e URLs pré-assinadas via query string (`X-Amz-Signature`), além de CORS nativo S3 e suporte a streaming ponta a ponta sem estouro de memória no Android.
4. **Multipart Upload & Operações em Lote**: Suporte a upload dividido em staging de disco temporário e exclusão de múltiplos objetos (`DeleteObjects`).
5. **Mini-IAM Nativo**: Controle de acesso por credenciais com chaves `AccessKey` e `SecretKey` e políticas granulares (`Admin`, `ReadOnly`, `WriteOnly`).
6. **Arquitetura nano-spring 2.4.0**: Baseada nos módulos independentes `nano-spring-core`, `nano-spring-web`, `nano-spring-data` e `nano-spring-discovery`, com suporte a CORS nativo, resolução mDNS para acesso direto via `http://s4.local:9090`, documentação OpenAPI/Swagger e persistência SQLite com migrações versionadas.

---

## 🚀 Funcionalidades Demonstradas

| Funcionalidade | Descrição | Componente nano-spring / Android |
| :--- | :--- | :--- |
| **S4 Desktop Explorer & File Server** | Interface web desktop com design Explorer (Windows 11), visualização de arquivos, player de mídia e upload drag & drop | `@RestController("")`, `StorageManager` |
| **S3 Gateway & Protocolo na Raiz** | Endpoints nativos na raiz (`/{bucket}/{key}`), `HEAD Bucket`, `HEAD Object`, `ListObjectsV2` com cursor e alias `/s3` | `S3RequestDispatcher`, `FileUtils`, `ByteRangeHandler` |
| **AWS SigV4 & Presigned URLs** | Validação de assinatura AWS Signature Version 4 em headers e query parameters (`X-Amz-Signature`) | `SigV4Signer`, `IamService` |
| **Multipart Upload & Operações em Lote** | Upload dividido com staging em disco sem estouro de RAM e `DeleteObjects` em lote | `MultipartUploadManager`, `S3RequestDispatcher` |
| **Mini-IAM & Segurança S3** | Gerenciamento de credenciais com políticas de acesso (`Admin`, `ReadOnly`, `WriteOnly`) | `IamSecurityInterceptor`, `IamService`, `IamUserRepository` |
| **OpenAPI / Swagger UI** | Especificação de API e documentação interativa gerada automaticamente | `nano-spring-web`, `/swagger-ui.html`, `/v3/api-docs` |
| **CORS Nativo S3 & API** | Suporte completo a requisições Cross-Origin e tratamento de preflight `OPTIONS` | `CorsConfiguration`, `S3RequestDispatcher` |
| **Resolução de Host mDNS** | Acesso direto pelo navegador via `http://s4.local:9090` sem configurar DNS | `MdnsHostResponder`, `nano.nsd.host-resolution=true` |
| **Descoberta de Serviço** | Anúncio do serviço na rede local via mDNS / DNS-SD Zeroconf | `NsdServiceManager`, `nano.nsd.enabled=true` |
| **REST API com JSON** | Endpoints serializados automaticamente para JSON | `@RestController("/api/users")`, `@RequestBody`, `@PathVariable` |
| **Server-Side Rendering (SSR)** | Páginas HTML dinâmicas com JMustache compiladas no dispositivo | `ModelAndView("user_profile")`, assets HTML/CSS/JS |
| **Server-Sent Events (SSE)** | Streaming contínuo de telemetria (Uptime, requisições, memória) em tempo real | `SseEmitter` em `/api/events` (`text/event-stream`) |
| **Migrações SQLite Nativas** | Versionamento de banco estilo Flyway executando scripts em `assets/db/migration/V*__*.sql` | `SqliteMigrator`, `nano_schema_history` |
| **Camada de Repositório** | Injeção gerenciada de classes de persistência | `@Repository`, `@Autowired private SQLiteDatabase db;` |
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
- **Host mDNS Local:** `http://s4.local:9090/`
- **Gradle:** 8.6
- **Android Gradle Plugin (AGP):** 8.2.2 (estável e compatível com Java 17)
- **Java:** OpenJDK 17 (`JavaVersion.VERSION_11` bytecode target)
- **Versão nano-spring:** `2.4.0` (módulos `core`, `web`, `data`, `discovery`)

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

### 1. S4 Desktop Explorer, Web (SSR) e File Server
- **`GET /` ou `GET /web`**: Interface principal S4 Desktop Explorer inspirada no Windows 11 com barra lateral de navegação, upload drag & drop, pré-visualização de arquivos e telemetria SSE ao vivo.
- **`GET /party`**: Interface Web File Server integrada com upload, listagem e download simplificado.
- **`GET /party/api/files`**: Lista arquivos do bucket padrão em JSON.
- **`POST /party/upload`**: Envio de arquivos via formulário web multipart para o armazenamento local.
- **`GET /party/download/:filename`**: Download de arquivos salvos.
- **`DELETE /party/delete/:filename`**: Exclusão de arquivos via interface web.
- **`GET /web/profile/:id`**: Página de perfil com lista de arquivos do usuário e formulário de upload.
- **`GET /web/download/:fileId`**: Download direto do arquivo salvo.
- **`GET /web/html`**: Demonstração de resposta HTML pura direta sem template.

### 2. S3 Compatible API (AWS CLI, Rclone, Cyberduck) & Mini-IAM
O endpoint `/s3` e o catálogo de credenciais `/api/iam` são protegidos pelo **Mini-IAM Persistente** do S4 com suporte a políticas (`Admin`, `ReadOnly`, `WriteOnly`):

- **Credenciais Padrão Pré-carregadas:**
  - `Admin`: `AKIA_ADMIN_ANDROID` / `secret_admin_super_key_2026`
  - `ReadOnly`: `AKIA_READONLY_ANDROID` / `secret_readonly_key_2026`
  - `WriteOnly`: `AKIA_BACKUP_ANDROID` / `secret_backup_key_2026`

- **Métodos de Autenticação Suportados:**
  - AWS CLI / S3 SDKs: cabeçalho `Authorization: AWS4-HMAC-SHA256 Credential=<AccessKey>/...` ou `AWS <AccessKey>:<Signature>`.
  - Bearer Token: `Authorization: Bearer <SecretKey>`.
  - Basic Auth: `Authorization: Basic <Base64(AccessKey:SecretKey)>`.
  - Query Parameter: `?access_key=AKIA_...` ou `?token=secret_...`.

- **`GET /s3`**: ListBuckets (retorna XML padrão S3).
  ```bash
  curl -i -H "Authorization: Bearer secret_admin_super_key_2026" http://localhost:9090/s3
  ```
- **`GET /s3/:bucket`**: ListObjectsV2 (lista objetos do bucket).
  ```bash
  curl -i -H "Authorization: Bearer secret_readonly_key_2026" http://localhost:9090/s3/default
  ```
- **`PUT /s3/:bucket/:key`**: PutObject (upload de arquivo direto via stream com cálculo de ETag MD5).
  ```bash
  curl -i -X PUT -H "Authorization: Bearer secret_backup_key_2026" \
       --data-binary @"meu_arquivo.txt" http://localhost:9090/s3/default/meu_arquivo.txt
  ```
- **`DELETE /s3/:bucket/:key`**: DeleteObject (remoção do arquivo no bucket - restrito a Admin).
  ```bash
  curl -i -X DELETE -H "Authorization: Bearer secret_admin_super_key_2026" \
       http://localhost:9090/s3/default/meu_arquivo.txt
  ```

- **Gerenciamento de Credenciais IAM (`/api/iam/users`):**
  ```bash
  # Listar credenciais
  curl -i -H "Authorization: Bearer secret_admin_super_key_2026" http://localhost:9090/api/iam/users

  # Criar nova credencial
  curl -i -X POST http://localhost:9090/api/iam/users \
       -H "Authorization: Bearer secret_admin_super_key_2026" \
       -H "Content-Type: application/json" \
       -d '{"name":"Novo Bot","accessKey":"AKIA_BOT_01","secretKey":"bot_secret_xyz","policy":"ReadOnly"}'
  ```

#### Uso com o AWS CLI (Protocolo Nativo na Raiz)
Configure o profile `s4` no seu AWS CLI (`aws configure --profile s4`):
- **AWS Access Key ID**: sua chave de acesso IAM (ex.: `AKTEST`)
- **AWS Secret Access Key**: sua chave secreta IAM
- **Default region name**: `us-east-1`

```bash
ENDPOINT="http://localhost:9090"

# 1. Verificar se o bucket existe (HEAD Bucket)
aws --profile s4 --endpoint-url "$ENDPOINT" s3api head-bucket --bucket default

# 2. Listar arquivos no bucket usando ListObjectsV2
aws --profile s4 --endpoint-url "$ENDPOINT" s3api list-objects-v2 --bucket default

# 3. Upload de arquivo via streaming direto
aws --profile s4 --endpoint-url "$ENDPOINT" s3 cp ./video.mp4 s3://default/video.mp4

# 4. Upload multipart automático para grandes arquivos (>8MB)
aws --profile s4 --endpoint-url "$ENDPOINT" s3 cp ./arquivo_grande.bin s3://default/arquivo_grande.bin

# 5. Download de arquivo com integridade ETag
aws --profile s4 --endpoint-url "$ENDPOINT" s3 cp s3://default/video.mp4 ./video_baixado.mp4

# 6. Excluir arquivo
aws --profile s4 --endpoint-url "$ENDPOINT" s3 rm s3://default/video.mp4
```

> 📖 **Guia de Testes Completo**: Para instruções com **rclone**, **MinIO Client (`mc`)** e Presigned URLs, consulte [`docs/S3-COMPLIANCE-TESTS.md`](docs/S3-COMPLIANCE-TESTS.md).

### 3. REST API (JSON)
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

### 4. Descoberta de Serviço & Resolução de Host mDNS
- **`GET /api/discovery/nsd`**: Consulta status de registro no mDNS / DNS-SD Zeroconf e resolução de Host A para navegadores (`s4.local`).
  ```bash
  curl -i http://localhost:9090/api/discovery/nsd
  ```
  Ou acesse diretamente pelo navegador em qualquer dispositivo na rede Wi-Fi:
  ```text
  http://s4.local:9090/
  ```

### 5. Suporte a CORS (Cross-Origin Resource Sharing)
- **`GET /api/config/cors`**: Consulta as políticas de CORS ativas definidas em `application.properties`.
  ```bash
  curl -i http://localhost:9090/api/config/cors
  ```
- **Requisição Preflight OPTIONS automática:**
  ```bash
  curl -i -X OPTIONS http://localhost:9090/api/users \
       -H "Origin: http://meuapp.com" \
       -H "Access-Control-Request-Method: POST"
  ```
  Retorna `HTTP 200 OK` com os cabeçalhos `Access-Control-Allow-Origin: *`, `Access-Control-Allow-Methods`, etc.

### 6. Banco de Dados SQLite & Migrações Nativas
- **`GET /api/database/migrations`**: Histórico das migrações SQL aplicadas pelo `SqliteMigrator`.
  ```bash
  curl -i http://localhost:9090/api/database/migrations
  ```
- **`GET /api/database/events`**: Registros recuperados via `@Repository` (`ServerEventRepository`).
  ```bash
  curl -i http://localhost:9090/api/database/events
  ```

### 7. Server-Sent Events (SSE)
- **`GET /api/events`**: Stream em tempo real de telemetria do servidor.
  ```bash
  curl -N http://localhost:9090/api/events
  ```

### 8. Upload de Arquivo Multipart
- **`POST /web/upload`**:
  ```bash
  curl -i -F "userId=1" -F "documento=@meu_arquivo.pdf" http://localhost:9090/web/upload
  ```

---

## 🏛️ Arquitetura do Sistema

```
┌─────────────────────────────────────────────────────────────────┐
│     Navegador Web (S4 Explorer) / AWS CLI / Rclone / S3 SDKs    │
└───────────────┬───────────────────────────────▲─────────────────┘
                │ Requisição HTTP (:9090)       │ Resposta (HTML / JSON / XML S3 / SSE)
┌───────────────▼───────────────────────────────┴─────────────────┐
│                   MyBackendService (Android Service)            │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │             nano-spring Server (NanoHTTPD)                │  │
│  │  - Módulos: core, web, data, discovery (2.4.0)            │  │
│  │  - Roteamento e Injeção (@RestController, @Autowired)     │  │
│  │  - IAM Security Interceptor (Autenticação AWS4 / Bearer)  │  │
│  │  - Motor de Armazenamento S3 & FileUtils                  │  │
│  │  - JMustache Template Engine (assets/templates/)          │  │
│  │  - SseEmitter (Streaming de Eventos / Telemetria)         │  │
│  │  - NsdServiceManager (mDNS / Resolução s4.local)          │  │
│  │  - SqliteMigrator (assets/db/migration/V*__*.sql)         │  │
│  │  - Handler de Arquivos Estáticos (assets/static/)         │  │
│  └───────┬────────────────────┬────────────────────┬─────────┘  │
│          │                    │                    │            │
│    ┌─────▼──────┐       ┌─────▼──────┐       ┌─────▼──────┐     │
│    │ IamService │       │StorageMgr  │       │UserService │     │
│    │ (@Service) │       │(Singletons)│       │ (@Service) │     │
│    └─────┬──────┘       └─────┬──────┘       └─────┬──────┘     │
│          │                    │                    │            │
│    ┌─────▼──────┐       ┌─────▼──────┐       ┌─────▼──────────┐ │
│    │IamUserRepo │       │File Storage│       │  AppDatabase   │ │
│    │(@Repository│       │(App Sandbox│       │  (Room SQLite) │ │
│    └─────┬──────┘       │  / Buckets)│       └────────────────┘ │
│          │              └────────────┘                          │
│    ┌─────▼───────────────────────────┐                          │
│    │ Native SQLite DB (V1..V3 IAM)   │                          │
│    └─────────────────────────────────┘                          │
│  Notificação Foreground: Uptime, IP, Requisições em tempo real  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📄 Licença e Créditos

Desenvolvido por **Matheus Cruz de Souza** como implementação de referência da biblioteca [nano-spring](https://github.com/matheuscruzsouza/nano-spring).
