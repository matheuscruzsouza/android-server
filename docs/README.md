# AndroidServer • Documentação de Arquitetura e Referência (nano-spring 1.3.0)

Consulte o arquivo principal [README.md](../README.md) na raiz do projeto para instruções de início rápido e catálogo completo de comandos.

## Novidades do nano-spring 1.3.0

### 1. Migrações de Banco de Dados com `SqliteMigrator`
O nano-spring 1.3.0 traz gerenciamento nativo de esquemas SQLite estilo Flyway:
- Localização padrão em `assets/db/migration/`.
- Padrão de nomenclatura obrigatório: `V{numero}__{descricao}.sql` (ex: `V1__init_server_events.sql`).
- Tabela de controle de versão: `nano_schema_history` criada e controlada automaticamente.
- Permite configurar o nome do banco e localização via `application.properties`:
  ```properties
  nano.datasource.name=androidserver.db
  nano.datasource.migration.enabled=true
  nano.datasource.migration.location=db/migration
  ```

### 2. Anotação `@Repository` e Acesso ao SQLite
- Classes anotadas com `@Repository` são identificadas automaticamente durante o escaneamento do `DexFile` e instanciadas como singletons no container de injeção de dependência.
- Podem ser injetadas em controladores ou serviços com `@Autowired`.
- O método estático `Server.getDatabase()` disponibiliza a instância de `android.database.sqlite.SQLiteDatabase` gerenciada e migrada pelo servidor.

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

### 5. Persistência
- **Android Room (SQLite v3):** Persistência relacional para `User` e `UserFile`.
- **Native SQLite (nano-spring 1.3.0):** Gerenciado por `SqliteMigrator` para eventos e migrações SQL.
