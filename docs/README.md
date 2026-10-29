# AndroidServer • Documentação de Arquitetura e Referência (nano-spring 1.6.0)

Consulte o arquivo principal [README.md](../README.md) na raiz do projeto para instruções de início rápido e catálogo completo de comandos.

## Novidades do nano-spring 1.6.0

### 1. Suporte Nativo a CORS (`CorsConfiguration`)
O nano-spring 1.6.0 adiciona suporte integrado a Cross-Origin Resource Sharing (CORS):
- Trata automaticamente requisições HTTP do método `OPTIONS` (Preflight) devolvendo `200 OK` e os cabeçalhos `Access-Control-*`.
- Anexa automaticamente cabeçalhos CORS em todas as respostas HTTP quando ativo.
- Configurável via `application.properties`:
  ```properties
  nano.cors.enabled=true
  nano.cors.allowed-origins=*
  nano.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
  nano.cors.allowed-headers=Content-Type,Authorization,X-Requested-With,Accept
  nano.cors.max-age=86400
  ```
- Injetável no container DI via `@Autowired private CorsConfiguration corsConfiguration;`.

## Novidades do nano-spring 1.5.0

### 1. Resolução de Host mDNS para Navegadores (`MdnsHostResponder`)
O nano-spring 1.5.0 adiciona o `MdnsHostResponder`, permitindo que navegadores desktop e móveis acessem o servidor via `http://<nome>.local:<porta>/`:
- Responde nativamente a consultas de Host (registro A) na porta Multicast UDP `5353` (`224.0.0.251`).
- Gerencia automaticamente o `WifiManager.MulticastLock` do Android durante o ciclo de vida do servidor.
- Configurável via `application.properties`:
  ```properties
  nano.nsd.enabled=true
  nano.nsd.name=AndroidServer
  nano.nsd.type=_http._tcp.
  nano.nsd.host-resolution=true
  ```
- Integrado ao `NsdServiceManager`, acessível via `nsdServiceManager.getHostResponder()`.

### 2. Descoberta de Serviço com NsdServiceManager (mDNS / Zeroconf / Bonjour - 1.4.0+)
Suporte nativo ao Android `NsdManager`:
- Anúncio de serviço na rede local via protocolo DNS-SD / mDNS (`_http._tcp.`).
- O serviço é registrado automaticamente na inicialização (`server.start()`) e desregistrado ao encerrar (`server.stop()`), prevenindo serviços órfãos.
- Injetável em controladores e serviços via `@Autowired private NsdServiceManager nsdServiceManager;`.

### 2. Migrações de Banco de Dados com `SqliteMigrator` (1.3.0+)
O nano-spring traz gerenciamento nativo de esquemas SQLite estilo Flyway:
- Localização padrão em `assets/db/migration/`.
- Padrão de nomenclatura obrigatório: `V{numero}__{descricao}.sql` (ex: `V1__init_server_events.sql`).
- Tabela de controle de versão: `nano_schema_history` criada e controlada automaticamente.
- Permite configurar o nome do banco e localização via `application.properties`:
  ```properties
  nano.datasource.name=androidserver.db
  nano.datasource.migration.enabled=true
  nano.datasource.migration.location=db/migration
  ```

### 3. Anotação `@Repository` e Injeção de `SQLiteDatabase`
- Classes anotadas com `@Repository` são identificadas automaticamente durante o escaneamento do `DexFile` e instanciadas como singletons no container de injeção de dependência.
- O `SQLiteDatabase` gerenciado é injetado diretamente com `@Autowired private SQLiteDatabase database;`.

---

## Detalhes de Implementação

### 1. Injeção de Dependências e Descoberta de Controladores
O `nano-spring` inicializa no Android instanciando a classe `Server(context, port, basePackage)`. O servidor inspeciona as classes do APK em tempo de execução via `DexFile`, detectando:
- `@RestController`
- `@Service`
- `@Repository`
- `@Autowired`
- `@Value`
- `@ControllerAdvice` e `@ExceptionHandler`
- `@Interceptor`

### 2. Roteamento Dinâmico com Expressões Regulares
As rotas são normalizadas e mapeadas usando regex:
- Parâmetros de rota como `:id` (`@PathVariable`) são convertidos para grupos de captura regex `([^/]+)` correspondentes à URI recebida.
- Os parâmetros de consulta (`@RequestParam`) são lidos diretamente da query string da requisição HTTP.
- Requisições multipart (`@UploadedFile`) têm seus arquivos temporários preservados e transmitidos como `java.io.File` para o método do controlador.

### 3. Renderização Server-Side (Mustache)
Templates localizados em `assets/templates/*.html` são interpretados pelo JMustache:
- Controladores retornam `ModelAndView("nome_template").addObject("chave", valor)`.
- As variáveis são injetadas de forma segura e o HTML final é servido com codificação UTF-8 e cabeçalho `text/html`.

### 4. Streaming em Tempo Real (Server-Sent Events)
- Através de `SseEmitter`, o controlador mantém uma conexão persistente `text/event-stream` com o navegador cliente.
- O payload de telemetria é convertido automaticamente para JSON e enviado em formato padrão SSE (`event: ...`, `data: ...`).
