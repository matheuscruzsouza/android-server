# Guia Prático de Testes de Conformidade S3 (AWS CLI, rclone, MinIO mc)

Este guia apresenta as instruções exatas para validar a compatibilidade do **S4 Server** com clientes oficiais de S3 sem nenhuma adaptação proprietária.

---

## 1. Configuração do AWS CLI

Adicione ou configure o profile `s4` no seu ambiente:

```bash
aws configure --profile s4
```

Preencha com as credenciais cadastradas no IAM do S4:
- **AWS Access Key ID**: `SUA_ACCESS_KEY` (ex.: `AKTEST`)
- **AWS Secret Access Key**: `SUA_SECRET_KEY` (ex.: chave secreta do usuário IAM)
- **Default region name**: `us-east-1`
- **Default output format**: `json`

### Variável de Endpoint
Defina a URL do seu servidor Android (porta padrão `8080` ou conforme configurado nas Settings):

```bash
ENDPOINT="http://localhost:8080"
# Ou via IP do Android na rede local:
# ENDPOINT="http://192.168.1.100:8080"
```

---

## 2. Bateria de Comandos S3

### 2.1. Criar Bucket (`create-bucket` e `HEAD Bucket`)
```bash
# Criação do bucket na raiz
aws --profile s4 --endpoint-url "$ENDPOINT" s3api create-bucket --bucket fotos

# Verificação HEAD Bucket (retorna 200 OK sem corpo)
aws --profile s4 --endpoint-url "$ENDPOINT" s3api head-bucket --bucket fotos
```

### 2.2. Enviar Arquivo via Streaming (`PutObject`)
```bash
# Upload de arquivo simples
echo "Olá S4 S3 Server!" > exemplo.txt
aws --profile s4 --endpoint-url "$ENDPOINT" s3 cp exemplo.txt s3://fotos/exemplo.txt
```

### 2.3. Consultar Metadados (`head-object`)
```bash
aws --profile s4 --endpoint-url "$ENDPOINT" s3api head-object --bucket fotos --key exemplo.txt
```
*Saída esperada:*
```json
{
    "AcceptRanges": "bytes",
    "LastModified": "2026-11-01T...",
    "ContentLength": 19,
    "ETag": "\"d41d8cd98f00b204e9800998ecf8427e\"",
    "ContentType": "text/plain"
}
```

### 2.4. Listagem com Cursor Real (`list-objects-v2`)
```bash
# Listagem padrão
aws --profile s4 --endpoint-url "$ENDPOINT" s3api list-objects-v2 --bucket fotos

# Listagem paginada (max-keys)
aws --profile s4 --endpoint-url "$ENDPOINT" s3api list-objects-v2 --bucket fotos --max-keys 2
```

### 2.5. Upload Dividido / Grande Arquivo (`Multipart Upload`)
O AWS CLI aciona automaticamente o Multipart Upload para arquivos maiores que o limite configurado (padrão 8MB):

```bash
# Gera um arquivo de teste de 15MB
dd if=/dev/urandom of=arquivo_grande.bin bs=1M count=15

# Upload com multipart automático
aws --profile s4 --endpoint-url "$ENDPOINT" s3 cp arquivo_grande.bin s3://fotos/arquivo_grande.bin
```

### 2.6. Download via Streaming (`GetObject` com Range)
```bash
aws --profile s4 --endpoint-url "$ENDPOINT" s3 cp s3://fotos/exemplo.txt baixado.txt
cat baixado.txt
```

### 2.7. Exclusão em Lote (`DeleteObjects`) e Remoção do Bucket
```bash
# Excluir objetos
aws --profile s4 --endpoint-url "$ENDPOINT" s3 rm s3://fotos/exemplo.txt
aws --profile s4 --endpoint-url "$ENDPOINT" s3 rm s3://fotos/arquivo_grande.bin

# Excluir o bucket vazio
aws --profile s4 --endpoint-url "$ENDPOINT" s3api delete-bucket --bucket fotos
```

---

## 3. Configuração com rclone

No arquivo `~/.config/rclone/rclone.conf`:

```ini
[s4]
type = s3
provider = Other
env_auth = false
access_key_id = SUA_ACCESS_KEY
secret_access_key = SUA_SECRET_KEY
endpoint = http://localhost:8080
region = us-east-1
acl = private
```

### Comandos rclone:
```bash
# Listar buckets
rclone lsd s4:

# Sincronizar diretório local com bucket S4
rclone sync ./meus_documentos s4:documentos

# Listar arquivos no bucket
rclone ls s4:documentos
```

---

## 4. Configuração com MinIO Client (`mc`)

```bash
# Registrar o alias do S4
mc alias set s4 http://localhost:8080 SUA_ACCESS_KEY SUA_SECRET_KEY

# Comandos de teste
mc ls s4
mc mb s4/backup
mc cp meu_backup.zip s4/backup/
mc rm s4/backup/meu_backup.zip
mc rb s4/backup
```
