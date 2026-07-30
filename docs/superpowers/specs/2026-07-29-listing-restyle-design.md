# Listing description restyle — design spec (2026-07-29)

Restyle `docs/DESCRIPTION.md` to match the visual polish of the
Create: Lights & Controls Modrinth listing
(https://modrinth.com/mod/createlights-controls) while keeping every
word of the current 1.10.0 content. User-approved design 2026-07-29.

## Format decision

**Markdown + light inline HTML.** The body stays Markdown (readable
diffs, easy edits); HTML is used only where Markdown cannot center
content: the banner image, the badge row, and the gold stat line.
This renders on Modrinth (Markdown with inline HTML) and pastes into
the CurseForge editor exactly like the current text does. Rejected:
full raw HTML (unmaintainable), per-platform variants (identical
output, extra moving parts).

## New header block (top of the paste content)

1. **Banner** — centered image, `docs/images/banner.png`, embedded via
   `https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/banner.png`.
   Claude designs it (Create-brass / tablet-styled title graphic,
   produced by rendering styled HTML in the browser pane and
   screenshotting it). Replaceable anytime with a hand-made one.
   NOTE: the raw URL serves from `main` — the banner only renders on
   the live listings after the PNG is pushed to main.
2. **Badge row** — centered, devins-badges cozy style (height 56):
   - Available for NeoForge (no link)
   - Requires Create → links to Create's CurseForge page
   - Donate → generic donate badge, links to
     https://streamelements.com/fryd42/tip
3. `---` divider, then the existing bold tagline paragraph unchanged.

## Section restyle (text unchanged)

Every existing section keeps its exact prose and gains an emoji
header plus `---` dividers between sections:

| Current header | New header |
| --- | --- |
| A pocket OS | 📱 A pocket OS |
| Signals, not levers | 🎛️ Signals, not levers |
| Real Create frequencies | 📡 Real Create frequencies |
| Sliders you can feel | 🎚️ Sliders you can feel |
| Sticky notes for your factory | 📝 Sticky notes for your factory |
| Pin it to your HUD | 📌 Pin it to your HUD |
| The screen is real | ✨ The screen is real |
| Place it on the wall | 🧱 Place it on the wall |
| Aim it anywhere | 🎯 Aim it anywhere |
| One screen, many tablets | 🖥️ One screen, many tablets |
| Make it yours | 🎨 Make it yours |
| Getting started | 🏭 Getting started |
| Compatibility | 📦 Compatibility |

## New flourishes

- **Wow-stat line** in "🖥️ One screen, many tablets": centered, gold
  (`<p style="text-align:center;"><strong style="color:#f2c94c; font-size:1.3em;">…`),
  text: "🧮 A 4×3 wall = 12 merged tablets — 384 signals on one
  screen" (32 signals per merged tablet × 12; matches the existing
  body text's cap math).
- **Tip box**: the Quick-add bullet in "📡 Real Create frequencies"
  is promoted to a `>` blockquote ("📋 **Tip / quick-add:** …") with
  the same wording; the bullet is removed from the list.
- **❤️ Credits** section (new, before the footer): thanks to the
  Create team (link) whose Redstone Link network the mod builds on,
  plus a one-line tip-jar mention with the StreamElements link.
- **Footer**: "Created by **FryD42** · **MIT licensed** — free to use
  in any modpack." + the existing GitHub source/issues/changelog link.

## Untouched

- All body prose, feature claims, and image embeds (hero4,
  mounted-factory2, themes, dyed-cases2).
- The inert `<!-- 📸 SCREENSHOT -->` slot comments (4 open slots).
- The shooting checklist below the second divider (never pasted).
- MIT / modpack-friendly licensing stance.
- The stripped-paste workflow: paste content still runs from the
  first `---` divider to the end of the footer; the checklist stays
  out. (The stripped file now starts at the banner instead of the
  tagline.)

## Screenshot impact

The restyle adds **no new required shots** — the banner is
Claude-made. The open queue stays: merged-wall hero, pinned-overlay
gameplay, assembly line, plus optional pocket-OS and follow-mode
shots (see the checklist in DESCRIPTION.md).

## Acceptance criteria

- DESCRIPTION.md renders correctly as GitHub-flavored Markdown with
  the inline HTML blocks (banner/badges/stat line centered).
- Stripped paste file regenerates cleanly between the two dividers.
- No content sentence added, removed, or reworded except: the
  quick-add bullet→blockquote move, the new stat line, Credits, and
  the footer.
- banner.png committed to docs/images/ and pushed to main before the
  listings are re-pasted (else the banner 404s).
