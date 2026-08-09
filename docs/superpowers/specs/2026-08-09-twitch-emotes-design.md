# Twitch chat emotes — design spec (2026-08-09)

Target release: **1.11.0** (rides the current beta train as beta.6).
Emotes render inline in Twitch chat on all three surfaces — GUI
screen, overlay pin, kiosk wall faces — with the wall as the point.
**100% client-side: no wire, component, NBT, or registrar change**;
pairs with beta.4/beta.5 (registrar stays "23").

Scope decisions (user, 2026-08-09): native + third-party emotes
(7TV/BTTV/FFZ — "so it feels right"); all three surfaces from day one
("the wall is the point"); animated from day one with defensive caps
("cap the crazy ones"); plus a layout memo and an emotes on/off
toggle from the perf discussion.

## Summary

Twitch's IRC tags (already requested via `CAP REQ :twitch.tv/tags`)
carry an `emotes=` tag on every message: native emote ID + exact
character ranges — native detection is free. Third-party emotes
(7TV, BTTV, FFZ — the KEKW/catJAM layer that makes chat look like
chat) come from each service's public anonymous API, keyed by the
numeric Twitch channel ID, which arrives free in the `room-id`
message tag. Images are fetched anonymously from the services' CDNs,
decoded to sprite-sheet textures, and drawn inline. The no-accounts,
no-tokens, read-only model holds everywhere; the socket lifecycle
rules are untouched.

## Emote discovery

- **Native**: `Worker.handle` parses the `emotes=` tag
  (`id:start-end,start-end/id:...`) into per-message spans.
  `ChatMessage` grows a span list (emote ID + range). **Ranges are
  Unicode CODE POINT offsets**, not UTF-16 char indices — slicing
  must be code-point aware or any message containing an astral-plane
  character (🎉 etc.) shifts every span after it. Image URL is the
  documented CDN template
  (`static-cdn.jtvnw.net/emoticons/v2/<id>/<format>/dark/1.0`).
- **Third-party**: per-channel name→emote maps fetched async on first
  interest in a channel, once the channel's `room-id` is known (first
  message received — before that, third-party emotes simply render as
  text, then self-heal):
  - 7TV: `7tv.io/v3/users/twitch/<id>` + the global set endpoint.
  - BTTV: `api.betterttv.net/3/cached/users/twitch/<id>` + global.
  - FFZ: `api.frankerfacez.com/v1/room/id/<id>` + global.
  - Gson (shipped with Minecraft) for parsing; each service failing
    independently degrades to "that provider's emotes stay text" —
    never an error surface, at most one INFO per provider per
    channel.
- Channel sets are dropped when the channel is no longer wanted
  (the service's existing wanted-set lifecycle), re-fetched on
  re-interest.

## New service: `client/TwitchEmotes`

Client-only, beside `TwitchChatService` (which stays pure IRC —
emote state does NOT live in the socket class). Owns:

- **Per-channel emote maps** (above) plus the three global sets.
- **Texture cache**, keyed `provider:id`. States: UNREQUESTED →
  LOADING → READY → FAILED. On first render request: background
  fetch (small dedicated executor, 2 threads) of the **1x** image,
  GIF/PNG decode via `javax.imageio` (GIF = frame compositing with
  disposal-method handling — the known-fiddly part), composed into
  ONE vertical sprite-sheet `NativeImage`, uploaded on the render
  thread as a `DynamicTexture` registered under
  `linktablet:twitch_emote/...`.
- **Caps ("cap the crazy ones")**: 1x resolution only; max 40
  decoded frames (extras dropped); max source download ~256 KB;
  LRU cache of 128 emotes, evicted textures closed. Worst-case VRAM
  ~15 MB, typical a few MB.
- **Animation clock**: one pure function (wall time + per-emote frame
  delays → frame index), used by every surface, so GUI, overlay, and
  wall agree on the current frame. Sprite sheets mean animation is
  UV selection only — zero texture uploads after load.
- Cleared wholesale on logout (the service precedent).

## Tokenizer — the one layout source

`EmoteText.tokenize(message, channel)` → ordered segments, each
TEXT(string) or EMOTE(key): native spans applied first (code-point
math), then remaining text split on spaces and matched word-for-word
against channel + global third-party maps. **All three surfaces lay
out through this one function** — the `TabletScreenMath`/
`PaintCanvas` one-source rule applied to chat. Never fork the
matching into a renderer.

**Layout memo**: tokenization is cached per message (identity-keyed,
alongside the ring buffer's lifetime) and invalidated per channel
when a third-party set arrives — so it runs once per message, not
once per frame. The GUI's per-frame `buildRows` consumes cached
segments.

## Rendering

- **GUI (`TwitchScreen`) + overlay**: the `font.split` wrapping is
  replaced by a segment-aware line breaker (word-wrap over mixed
  text/emote runs; an emote is an unbreakable token). Emotes blit
  inline at line height (9 px tall; width = 9 × frame aspect, wide
  emotes proportionally wider). `LINE_H` unchanged. Unloaded /
  FAILED / toggle-off emotes render as their text name — chat never
  blocks on the network.
- **Wall face (`renderTwitchFace`)**: lines stay single-line,
  bottom-stacked, ellipsized — now at segment granularity. Emote
  quads are drawn via `RenderType.text(sheet)` — the same
  render-type family the font uses — so they live inside the
  existing text pass and the fills→items→text discipline is
  untouched (no new custom RenderType near the cached quad
  consumer). Per face: collect emote quads during the line loop,
  flush grouped by texture. The bleed inset rules (beta.5) apply to
  emote quads exactly as to text.
- **Toggle**: emote glyph button in the `TwitchScreen` header (by
  the pin), backed by `ClientPrefs` boolean `twitch.emotes`
  (default ON). OFF = beta.5's pure-text rendering everywhere,
  including walls and overlay — the potato-GPU self-serve valve.

## Performance envelope (from the design discussion)

- GPU: no per-frame uploads; +1 micro-batch per distinct visible
  emote texture (busy merged wall ≈ 15–25 — cheaper than the
  existing kiosk item-icon pass). VRAM LRU-capped (~15 MB worst).
- CPU: decode off-thread, once per emote; tokenize once per message
  (memo); per-frame cost is the same order as today's `font.split`.

## Risk register (honest)

1. **7TV WebP migration** — 7TV has been moving toward WebP/AVIF
   files; ImageIO cannot decode WebP. Mitigation: request GIF/PNG
   variants per the API's file listing, fall back to text on
   404/decode failure. **The FIRST implementation task is a spike**:
   measure how many of a real channel's 7TV emotes actually serve
   GIF/PNG. If coverage is bad, STOP and decide (WebP decoder dep vs
   partial 7TV coverage) before building on top.
2. **GIF disposal compositing** — ImageIO hands back raw frames +
   metadata; wrong compositing = flickering/ghosting emotes. Budget
   real test messages at it early.
3. **Third-party API shape drift** — cached endpoints are stable and
   community-standard, but each parser must fail soft (provider →
   text, never a crash).

## Non-goals (v1)

- No sub/follower emote *sending* — read-only forever (unchanged).
- No badges, no cheermotes, no emote tooltips/hover.
- No 2x/4x fetches, no per-emote settings, no disk cache (in-memory
  only; a relog re-fetches).
- No wire changes of any kind.

## Test matrix (beta.6 additions)

- Native emote mid-message renders on GUI, overlay, and wall; a
  message with an astral emoji BEFORE an emote keeps the span
  aligned.
- Heavy 7TV channel: emotes pop in as sets/textures load (text →
  image self-heal), animated ones animate in sync across GUI +
  wall simultaneously.
- Cap check: a 60+-frame 7TV monster animates with its frame cap,
  no hitch on first appearance (decode is off-thread).
- Toggle OFF → pure beta.5 text everywhere; ON again → emotes
  return without a relog.
- Kill the network after emotes are cached → chat goes OFFLINE as
  today; cached emotes still animate; new-emote fetches fail soft
  to text.
- Merged wall + rotated wall: emote quads respect the bezel bleed
  insets on all four edges (the beta.5 screenshot rule).
- Unknown/failed emote (dead CDN id) renders as text, one INFO max.
- Channel part → re-join: sets re-fetch, no stale cross-channel
  emote matches (channel A's 7TV names must not fire in channel B).
- Regression: username colors, wrapping indent, autoscroll,
  status lines, socket-lifecycle (close every surface → socket
  drops) all unchanged; registrar untouched — beta.6 client joins a
  beta.4/5 server.
