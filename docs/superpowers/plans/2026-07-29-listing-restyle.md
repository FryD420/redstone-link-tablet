# Listing Restyle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle `docs/DESCRIPTION.md` to the visual polish of the Create: Lights & Controls listing (banner, badges, emoji headers, dividers, tip box, stat line, credits) with zero prose changes beyond the spec's approved additions.

**Architecture:** Markdown body + inline HTML only for centered elements (banner, badge row, stat line). Banner is a Claude-designed PNG produced by screenshotting an HTML mock with headless Chrome. Spec: `docs/superpowers/specs/2026-07-29-listing-restyle-design.md`.

**Tech Stack:** Markdown, devins-badges images, headless Chrome (`--headless --screenshot`), PowerShell System.Drawing as fallback.

## Global Constraints

- Body prose is UNCHANGED except: quick-add bullet → blockquote, new stat line, new Credits section, new footer line (spec "Acceptance criteria").
- Inline HTML only for: banner `<p>`, badge row `<p>`, stat line `<p>` — everything else stays Markdown.
- The `<!-- 📸 SCREENSHOT -->` comments and the shooting checklist below the second divider stay byte-identical.
- Banner embeds from `https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/banner.png` — renders on listings only after a push to main.
- **This repo commits ONLY when the user asks** (CLAUDE.md) — no automatic commits despite the usual plan cadence; batch them for the user's go.
- Donate link: `https://streamelements.com/fryd42/tip` (exact).

---

### Task 1: Banner PNG

**Files:**
- Create: `<scratchpad>/banner.html` (mock source, temporary)
- Create: `docs/images/banner.png` (1200×300)

**Interfaces:**
- Produces: `docs/images/banner.png`, embedded by Task 2's header block.

- [ ] **Step 1: Write the HTML mock** in the scratchpad — dark tablet-screen backdrop, brass/gold title "Redstone Link Tablet" with a small "CREATE:" kicker, subtle glow underline, 1200×300 fixed body. Design cues: Create's brass (#d4aa5f range), the mod's DARK theme slate, rounded tablet-bezel frame.
- [ ] **Step 2: Screenshot it with headless Chrome:**

```powershell
& "C:\Program Files\Google\Chrome\Application\chrome.exe" --headless --screenshot="<repo>\docs\images\banner.png" --window-size=1200,300 --default-background-color=00000000 "<scratchpad>\banner.html"
```

If Chrome is elsewhere/absent, check `${env:ProgramFiles(x86)}` and `$env:LOCALAPPDATA\Google\Chrome\Application`; last resort: PowerShell System.Drawing (gradient rect + bold Segoe UI text).

- [ ] **Step 3: USER CHECKPOINT — send banner.png via SendUserFile (render)** and iterate on feedback until approved. Do not proceed to Task 2's embed until approved.

### Task 2: Restyle DESCRIPTION.md

**Files:**
- Modify: `docs/DESCRIPTION.md` (paste-content region between the two `---` dividers, plus the header note stays)

**Interfaces:**
- Consumes: `docs/images/banner.png` (Task 1).
- Produces: restyled paste region consumed by Task 3's extraction.

- [ ] **Step 1: Insert the header block** immediately after the first `---` divider, BEFORE the bold tagline:

```html
<p align="center"><img src="https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/banner.png" alt="Create: Redstone Link Tablet"></p>
<p align="center"><img src="https://github.com/intergrav/devins-badges/blob/v3/assets/cozy/supported/neoforge_64h.png?raw=true" alt="Available for NeoForge" height="56">&nbsp;&nbsp;<a href="https://www.curseforge.com/minecraft/mc-mods/create"><img src="https://github.com/intergrav/devins-badges/blob/v3/assets/cozy/requires/create_64h.png?raw=true" alt="Requires Create" height="56"></a>&nbsp;&nbsp;<a href="https://streamelements.com/fryd42/tip"><img src="https://github.com/intergrav/devins-badges/blob/v3/assets/cozy/donate/generic-singular_64h.png?raw=true" alt="Donate" height="56"></a></p>

---
```

(Verify the generic donate badge filename exists in intergrav/devins-badges v3 `assets/cozy/donate/`; if the exact name differs, use the repo's generic donate cozy badge.)

- [ ] **Step 2: Emoji headers + dividers** — retitle the 13 `##` sections per the spec table (📱 🎛️ 📡 🎚️ 📝 📌 ✨ 🧱 🎯 🖥️ 🎨 🏭 📦) and ensure a `---` line between every pair of adjacent sections.
- [ ] **Step 3: Quick-add tip box** — in 📡 Real Create frequencies, delete the `**Quick-add:** …` bullet and append after the list:

```markdown
> 📋 **Tip — quick-add:** right-click any Redstone Link (or anything with a link frequency, like elevator contacts) while holding the tablet — the editor opens with that frequency pre-filled and a name suggested. Just hit Save.
```

- [ ] **Step 4: Stat line** — in 🖥️ One screen, many tablets, after the first paragraph:

```html
<p align="center"><strong style="color:#f2c94c; font-size:1.3em;">🧮 A 4×3 wall = 12 merged tablets — 384 signals on one screen</strong></p>
```

- [ ] **Step 5: Credits + footer** — before the final GitHub line, insert:

```markdown
## ❤️ Credits

Huge thanks to the [Create](https://www.curseforge.com/minecraft/mc-mods/create) team — this addon stands on their Redstone Link network and visual language. Go support the original mod!

Enjoying the tablet? You can [drop a tip](https://streamelements.com/fryd42/tip) ☕ — thank you!

---

Created by **FryD42** · **MIT licensed** — free to use in any modpack.
```

Keep "Source, issues, and changelog on [GitHub](…)" as the last paste line.

- [ ] **Step 6: Verify rendering** — preview the file as markdown (browser pane or GitHub-flavored render) and check: banner+badges centered, all 13 emoji headers, dividers, blockquote, gold line, credits, footer, 📸 comments intact, checklist untouched.

### Task 3: Stripped paste file + delivery

**Files:**
- Create: `<scratchpad>/DESCRIPTION_PASTE.md`

**Interfaces:**
- Consumes: restyled `docs/DESCRIPTION.md` (Task 2).

- [ ] **Step 1: Extract** everything between the first `---` divider and the line before the closing `---` + "Shooting checklist" section (now: banner `<p>` through the GitHub line) — recompute the line range, don't reuse 11–273.
- [ ] **Step 2: Send** via SendUserFile with paste instructions; remind: banner renders only after push to main, so commit+push (user-gated) BEFORE re-pasting the listings.

---

## Self-review

Spec coverage: header block (T2S1), emoji/dividers (T2S2), tip box (T2S3), stat line (T2S4), credits/footer (T2S5), banner (T1), stripped file (T3), push-to-main caveat (T3S2 + constraints) — all covered. No placeholders; exact snippets included. Names consistent (banner.png, paste file).
