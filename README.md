# Pixi

Bichinho flutuante no topo (ou em qualquer lugar) da tela que reage a eventos locais.

## O que é

Port independente para Android inspirado no [Coucou](https://github.com/louis-cfm/coucou) (MIT).  
O personagem **Mochi**, nome, ícone e sons do Coucou **não** são usados — Pixi é original.

Ideias de interações de Dynamic Island inspiradas em projetos open-source como  
[SmartIsland](https://github.com/agupta07505/SmartIsland).

## Recursos

- **Posição livre**: arraste para qualquer lugar; ele fica onde você soltar.
- **Snap opcional para “ilha”**: se soltar perto do topo central, anima até a posição de Dynamic Island.
- **Moods / sprites**:
  - `idle` — respiração suave
  - `happy` — pulo alegre (toque)
  - `alert` — balança (notificação / bateria baixa)
  - `annoyed` — irritado (3 toques)
  - `dizzy` — tonto (2 toques)
  - `drag` — esmagado enquanto arrasta
  - `download` — progresso + seta
  - `sleep` — Zzz após inatividade
  - `charge` — ⚡ carregador conectado
- Sprites cartoon soft em `app/src/main/assets/sprites/` (64×64 frames em faixa horizontal).
- Fallback procedural se faltar PNG.
- Servidor local `127.0.0.1:8765` para scripts/Termux.
- NotificationListener detecta downloads e dispara animação.
- Broadcast de bateria baixa / carregador.

## Teste rápido (Termux)

```bash
curl "http://127.0.0.1:8765/event?type=alert"
curl "http://127.0.0.1:8765/event?type=download&value=40"
curl "http://127.0.0.1:8765/event?type=done"
curl "http://127.0.0.1:8765/ask?msg=Posso+rodar+isso"
```

Tipos: `alert` | `done` | `idle` | `sleep` | `charge` | `download` | `annoyed` | `dizzy`

## Build

Abra no Android Studio ou:

```bash
./gradlew assembleDebug
```

APK em `app/build/outputs/apk/debug/`.

Permissões necessárias:
1. Sobreposição (SYSTEM_ALERT_WINDOW)
2. Acesso a notificações (opcional, para downloads)

## Licença

MIT (reescrita independente). Veja NOTICE.md.
