# vLogin

Plugin de autenticação para Minecraft, escrito do zero. Uma única jar roda em
**Paper/Spigot/Bukkit 1.8 – 1.21.x**, **Folia**, **BungeeCord** e **Velocity**.

Não há loader: a mesma jar carrega os três descritores (`plugin.yml`, `bungee.yml`,
`velocity-plugin.json`) e cada plataforma escolhe o seu. As dependências vêm
embutidas e renomeadas, então não há conflito com outros plugins.

---

## Instalação

Copie `vLogin-1.1.0.jar` para a pasta `plugins/`. Na primeira execução são criados:

```
plugins/vLogin/
├── config.yml            tudo em um arquivo só
├── secret.key            assina as mensagens entre proxy e backend
└── messages/
    ├── pt-br.yml
    └── en.yml
```

Requisitos: Java 8+ no backend, Java 17+ no Velocity.

---

## Redis (mais de um proxy)

Sem Redis, cada proxy conhece apenas as próprias sessões: quem troca de proxy
autentica de novo, e a checagem de "já está online" não enxerga o outro lado. Com
Redis, **login, logout, sessões e presença são compartilhados** entre todos os nós.

```yaml
redis:
  enabled: true
  host: "127.0.0.1"
  port: 6379
  username: ""      # vazio = autentica só com a senha (Redis < 6)
  password: "..."
  channel: "vlogin" # prefixo dos canais e chaves
```

Ligue nos proxies; nos backends é opcional (serve para propagar logout e kick).

O que roda por cima disso:

- **Presença**: chave com TTL renovado. Um nó que morre não deixa ninguém preso
  como "online" para sempre.
- **Sessões**: chave com TTL. Quem já logou entra direto em qualquer proxy.
- **Eventos**: `LOGIN`, `LOGOUT`, `KICK`, `ACCOUNT_CHANGED`, `RELOAD` por pub/sub.
  Cada nó ignora o próprio eco.
- **Queda do Redis não trava login.** Toda operação degrada para no-op: sem Redis a
  rede fica mais burra, não fechada. O assinante reconecta sozinho.

---

## Folia

Suportado, com `folia-supported: true` no descritor.

Folia divide o servidor em regiões que tickam em threads diferentes: não existe uma
"main thread", `Bukkit.getScheduler()` lança exceção, e um jogador só pode ser tocado
pela thread que está tickando a região dele. O plugin resolve isso em
[FoliaSupport.java](bukkit/src/main/java/br/vituz/core/vlogin/bukkit/compat/FoliaSupport.java):

- Trabalho ligado a um jogador (inventário, limbo, kick, título) vai para o
  `EntityScheduler` **daquele** jogador.
- **Todo envio ao jogador** (mensagem, action bar, título, som) passa pelo
  `EntityScheduler` dele. Boa parte do núcleo roda fora da thread do jogador
  (callbacks do storage, o contador de 1 segundo), e escrever de lá é o que produz
  os avisos `Failed to deliver packet` do servidor.
- Trabalho solto vai para o `GlobalRegionScheduler`.
- Tarefas assíncronas usam o `AsyncScheduler`.
- Teleporte usa `teleportAsync`, obrigatório entre regiões.
- Esconder jogadores agenda cada lado no dono correspondente. O par
  "esconder A de B" e "esconder B de A" roda em duas threads diferentes.

Como o módulo compila contra a API 1.8, tudo isso é resolvido por reflexão em tempo
de execução; em servidor comum cai no scheduler normal do Bukkit.

---

## Comandos

### Jogador

| Comando | Função |
|---|---|
| `/logar <senha>` | Autentica |
| `/registrar <senha> <senha>` | Cria o registro |
| `/trocarsenha <atual> <nova>` | Altera a própria senha (também `/mudarsenha`) |
| `/deslogar` | Encerra a sessão |
| `/original` | Vincula a conta ao UUID da Mojang |
| `/offline` | Desvincula |

Os nomes vêm de `commands.aliases` no config e podem ser trocados.

**Não existe `/desregistrar` para o jogador.** Remover conta é operação de
administrador, e por padrão só pelo console.

### Administrador: `/vlogin <sub>` (permissão `vlogin.admin`)

| Subcomando | Função |
|---|---|
| `info <jogador>` | Ficha completa da conta |
| `diagnose` | Reúne os dados para suporte (versões, storage, Redis, contadores) |
| `create <jogador> <senha>` | Cria a conta |
| `delete <jogador>` | Apaga a conta |
| `setpass <jogador> <senha>` | Define a senha de alguém |
| `setuuid <jogador> <uuid\|offline\|premium>` | Define o UUID |
| `resetip <jogador>` | Esquece o IP guardado |
| `unblock <ip>` | Tira o bloqueio por tentativas |
| `grant <jogador>` | Libera a entrada sem senha |
| `spawn set` | Marca onde o login acontece |
| `prune <dias> [confirmar]` | Apaga contas paradas |
| `export` | Grava todas as contas em um arquivo |
| `import confirmar` | Traz contas de outro banco |
| `nlogin confirmar` | Traz as contas do nLogin |
| `reload` · `help` · `version` | |

`prune` e `import` sempre mostram o resultado antes de agir: `prune` sem
`confirmar` só conta quantas contas seriam apagadas.

### Console-only

```yaml
commands:
  console-only: [create, delete, setpass, setuuid, grant, prune, import, nlogin, export]
```

Tudo que pode tomar, apagar ou re-chavear uma conta está nessa lista. Uma conta de
administrador comprometida in-game não deve conseguir entregar o login de outra
pessoa. `info` e `diagnose` ficam de fora porque são só leitura.

### Exemplo de `/vlogin info`

```
┌ JogadorExemplo
│ situação · aguardando senha / conta original
│ visto em · 05/08/2026 - 01:47:10 (17s atrás)
│ criada em · 30/06/2026 - 00:47:27 (36d 1h atrás)
│ identidade · 3f2a91c4d8e74b5aa1c6f0b923d47e18 mojang
│ endereço · 203.0.113.40
│ mesmo endereço · JogadorExemplo_alt, .BedrockExemplo
└ 6.80 ms
```

"mesmo endereço" lista as outras contas vistas do mesmo IP: os alts, e a conta
Bedrock ao lado da Java; some quando não há nenhuma. Cada rótulo é uma chave em
`messages/`, então dá para traduzir e reformatar.

---

## Migrando de um plugin de login existente

Duas formas.

**Apontar para a tabela que já existe** (sem copiar nada):

```yaml
storage:
  driver: mysql
  sql:
    host: "seu.host"
    port: 3306
    database: "nlogin"
    username: "usuario"
    password: "senha"
  schema:
    table: "nlogin"
    columns:
      id: "ai"
      username: "last_name"
      uuid: "unique_id"
      premium-uuid: "mojang_id"
      bedrock-uuid: "bedrock_id"
      password: "password"
      address: "last_ip"
      last-login: "last_seen"
      registered-at: "creation_date"
      email: "email"
      discord: "discord"
      flags: "settings"
```

**Ou copiar para uma tabela própria.** Preencha só a conexão de origem:

```yaml
storage:
  import:
    preset: nlogin       # já sabe a tabela e as doze colunas do nLogin
    driver: mysql
    sql:
      host: "seu.host"
      port: 3306
      database: "nlogin"
      username: "usuario"
      password: "senha"
```

e rode no console:

```
/vlogin nlogin confirmar
```

O `preset: nlogin` existe para você não ter que descobrir e digitar doze nomes de
coluna certos. Errar um só apareceria depois, como conta sem senha.

O que a migração garante:

- **A origem é aberta somente para leitura.** Nenhuma tabela criada, nenhuma coluna
  alterada, nada apagado. Se algo der errado no meio, o nLogin continua funcionando
  exatamente como estava, e dá para voltar atrás só trocando a jar.
- **Nickname que já existe aqui nunca é sobrescrito**, então rodar duas vezes não
  duplica nem apaga nada. Nicknames repetidos na origem (o nLogin aceita duas contas
  que só diferem em maiúsculas) entram uma vez, pela conta mais antiga.
- **Os hashes vão byte a byte.** Ninguém troca de senha por causa da migração.
- **Uma conta problemática não derruba as outras**: o lote é a via rápida, e quando
  ele é recusado a cópia refaz aquele trecho conta a conta. O relatório final diz
  quantas entraram, quantas já existiam e quantas ficaram para trás.

Para copiar de outro vLogin em vez do nLogin, troque para `preset: vlogin` e use
`/vlogin import confirmar`. Com `preset: custom`, o layout vem de
`storage.import.schema`.

Nos dois caminhos:

- **Colunas que faltarem são criadas** na inicialização (`ALTER TABLE`), no destino
  nunca na origem.
- **O formato de cada hash é detectado individualmente**, pelo prefixo, não pela
  opção `algorithm`. BCrypt (`$2a$`/`$2y$`/`$2b$`), Argon2 (`$argon2id$`/`i`/`d`),
  PBKDF2, SHA-256/512 e MD5 convivem na mesma tabela. A opção `algorithm` decide só
  o formato das senhas **novas**; um hash em formato antigo é regravado no formato
  atual no primeiro login bem-sucedido.

Faça backup antes (`/vlogin export` serve).

---

## Proxy (BungeeCord / Velocity)

1. Instale a mesma jar no proxy e nos backends.
2. Copie o `secret.key` do proxy para todos os backends, porque é ele que assina as
   mensagens. Sem isso, um cliente modificado poderia forjar um "já autenticado".
3. No backend: `network.role: backend`.
4. No proxy: preencha `network.auth-servers`.

As mensagens levam timestamp e HMAC-SHA256; qualquer coisa que falhe em uma das duas
checagens é descartada, e mensagens vindas de um cliente nesse canal são recusadas
nas duas pontas.

---

## Contas originais (premium)

Com `accounts.premium.mode: verify` ou `exclusive`, o vLogin consulta a Mojang no
pré-login e força autenticação online **daquela conexão** (`setOnlineMode(true)` no
BungeeCord, `forceOnlineMode()` no Velocity). Quem provar a posse do nickname entra
sem senha.

Se a Mojang estiver fora do ar, o nickname **não** é rebaixado para offline. É
exatamente assim que roubo de nickname acontece. Cache de 30 minutos, backoff de 2
minutos em falha.

### Paper sozinho, sem proxy

Num Paper/Spigot offline não existe API para isso: quando o `AsyncPlayerPreLoginEvent`
dispara, a fase de login já terminou, e a decisão de criptografar acontece antes,
dentro da pipeline de rede.

Com `accounts.premium.standalone-auth: true` o vLogin entra nessa pipeline, segura o
pacote de entrada e, quando o nickname é de uma conta original, coloca o login no
mesmo trilho de um servidor `online-mode=true`. Daí em diante quem trabalha é o
próprio servidor: ele pede a criptografia, confere no sessionserver da Mojang e troca
o perfil pelo verdadeiro. **A skin vem junto porque vem do perfil**, não de código
nosso. Quem não é original nem chega a ser incomodado e segue registrando com senha.

Nada disso precisa de ProtocolLib ou PacketEvents.

O recurso não é instalado quando o servidor já está em `online-mode`, quando está
atrás de um proxy (aí quem autentica é o proxy) ou quando a versão não é compatível.
Nos três casos o console diz o motivo e todo mundo entra com senha: **falhar aqui
nunca abre uma conta**.

Uma conta que já existe com senha e não está marcada como original **não** é promovida
sozinha, senão o dono do nickname na Mojang tomaria a conta de quem registrou antes.
Para vincular, o jogador roda `/original` uma vez.

---

## Notas de segurança

Decisões que parecem detalhe mas seguram ataques reais:

- **Login premium sem senha só onde há prova.** Reconhecer que um nickname é premium
  não prova nada: em servidor offline qualquer um digita aquele nome. E o UUID que
  chega na conexão também não prova, porque quem conecta escolhe o que manda. Vale
  apenas o que o transporte comprovou: o proxy que forçou online mode, o servidor em
  `online-mode=true`, ou o handshake que o próprio vLogin fez com a Mojang.
- **Verificação na Mojang falha fechada.** Se a consulta não responde, a entrada é
  recusada em vez de tratada como conta offline. O contrário é como nicknames premium
  são roubados.
- **Contadores de tentativa decaem por endereço, nunca em massa**, e um login
  bem-sucedido não zera o contador do IP, senão bastaria ter uma conta própria para
  reiniciar o limite entre chutes na conta alheia.
- **Mensagens entre proxy e backend são assinadas e de uso único.** Elas trafegam pela
  conexão do jogador, então um cliente modificado consegue vê-las: a assinatura impede
  alterar, e o nonce impede reenviar.
- **Se o plugin não sobe, o servidor recusa conexões.** Desabilitar e seguir aceitando
  jogadores deixaria todas as contas abertas. Rejeitar é barulhento de propósito.
- **Nunca há senha em texto claro** em log, backup ou no comando `verify`.

- **Senha digitada não vai para o log.** O servidor escreve
  `Fulano issued server command: /logar hunter2` no console e no arquivo de log
  *antes* de qualquer plugin poder cancelar, então a única saída é filtrar o próprio
  logger, e é o que `commands.redact-passwords-in-log` faz. Um `/logar` sem argumento
  continua aparecendo, para o histórico de quem tentou entrar.
- **Falha do banco não vira conta nova.** Se a consulta falha no login, a conexão é
  recusada em vez de tratada como conta inexistente; e o registro reconfere no banco
  logo antes de gravar. Sem isso, uma instabilidade momentânea deixaria alguém
  definir senha em cima de conta alheia.
- **Comandos vindos de backend estão desligados por padrão** (`@proxy`): é um console
  remoto, e um único backend comprometido dominaria a rede.

- **Nickname é único no banco**, por índice do próprio SQL. A checagem no código
  fecha quase toda a janela de dois registros simultâneos; só a restrição do banco
  fecha por inteiro.
- **Autenticar é operação de uma vez só.** Dois caminhos podem chegar juntos (o
  `/logar` e uma mensagem do proxy), e sem isso os comandos pós-login rodariam duas
  vezes (kit duplicado, por exemplo).

Riscos que continuam sendo seus:

- **Sessão vale por IP.** Quem estiver atrás do mesmo IP público da vítima (CGNAT,
  rede da escola, mesma casa) consegue retomar a sessão dela dentro da janela. É
  assim em qualquer plugin de login que reaproveite sessão. O mesmo IP é justamente
  como o jogador é reconhecido, e não há como separar duas pessoas atrás do mesmo
  endereço. Os três ajustes estão em `login.session` (veja a seção abaixo).
- **`same-address-relogin` permite derrubar quem está no mesmo IP.** É o que faz a
  reconexão funcionar depois de uma queda; o efeito colateral é que alguém na mesma
  rede pode desconectar você repetidamente.
- **Os arquivos de `/vlogin export` contêm os hashes das senhas.** Trate a pasta
  `plugins/vLogin/backups/` como material sensível.

- Backends precisam estar atrás de firewall. Com `role: backend` as checagens de
  pré-login ficam a cargo do proxy; quem alcançar o backend direto contorna essa
  parte (a senha continua sendo exigida).
- `login.session.bind-to-address: false` deixa qualquer um retomar a sessão de quem
  saiu há pouco. O padrão é `true` por isso.

## Bedrock (Floodgate)

Com o **Floodgate** instalado, contas Bedrock entram direto: ele já autenticou a
conta Xbox antes de o jogador chegar, então o vLogin não pede senha.

```yaml
accounts:
  bedrock:
    auto-login: true          # entra sem senha
    skip-password: true       # dispensa o registro
    floodgate:
      enabled: true
      username-prefix: "."    # igual ao do config do Floodgate
      username-pattern: '(\.?[a-zA-Z0-9_ ]{1,20})'
      keep-stored-uuid: false
      require-linked: false
```

O `username-pattern` separado **é necessário**, não é enfeite: gamertags chegam com o
prefixo do Floodgate e podem ter caracteres que o padrão dos jogadores Java recusa:
sem ele, todo Bedrock levaria "nickname inválido" antes mesmo de entrar.

`keep-stored-uuid` preserva o UUID já gravado em vez de adotar o do Floodgate. Serve
para quem trouxe contas de outro plugin e tem inventário, economia ou permissões
presos ao UUID antigo.

`require-linked` só autentica automaticamente quem tem conta Java vinculada no
Floodgate. Quem não tiver segue pelo caminho com senha.

**Sem o Floodgate instalado**, o plugin não quebra, cai no reconhecimento pelo
formato do UUID (o Floodgate zera os 8 primeiros bytes) e avisa no console.
`/vlogin diagnose` mostra qual dos dois está em uso, e `/vlogin info` passa a exibir
o XUID, o gamertag e a conta Java vinculada.

## Sessão de login

Quem sai e volta dentro do prazo não digita a senha de novo. Três opções controlam
isso, em `login.session`:

```yaml
login:
  session:
    enabled: true          # false → senha em toda entrada
    minutes: 5             # janela em que a conta fica acessível sem senha
    bind-to-address: true  # exige o mesmo IP para retomar
```

| | `bind-to-address: true` (padrão) | `bind-to-address: false` |
|---|---|---|
| Mesmo IP | retoma sem senha | retoma sem senha |
| IP diferente | **pede a senha** | retoma sem senha |
| Custo | trocar de rede obriga a redigitar | qualquer um com o nick entra |

O modo solto existe para quem se incomoda de perder a sessão ao trocar de wi-fi para
dados móveis. Ele **não** serve para reduzir o risco de IP compartilhado. Faz o
oposto, porque libera qualquer conexão com aquele nickname. Para esse caso, mantenha
`true` e reduza `minutes`, ou desligue a sessão. O plugin avisa no console quando a
opção está desligada.

## O que está implementado

- Registro, login, troca de senha, logout, vínculo premium.
- SQLite, MySQL e MariaDB, com pool e schema configurável.
- Argon2id/i/d, BCrypt 2a/2y/2b, PBKDF2, SHA-256/512, MD5, com verificação por detecção
  de formato e regravação automática.
- Limbo: inventário escondido e restaurado, movimento travado, chat/comandos/
  interações bloqueados, jogadores não autenticados invisíveis, cegueira opcional,
  ponto de login configurável.
- Timeout com contagem na action bar, títulos e sons.
- Kick imediato ao errar a senha (`login.max-attempts: 1`), com o bloqueio de IP em
  um limiar separado (`login.lockout.after-attempts: 10`). Derrubar quem errou é
  uma coisa, bloquear o IP por quinze minutos por um erro de digitação é outra.
- Limite de contas por IP com isenções.
- Sessões por IP, compartilhadas via Redis quando ligado.
- Login automático de contas originais e Bedrock, com `skip-password`.
- Redis para redes com mais de um proxy.
- Folia.
- 16 subcomandos administrativos, incluindo backup, purge e migração.
- Comandos pós-login/pós-registro com `@console`, `@delay`, `@server` e `@proxy`.
- Mensagens em pt-BR e inglês, com cores `&` e hex `&#RRGGBB`.

## O que **não** está implementado

Nada disso aparece no config: o que está lá funciona. Listado para deixar o escopo
explícito:

- 2FA por e-mail ou Discord.
- Integração com a API do Floodgate. Contas Bedrock são reconhecidas pelo padrão de
  UUID do Floodgate e pela coluna `bedrock-uuid`, sem chamar o plugin.
- `username-appender` (prefixo/sufixo por tipo de conta).
- Redirecionamento para o último servidor conectado (`post-login` funciona).
- Diálogos nativos da 1.21.6+.
- Recuperação de senha por e-mail.
- Tradução automática pelo idioma do cliente.

Em `protection.hide-attributes`, são salvos e restaurados velocidade de
caminhada/voo e estado de voo; vida e saciedade são protegidas por cancelamento de
eventos, não salvas.

---

## Compilando

```bash
mvn install
```

A jar sai em `dist/target/vLogin-1.1.0.jar`. É preciso JDK 17+ (o módulo Velocity
exige 17); os demais são compilados com `--release 8`.

| Módulo | Alvo | Papel |
|---|---|---|
| `common` | Java 8 | Núcleo independente de plataforma |
| `bukkit` | Java 8 | Compilado contra a API 1.8.8; o resto por reflexão |
| `bungee` | Java 8 | BungeeCord |
| `velocity` | Java 17 | Velocity 3 |
| `dist` | | Junta tudo em uma jar, com relocation das dependências |

O pacote raiz é `br.vituz.core.vlogin`; as main classes são
`br.vituz.core.vlogin.bukkit.VLoginBukkit`, `...bungee.VLoginBungee` e
`...velocity.VLoginVelocity`, uma por descritor.

O módulo Bukkit é compilado contra a API **1.8.8** de propósito: assim nada mais novo
entra no bytecode por acidente. Títulos, action bars, sons, visibilidade, título de
inventário e os schedulers do Folia passam por uma camada de compatibilidade que
escolhe o caminho certo em tempo de execução, inclusive pacotes NMS diretos no
1.8–1.10, onde a API ainda não existia.

Dependências embutidas são renomeadas para `br.vituz.core.vlogin.libs.*` (SnakeYAML,
BouncyCastle, HikariCP, SLF4J, Jedis, commons-pool2, Gson). Drivers JDBC ficam sem
renomear de propósito: eles resolvem recursos pelo próprio nome de pacote.
