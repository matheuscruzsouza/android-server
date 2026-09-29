# AndroidServer • Documentação de Arquitetura e Referência

Consulte o arquivo principal [README.md](../README.md) na raiz do projeto para instruções de início rápido e catálogo completo de comandos.

## Detalhes de Implementação

### 1. Injeção de Dependências e Descoberta de Controladores
O `nano-spring` inicializa no Android instanciando a classe `Server(context, port, basePackage)`. O servidor inspeciona as classes do APK em tempo de execução via `DexFile`, detectando:
- `@RestController`
- `@Service`
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

### 5. Persistência com Android Room
- O banco SQLite `AppDatabase` (versão 3) armazena as entidades `User` e `UserFile`.
- O callback `onOpen` garante a inicialização de registros padrão (seeding) caso a base esteja vazia, permitindo testes imediatos da API e SSR.
