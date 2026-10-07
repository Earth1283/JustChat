# JustChat

A chat formatter for Minecraft that keeps formatting when its integrations fail.

Runs on **Spigot, Paper and Folia** (Minecraft 1.20.6+, Java 21+). No required plugin dependencies, nothing downloaded at runtime: Kotlin, Adventure/MiniMessage, Gson and the SQLite driver all ship inside the jar (relocated where a conflict is plausible)s

A server with only JustChat installed still gets public chat formatting, built-in placeholders, colors, private messages, replies, ignore, config reload, diagnostics and persistent state.

## Install

Drop `JustChat-<version>.jar` into `plugins/`. Optionally add any of LuckPerms, Vault (or VaultUnlocked), PlaceholderAPI; they are soft dependencies, picked up whether they load before or after JustChat.

## Commands

| Command | Permission (default) | |
|---|---|---|
| `/msg`, `/tell`, `/w`, `/m`, `/whisper`, `/pm <player> <message>` | `justchat.msg` (everyone) | Private message |
| `/reply`, `/r <message>` | `justchat.msg` | Reply to the last conversation partner |
| `/ignore <player>`, `/unignore <player>`, `/ignorelist` | `justchat.ignore` (everyone) | Ignore management |
| `/justchat color <color\|#rrggbb\|reset>` | `justchat.color` (op) | Preferred message color |
| `/justchat status` | `justchat.admin` (op) | Facts only; hover any label or value for its meaning in a tooltip |
| `/justchat doctor` | `justchat.admin` | Problems, their impact and a suggested action |
| `/justchat reload` | `justchat.admin` | Reload `config.yml` |
| `/justchat version` | `justchat.admin` | Admin permissions |

Other permissions: `justchat.chat.color` (op) allows `&` / `&#rrggbb` codes in messages, `justchat.chat.minimessage` (op) allows MiniMessage color and decoration tags, `justchat.ignore.bypass` (op) makes a player impossible to ignore.

## Configuration

See the commented [`config.yml`](src/main/resources/config.yml). Highlights:

```yaml
chat:
  format: "<prefix><player> <dark_gray>»</dark_gray> <message>"
private-messages:
  sender-format: "<gray>[You → <receiver>]</gray> <message>"
  receiver-format: "<gray>[<sender> → You]</gray> <message>"
placeholders:
  unresolved: KEEP        # KEEP | EMPTY
metadata:
  default-prefix: ""
  default-suffix: ""
  default-group: "default"
continuity:
  sqlite: true
```

### Placeholders

Built in (never need any plugin): `<player>` and `<username>` (account name), `<display_name>`, `<message>`, `<world>`, `<prefix>`, `<suffix>`, `<group>`, `<uuid>`. Private message formats also get `<sender>`, `<receiver>`, `<receiver_display_name>`, `<receiver_prefix>`, `<receiver_suffix>`, `<receiver_group>`; the plain placeholders there describe the sender.

`<prefix>`, `<suffix>` and `<display_name>` accept legacy `&` codes and `&#rrggbb`.

With PlaceholderAPI installed, `%placeholder%` tokens in formats are expanded for the sender. Without it, or when a placeholder cannot be resolved, `placeholders.unresolved` decides: `KEEP` leaves the raw text, `EMPTY` removes it. JustChat never invents a value. Message text typed by players is never expanded as a placeholder or template.

Placeholders cannot be used inside `<gradient>` or `<rainbow>`; such a format is rejected at load time with a clear error.

## How JustChat behaves when things break

No integration failure disables JustChat.

| What fails | What happens |
|---|---|
| LuckPerms goes away | Vault is used if present, otherwise the continuity cache |
| Vault goes away | Continuity cache |
| PlaceholderAPI goes away | Built-in placeholders keep working; `%...%` follow `placeholders.unresolved` |
| SQLite fails | Memory cache keeps serving; writes are queued and retried with backoff |
| Every metadata source fails | `metadata.default-*` |
| `/justchat reload` with an invalid file | Last-known-good configuration stays active |
| Configured format is unusable | Hardcoded emergency format: `<gray><player></gray> <dark_gray>»</dark_gray> <white><message></white>` |

Metadata resolution order: LuckPerms API, Vault API, memory cache, SQLite-seeded cache, configured defaults. LuckPerms is preferred, not required. A healthy Vault chat provider (including VaultUnlocked) means the metadata capability is healthy.

The chat hot path never touches disk or network. SQLite is read once per player at pre-login (off the main thread, with a timeout) to seed the memory cache, and written asynchronously, debounced and batched.

## Ignore: what it does and does not do

`/ignore` affects **JustChat-owned communication only**: public chat that JustChat delivers and JustChat private messages. It cannot hide messages sent by other plugins, vanilla commands (for example `/minecraft:msg`, so it's advised to disable them manually via LuckPerms), broadcasts, or death/join messages. An ignored player is not told they are ignored: their private message echoes to them as normal but is not delivered.

## Compatibility notes

- JustChat cancels `AsyncPlayerChatEvent` at `HIGHEST` priority and delivers the formatted component itself. Plugins listening at `MONITOR` (chat loggers, bridges) see a cancelled event unless they opt in to cancelled events. Messages are not cryptographically signed as vanilla signed chat.
- `/msg`, `/tell` and `/w` are registered as plugin commands and take precedence over the vanilla commands of the same name.
- Persisted data (ignore lists, color preferences, last-known metadata) lives in `plugins/JustChat/justchat.db`. With `continuity.sqlite: false` it lives in memory only.

## Building

```
./gradlew build        # compile, run tests, produce build/libs/JustChat-<version>.jar
./gradlew test         # unit + chaos tests (no server needed)
./gradlew benchmark    # render-path microbenchmark
./gradlew runServer    # Paper 1.20.6 dev server with the plugin
```

The chaos scenarios (provider outage, restart without the provider, provider restored, SQLite failure) are part of the normal test suite and run in CI.

### Measured render cost

Single thread, cached or live metadata, default format (`./gradlew benchmark`):

| | p50 | p99 | allocation |
|---|---|---|---|
| JustChat pipeline, cached metadata | 0.33 µs | 0.66 µs | ~0.9 KB/msg |
| JustChat pipeline, live metadata | 0.56 µs | 4.4 µs | ~0.9 KB/msg |
| Parsing MiniMessage on every message | 9.7 µs | 29.8 µs | ~14.9 KB/msg |

Note this measure formatting performance only.
