# Feature Request / RFC: Resolução de Host mDNS (A/AAAA) para Acesso Direto via Navegador (`<serviceName>.local`)

* **Componente:** `nano-spring` (Módulo de Descoberta / `NsdServiceManager`)
* **Versão Alvo Sugerida:** `1.5.0`
* **Autor:** Comunidade / Projeto AndroidServer
* **Data:** 29 de Setembro de 2026

---

## 1. Contexto e Motivação

Na versão **1.4.0**, o `nano-spring` introduziu o `NsdServiceManager`, implementado sobre a API nativa do Android `android.net.nsd.NsdManager`.

Essa funcionalidade foi um avanço ao permitir o anúncio do servidor via DNS-SD (`_http._tcp.`). Contudo, ao tentar acessar o servidor através de um navegador web padrão (Google Chrome, Firefox, Microsoft Edge, Safari) usando a URL anunciada:

```text
http://androidserver.local:9090/
```

O navegador falha com o erro:
```text
DNS_PROBE_FINISHED_NXDOMAIN
```

### Por que a implementação atual (1.4.0) falha no navegador?

1. **DNS-SD vs Registro de Host (A/AAAA):**
   * O `android.net.nsd.NsdManager` registra apenas registros do tipo **PTR, SRV e TXT** para descoberta de serviços (`_http._tcp.local`).
   * Ele **não** publica e **não** responde a consultas mDNS por registros de **Host (tipo A/AAAA)** para o nome configurado (`<nano.nsd.name>.local`).
2. **Comportamento dos Navegadores:**
   * Navegadores web não realizam varreduras DNS-SD ativas (`_http._tcp`). Quando um usuário digita uma URL com sufixo `.local`, o navegador delega a resolução para o resolver do sistema operacional (Windows 10/11 mDNS, Linux Avahi, macOS Bonjour).
   * O sistema operacional envia uma consulta Multicast UDP na porta `5353` perguntando pelo registro **A** de `androidserver.local`.
   * Como o Android OS não responde a essa consulta pelo nome do serviço, o sistema operacional reporta `NXDOMAIN` (domínio inexistente).

---

## 2. Objetivo

Permitir que desenvolvedores e usuários acessem a aplicação web servida pelo `nano-spring` diretamente pelo navegador de qualquer máquina na rede local digitando:

$$\text{http://} \langle \text{nano.nsd.name} \rangle \text{.local:} \langle \text{server.port} \rangle \text{/}$$

Sem necessidade de:
* Configurar ou alterar registros DNS no roteador.
* Fixar IP do celular ou editar `/etc/hosts` nas máquinas clientes.
* Instalar aplicativos clientes especializados em descoberta de serviço.

---

## 3. Solução Proposta

Implementar uma engine mDNS pura em Java (ou integrar como provedor alternativo/complementar ao `NsdManager`), utilizando a biblioteca **JmDNS** (ou implementação leve equivalente baseada em `MulticastSocket`), capaz de responder diretamente às consultas de **Host A/AAAA** além de manter o anúncio DNS-SD de serviço.

### 3.1. Arquitetura Proposta

Criar uma abstração ou expandir o `NsdServiceManager` para suportar modos de operação configuráveis no `application.properties`:

```properties
# Habilita o serviço de descoberta
nano.nsd.enabled=true
nano.nsd.name=AndroidServer
nano.nsd.type=_http._tcp.

# Novo: Provedor de mDNS (native | jmdns | hybrid)
# - native: Usa android.net.nsd.NsdManager (padrão 1.4.0, apenas DNS-SD)
# - jmdns: Usa JmDNS em Java (responde a DNS-SD e Host A/AAAA no navegador)
# - hybrid: Combina anúncio nativo e resposta de host
nano.nsd.provider=jmdns

# Habilita anúncio explícito de Host A para navegadores (<name>.local)
nano.nsd.host-resolution=true
```

### 3.2. Gerenciamento de Wi-Fi Multicast no Android

O subsistema Wi-Fi do Android desativa o recebimento de pacotes multicast por economia de energia. Para que qualquer implementação mDNS em espaço de usuário (como JmDNS) receba consultas UDP 5353 de outros computadores, o `nano-spring` deve gerenciar o `WifiManager.MulticastLock`:

```java
public class JmDnsServiceManager implements DiscoveryService {
    private WifiManager.MulticastLock multicastLock;
    private JmDNS jmdns;

    public void start(Context context, String serviceName, int port) {
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("nano_spring_mdns_lock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();
        }

        InetAddress bindingAddress = getWifiInetAddress(context);
        jmdns = JmDNS.create(bindingAddress, serviceName);

        // Registra o serviço HTTP DNS-SD
        ServiceInfo serviceInfo = ServiceInfo.create(
            "_http._tcp.local.",
            serviceName,
            port,
            "path=/"
        );
        jmdns.registerService(serviceInfo);
        
        // JmDNS automaticamente responde a requisições de Host A para <serviceName>.local
    }

    public void stop() {
        if (jmdns != null) {
            jmdns.unregisterAllServices();
            jmdns.close();
        }
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
    }
}
```

---

## 4. Requisitos e Permissões do Android

Para os aplicativos que utilizarem a funcionalidade com resolução de host no navegador, o `nano-spring` deve documentar a necessidade da seguinte permissão no `AndroidManifest.xml`:

```xml
<!-- Necessário para receber tráfego Multicast UDP 5353 de navegadores externos -->
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.INTERNET" />
```

---

## 5. Casos de Uso e Benefícios

1. **Apresentações e Demos:** O desenvolvedor inicia o servidor no celular e qualquer colega na mesma Wi-Fi abre `http://androidserver.local:9090` no computador sem precisar conferir o IP do celular nas configurações do Android.
2. **Ambientes IoT / Edge Computing:** Dispositivos e dashboards Web que precisam se comunicar com o smartphone sem infraestrutura de roteador gerenciável ou servidores em nuvem.
3. **Consistência:** Alinha o comportamento do `nano-spring` com o que os desenvolvedores esperam de soluções Zeroconf modernas (como as de Raspberry Pi, ESP32 e dispositivos Apple).

---

## 6. Critérios de Aceite

1. **Navegador Web:** Digitar `http://<serviceName>.local:<port>/` no Google Chrome, Firefox ou Safari (em Windows 10/11, macOS ou Linux) em um computador conectado na mesma rede Wi-Fi deve carregar a página inicial sem erro `DNS_PROBE_FINISHED_NXDOMAIN`.
2. **Limpeza de Recursos:** Ao invocar `server.stop()`, o `MulticastLock` deve ser liberado imediatamente para não drenar a bateria do aparelho.
3. **Fallback Elegante:** Se a permissão `CHANGE_WIFI_MULTICAST_STATE` não estiver presente ou a interface Wi-Fi estiver desconectada, o servidor deve logar um aviso sem quebrar a inicialização do container HTTP.
4. **Compatibilidade:** Funcionar a partir do Android 5.0 (API 21) até o Android 14+ (API 34+), incluindo dispositivos legados como o Moto G4 Play (Android 7.1.1).
