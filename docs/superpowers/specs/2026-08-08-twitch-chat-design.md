# Twitch Chat app — design spec (2026-08-08)

Target release: **1.11.0** (batched into the open pairing break by
user decision — ships to testers as beta.2). Read-only viewer of a
selected Twitch channel's chat, on the tablet's three surfaces.

## Summary

New launcher program **Twitch Chat** (`Program.TWITCH`, id 27, key
`"twitch"`, chip 0xFF9146FF — Twitch purple, icon
`minecraft:amethyst_shard`), App Store distribution. Shows a chosen
channel's live chat: held GUI, overlay pin (HUD chat while playing),
and placed tablets as a chat board tuned to one channel for every
viewer. **Read-only forever until explicitly revisited**: anonymous
guest access to Twitch's public chat relay — no account, no OAuth, no
tokens, nothing stored.

## Connection layer

`client/TwitchChatService` (client-only, the ClockService precedent):

- ONE TLS socket to `irc.chat.twitch.tv:6697`, anonymous guest nick
  (`justinfan` + random digits), `CAP REQ :twitch.tv/tags` for
  display-name + color tags, plain line-based IRC subset: NICK, CAP,
  JOIN/PART, PRIVMSG parse, PING→PONG. No third-party dependencies
  (twitch4j rejected as jar-in-jar bloat; JDK WebSocket unnecessary —
  the raw socket speaks the same protocol with less framing).
- One daemon reader thread; writes (JOIN/PART/PONG) from the client
  thread through a synchronized writer. All UI-visible state
  (buffers, status) is handed to the client thread — no rendering
  from the socket thread.
- The single socket JOINs every channel currently watched; channels
  are ref-counted by consumer surfaces and PARTed when the last
  consumer leaves. **No consumer = socket closed.** The mod never
  talks to Twitch while nothing displays chat.
- Per-channel ring buffer of the last 100 messages
  (`record ChatMessage(String user, int color, String text, long
  clientTick)`); buffer replaces oldest.
- Reconnect with exponential backoff (2s → 60s cap) on socket death;
  rejoins all wanted channels on reconnect. Status per channel:
  CONNECTING / LIVE / OFFLINE (shown as a quiet status line, never
  log spam — at most one INFO per state change).
- Channel names normalized lowercase, `#` prefix added on the wire,
  input validated to Twitch's charset (`[a-zA-Z0-9_]{1,25}`).

## Channel selection

Two layers, the theme idiom throughout:

- **Personal** (held/slot views + overlay): client pref
  `twitch.channel` in ClientPrefs — per-player, like Clock's zones.
  Empty = unset, screen shows the channel box hint.
- **Placed tablet**: `twitch_channel` STRING component + BE NBT tag
  `"twitch_channel"` (absent = unset, never written empty), item↔block
  round-trip like the probe list. Set from a block-bound GUI via
  `SetTwitchChannelPayload(SignalTarget, String)` — the
  handleSetTheme both-target shape, server validates the charset and
  length (25). Registrar **"21" → "22"** (inside the 1.11.0 break;
  each wire growth gets its own fence).
- Kiosk face shows the BLOCK's channel for every viewer; each
  viewer's client makes its own anonymous connection. The server
  never connects to anything.

## Surfaces

- **`TwitchScreen`**: channel ChromeEditBox at top (click-to-focus
  handled — the standalone-EditBox lesson), scrolling message list
  (username in its Twitch color from tags, message text wrapped via
  font.split), standard pin + Home header. Block-bound views edit the
  BE channel (payload); item views edit the client pref.
- **Overlay pin** (`TwitchOverlayContent`): compact recent-messages
  pane — the streamer's HUD chat. Reads the same service buffers.
- **Kiosk face** (`renderTwitchFace`): text chat wall — background
  fills then text only (three-pass rule trivially satisfied; no
  items). Rows capped to glass height, newest at the bottom. Face
  tap opens the GUI via the existing generic branch. The face
  registers itself as a service consumer only while actually
  rendered (BER-side presence, expiring a few seconds after the last
  render so chunk culling parts the channel).

## v1 limits (explicit)

- Text only: Twitch emotes render as their text names; no image
  fetching, no badges. Clean future upgrade.
- No sending, no accounts, no tokens — read-only is the product.
- No moderation/filtering: chat is live unfiltered internet content;
  whoever sets the channel curates the wall. (Family-server note:
  channel choice on a placed tablet is open to anyone who can open
  its GUI, same trust model as signal editing.)
- Addon API untouched.

## Components touched

- `Program` enum: `TWITCH(27, "twitch", 0xFF9146FF,
  "minecraft:amethyst_shard")` + lang (`program.linktablet.twitch`
  + `.desc`, `gui.linktablet.twitch.*` strings).
- New `client/TwitchChatService`, `client/screen/TwitchScreen`,
  `client/screen/TwitchOverlayContent`, `renderTwitchFace` in
  TabletScreenRenderer + dispatch case in TabletBlockEntityRenderer +
  contentFor/screenFor cases.
- `ModDataComponents.TWITCH_CHANNEL` (String), BE field + NBT +
  round-trip, `SetTwitchChannelPayload` + handler, registrar "22".
- ClientPrefs `twitch.channel`.

## Test matrix (beta.2 additions)

- Set a live channel on a held tablet → chat flows; pin the overlay →
  chat on HUD while playing.
- Wall tablet: set channel from its GUI → face shows chat; second
  account sees the same wall (its own connection).
- Connection hygiene: close every Twitch surface → socket closes
  (netstat or log line); reopen → rejoins.
- Kill the network mid-chat → OFFLINE status, auto-reconnect when it
  returns.
- Misspelled/empty channel → "no messages yet" / hint, no errors.
- Break + re-place a channel-set tablet → channel survives.
- Regression: registrar "22" pairing break, old-world load, the
  1.11.0 matrix still green.
