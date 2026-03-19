# EasyChat

Aplicativo de mensagens instantâneas para Android desenvolvido na disciplina **Programação para Dispositivos Móveis** da Universidade Federal de Uberlândia (UFU) — Grupo 9.

---

## 👥 Equipe

| Membros |
|---|---|
- Artur Batalini Coelho Alvarim 
- Luiz Alexandre Anchieta Freitas 
- Lucas Duarte Soares 
- Vitor Hugo Rocha Curcino 

---

## 📱 Funcionalidades

- **Autenticação** via telefone com OTP (SMS)
- **Mensagens em tempo real** com sincronização via Supabase Realtime
- **Status de mensagem**: enviada (✓), entregue e lida (✓✓)
- **Envio de mídia**: imagens, áudios e vídeos
- **GPS**: envio de localização atual na conversa
- **Câmera**: captura e envio de fotos diretamente pelo app
- **Microfone**: gravação e envio de mensagens de voz
- **Notificações push** via Firebase Cloud Messaging (FCM)
- **Grupos**: criação, gerenciamento de membros, adição e remoção
- **Busca de usuários** por username e importação de contatos do dispositivo
- **Perfil do usuário**: foto, username e mensagem de status
- **Mensagens fixadas** no topo do chat (requisito especial)
- **Filtro de mensagens** por palavra-chave com destaque visual (requisito especial)
- **Criptografia** de mensagens de texto com AES/CBC
- **Armazenamento local** com Room para funcionamento offline/cache
- **Logout** com encerramento seguro de sessão

---

## 🏗️ Arquitetura

O projeto adota **MVVM (Model-View-ViewModel)** com fluxo de estado unidirecional:

```
UI (Compose Screens)
    ↕ StateFlow / collectAsStateWithLifecycle
ViewModels
    ↕
Repositories (ChatRepository, UserRepository, MediaRepository)
    ↕                          ↕
Supabase (remoto)         Room / LocalMessageRepository (local)
```



## 🛠️ Tecnologias e Bibliotecas

| Tecnologia | Uso |
|---|---|
| **Supabase** (Postgrest, Auth, Realtime, Storage) | Backend: banco PostgreSQL, autenticação OTP, mensagens em tempo real, armazenamento de mídia |
| **Firebase Cloud Messaging (FCM)** | Notificações push |
| **Jetpack Compose + Material 3** | Interface declarativa |
| **Room (SQLite)** | Cache local de mensagens |
| **Google Play Services Location** | Obtenção de coordenadas GPS |
| **Android MediaRecorder** | Gravação de áudio (MPEG-4/AAC) |
| **ImagePicker (Dhaval2404)** | Seleção e captura de imagens |
| **Coil Compose** | Carregamento assíncrono de imagens |
| **Country Code Picker (CCP)** | Seletor de código de país no login |
| **CryptoManager (AES/CBC)** | Criptografia simétrica de mensagens de texto |

---

## ⚙️ Configuração e Execução

### Pré-requisitos

- Android Studio Hedgehog ou superior
- Android SDK 26+
- Conta no Supabase e projeto configurado
- Projeto Firebase com FCM habilitado

### Passos

1. Clone o repositório:
```bash
git clone https://github.com/duartelucas03/chatDnd.git
cd chatDnd
```

2. Adicione o arquivo `google-services.json` do seu projeto Firebase em:
```
app/google-services.json
```

3. Configure as credenciais do Supabase em `SupabaseClientProvider.kt`:
```kotlin
val client: SupabaseClient = createSupabaseClient(
    supabaseUrl = "SUA_URL_SUPABASE",
    supabaseKey = "SUA_CHAVE_SUPABASE"
)
```

4. Abra o projeto no Android Studio, sincronize o Gradle e execute no dispositivo ou emulador.

---

## 🧪 Como Testar

> ⚠️ **Atenção:** O provedor de SMS utilizado (Vonage) não envia OTP para números brasileiros. Para testar, utilize os números previamente cadastrados no Supabase com OTP fixo.

### Credenciais de teste

| Número | OTP |
|---|---|
| +55 34 99999-9999 | 123456 |

**Passos:**
1. Abra o app
2. Informe o número `+55 34 99999-9999` na tela de login
3. Na tela de OTP, informe `123456`
4. Defina um username e acesse o app

---

## 📂 Estrutura do Banco de Dados (Supabase)

### Tabelas principais

- **users** — id, username, phone, fcm_token, status, status_message, avatar_url, last_seen
- **chatrooms** — id, is_group, name, avatar_url, last_message, last_message_at, last_message_type, created_by
- **chatroom_members** — chatroom_id, user_id, role, last_read_at, joined_at
- **messages** — id, chatroom_id, sender_id, content, type, media_url, location_lat, location_lng, is_pinned, status, local_id, is_synced, created_at

### Buckets de Storage

- **avatars** — fotos de perfil dos usuários
- **chat-media** — imagens e vídeos enviados no chat
- **audio-messages** — mensagens de voz

---

## 🔐 Segurança

As mensagens de texto são criptografadas com **AES/CBC/PKCS5Padding** antes de serem gravadas no banco. A descriptografia ocorre localmente no dispositivo no momento da exibição. Mensagens de mídia (imagem, áudio, vídeo) e localização não são criptografadas — apenas a URL de referência é armazenada.


---

## APK

O apk está no caminho: 

```
app/release
```

---