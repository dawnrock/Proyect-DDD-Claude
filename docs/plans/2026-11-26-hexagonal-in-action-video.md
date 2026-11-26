# Plan: Video 1 · Hexagonal architecture in action

- **Issue:** [#190](https://github.com/dawnrock/Proyect-DDD-Claude/issues/190) (part of [#189](https://github.com/dawnrock/Proyect-DDD-Claude/issues/189))
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-26
- **Status:** draft
- **Total estimate:** ~130 min of work in 13 tasks, plus the user's reviews and the voice recording

## Goal

A video of **90 s at most** for the user's portfolio website. It shows monA Bank's hexagonal architecture working: different **input and output adapters** reach the same core through its ports, each one with a **real use case or port** of the app. Viewers should come away understanding why the domain does not know the technology, and what the project gains from that. It is the first video of #189, so it also lays the shared base (folder, brand kit, render script) that the other four reuse.

Agreed with the user (2026-11-26):

- **Narrated video.** Drafts use **Kokoro** (local, free text-to-speech): it is only there to time the edit and hear the script out loud, and it never reaches the published video. The final version uses **the user's own voice**, recorded once, when script and pace are closed.
- **Burnt-in subtitles always**, because web videos usually autoplay muted.
- HyperFrames skills are installed **only in the working session** (`npx skills add heygen-com/hyperframes`), never committed; `.claude/` does not change.
- HTML compositions are versioned in `portfolio/video/`; MP4 and audio files are **not** committed (they go to a GitHub release when #189 closes).
- monA Bank brand style (ADR 0022, 0023): palette, logo, the A's slant, Tektur and Sora.

### Out of scope

- Videos 2-5 (#191-#194) and the vertical cuts for social media.
- Any change to the app's code (`src/`), the API or `.claude/`.
- Publishing the GitHub release (done once, when #189 closes).
- Background music (can be added later if the user wants it).

## Context

No Java changes: the work is in `portfolio/video/` and the docs. What the video shows comes from the repository as it is today:

| Block | Real element in the repo | Where |
| ----- | ------------------------ | ----- |
| The hexagon | `domain.model`, `domain.service`, `application` (input ports `*Commands`/`*Queries`, output ports), adapters around | `docs/arquitectura/fase-2-hexagonal.md` |
| Input · REST | `POST /api/auth/token` (JWT, ADR 0015) and `POST /api/transfers`; `RestExceptionHandler` → `ProblemDetail` | `adapter.in.rest`, `docs/api/openapi.yaml` |
| Input · web | Deposit from the account detail with htmx (`DepositWebController`) | `adapter.in.web` |
| Input · CLI | `DomainModelRunner` with the `demo` profile and its `[demo]` trace | `adapter.in.cli` |
| Input · scheduler | `SegmentReviewScheduler`: "who calls the hexagon here is the clock" (`SegmentReviewCommands`) | `adapter.in.scheduler` |
| Output · persistence | `CheckingAccountRepository` → `JpaCheckingAccountRepository` (JPA + Flyway; PostgreSQL on Neon / H2) and `InMemoryCheckingAccountRepository` in the tests | `adapter.out.persistence`, `src/test/.../adapter/out/persistence` |
| Output · events | `DomainEventPublisher` → `OutboxDomainEventPublisher` + `OutboxRelay` (ADR 0021) | `adapter.out.event` |
| Output · tax agency | `IncomeVerifier` → `FakeTaxAgencyIncomeVerifier` (segment by verified income) | `adapter.out.taxagency` |
| Protected domain | `CheckingAccount` rejects a withdrawal below what is allowed (`WithdrawalRejected`, R6-R8); `HexagonalArchitectureTests` (`dependenciesPointTowardsTheHexagon`, `theDomainOnlyDependsOnTheJdk`…) and `DomainIsolationTests` | `domain.model`, `src/test` |

Correction to the issue: the issue named `OutboxRelay` as the scheduler input adapter. The repo's scheduler input adapter is `SegmentReviewScheduler`; `OutboxRelay` belongs to the event output adapter, so it appears there.

Files:

| Area | File | Change |
| ---- | ---- | ------ |
| docs | `portfolio/video/README.md` | New: how to install the skills in the session, preview, voice, render |
| build | `.gitignore` | Ignore `portfolio/video/**/renders/` and the audio of `portfolio/video/**/audio/` |
| docs | `portfolio/video/_shared/` | New: brand kit (tokens, fonts, logo), reused by videos 2-5 |
| docs | `portfolio/video/hexagonal-en-accion/` | New: script, storyboard, composition, subtitles |
| build | `portfolio/video/render.mjs` | New: reproducible render (draft/final) with a duration check |
| docs | `docs/ia/*.md` | Project status, decisions, pending items, history |

Tools in the session: Node 22, ffmpeg/ffprobe, Chromium, Python 3.13 (for Kokoro, `pip install kokoro-onnx soundfile`) and Whisper/Parakeet from the HyperFrames skills for timestamps. Review with the `design:design-critique`, `design:ux-copy` and `design:accessibility-review` skills, and frame checks with Playwright.

## Tasks

### [ ] T1 — Base folder and session setup (~10 min)

- **Layer:** build · docs
- **Files:** `portfolio/video/README.md`, `.gitignore`
- **Depends on:** —
- **What to do:**
  1. Install the HyperFrames skills in the session and check where they land; if they land inside the repo (for example `.claude/skills/` or `.agents/`), install them in the user directory instead, so that nothing new appears in `git status`.
  2. Write `portfolio/video/README.md`: purpose (#189), install in the session, preview, draft voice (Kokoro), final voice (user's recording), render, and where the MP4s go (release).
  3. Add to `.gitignore`: `portfolio/video/**/renders/` and `portfolio/video/**/audio/*.wav|*.mp3|*.m4a`.
- **Acceptance criteria:**
  - [ ] After installing the skills, `git status` only shows the README and `.gitignore`.
  - [ ] An `.mp4` or `.wav` inside `portfolio/video/` does not show up in `git status`.

### [ ] T2 — Shared brand kit (~10 min)

- **Layer:** docs
- **Files:** `portfolio/video/_shared/brand.css`, `portfolio/video/_shared/README.md`
- **Depends on:** T1
- **What to do:**
  1. Colour tokens and type scale of ADR 0022/0023 as CSS variables; the A's slant (≈ 12.4°) as a variable for parallelograms.
  2. Tektur and Sora: reference `src/main/resources/static/vendor/` (with their OFL licence) and the logo SVG of `static/brand/` without duplicating them. If the renderer cannot read files outside the composition folder, `render.mjs` (T3) copies them to a temporary folder before rendering; they are never committed twice.
- **Acceptance criteria:**
  - [ ] A test page with the kit shows the logo, Tektur and Sora correctly in Chromium (Playwright screenshot).

### [ ] T3 — Reproducible render script (~10 min)

- **Layer:** build
- **Files:** `portfolio/video/render.mjs`
- **Depends on:** T2
- **What to do:**
  1. `node portfolio/video/render.mjs <slug> [--draft|--final]`: prepares the shared assets, renders with the HyperFrames CLI to `portfolio/video/<slug>/renders/` at 1920×1080, and muxes the voice from `audio/`.
  2. Checks the duration with `ffprobe` and fails if it goes over the maximum the composition declares (90 s for this video).
- **Acceptance criteria:**
  - [ ] Rendering a 3 s placeholder composition produces an MP4 1920×1080, and a 95 s one with a 90 s maximum fails with a clear message.

### [ ] T4 — Script in Spanish (~10 min) · **user review**

- **Layer:** docs
- **Files:** `portfolio/video/hexagonal-en-accion/script.md`
- **Depends on:** —
- **What to do:**
  1. Write the narration in Spanish, 200-220 words (≈ 85 s), in five blocks with target times: hexagon (≈ 10 s), input adapters (≈ 35 s), output adapters (≈ 25 s), protected domain (≈ 10 s), closing (≈ 5 s).
  2. Next to each block, the on-screen text (short, readable on a phone) and the real repo elements it names (Context table). Polish it with `design:ux-copy`.
  3. Check every class, route and rule name against the repo (`node scripts/graphify.mjs` or Grep).
- **Acceptance criteria:**
  - [ ] The user approves the script (it is the gate for T5-T11).
  - [ ] Every name it shows exists in the repo.

### [ ] T5 — Storyboard (~10 min) · **user review**

- **Layer:** docs
- **Files:** `portfolio/video/hexagonal-en-accion/storyboard.md`
- **Depends on:** T4
- **What to do:**
  1. One row per scene: time, layout (where the hexagon, the adapter and the code go), animation, on-screen text and code fragment.
  2. Optional rough sketch in Excalidraw of the key frames, shown to the user before committing (diagram agreement).
  3. `design:design-critique` over the storyboard: hierarchy, pace, legibility.
- **Acceptance criteria:**
  - [ ] At least three different input adapters and three output adapters, each one with a real use case or port.
  - [ ] The user approves the storyboard.

### [ ] T6 — Draft voice and timing (~10 min)

- **Layer:** docs
- **Files:** `portfolio/video/hexagonal-en-accion/timing.json`
- **Depends on:** T4
- **What to do:**
  1. Generate the narration with Kokoro, a Spanish voice, into `audio/draft.wav` (not committed).
  2. Get word timestamps with Whisper/Parakeet and derive the start and end of each block into `timing.json`, which the composition reads to sync scenes and subtitles.
- **Acceptance criteria:**
  - [ ] `timing.json` has the five blocks and their total is ≤ 90 s; if not, go back to T4 and trim the script.

### [ ] T7 — Scene 1 · The hexagon (~10 min)

- **Layer:** docs
- **Files:** `portfolio/video/hexagonal-en-accion/index.html` (scene 1), `scenes/01-hexagon.*` if the composition is split
- **Depends on:** T2, T5, T6
- **What to do:**
  1. The hexagon builds up: domain in the centre, application with its ports, adapter slots around, with the brand slant and colours.
  2. Timing taken from `timing.json`.
- **Acceptance criteria:**
  - [ ] Draft render of the scene alone; key frames reviewed with Playwright (screenshots at its start, middle and end).

### [ ] T8 — Scene 2 · Input adapters (~10 min)

- **Layer:** docs
- **Files:** `portfolio/video/hexagonal-en-accion/` (scene 2)
- **Depends on:** T7
- **What to do:**
  1. Four doors into the same core, one after another: REST (token request → transfer → `ProblemDetail` for a domain error), web (deposit with htmx), CLI (`[demo]` trace), scheduler (`SegmentReviewScheduler`, "el reloj").
  2. Each door shows a short real fragment (route, class, log line) and lights up the input port it uses.
- **Acceptance criteria:**
  - [ ] The four adapters appear with fragments copied from the repo; draft render of the scene.

### [ ] T9 — Scene 3 · Interchangeable outputs (~10 min)

- **Layer:** docs
- **Files:** `portfolio/video/hexagonal-en-accion/` (scene 3)
- **Depends on:** T7
- **What to do:**
  1. `CheckingAccountRepository`: the JPA adapter (PostgreSQL on Neon / H2) swaps for the in-memory double of the tests without the core noticing.
  2. `DomainEventPublisher` → outbox and relay; `IncomeVerifier` → the simulated tax agency.
- **Acceptance criteria:**
  - [ ] Three ports with their real adapters; the swap of the repository adapter is visible; draft render of the scene.

### [ ] T10 — Scenes 4 and 5 · Protected domain and closing (~10 min)

- **Layer:** docs
- **Files:** `portfolio/video/hexagonal-en-accion/` (scenes 4-5)
- **Depends on:** T7
- **What to do:**
  1. A withdrawal rejected by `CheckingAccount` (the balance does not move) and the ArchUnit rules that keep the architecture from being broken.
  2. Closing: monA Bank logo, stack (Java 21 · Spring Boot 4.1 · DDD · Hexagonal) and the repository link.
- **Acceptance criteria:**
  - [ ] Draft render of both scenes.

### [ ] T11 — Subtitles and full draft (~10 min) · **user review**

- **Layer:** docs
- **Files:** `portfolio/video/hexagonal-en-accion/captions.json` (or `.vtt`), composition
- **Depends on:** T6, T8, T9, T10
- **What to do:**
  1. Burnt-in subtitles from the transcript, in the brand style, legible on a phone and never covering the code.
  2. Full draft render with the Kokoro voice; `design:accessibility-review` over key frames (contrast, text size).
  3. Hand the draft to the user and iterate (back to T4-T10 as needed) until script, pace and look are closed.
- **Acceptance criteria:**
  - [ ] Full draft ≤ 90 s, 1920×1080, with subtitles.
  - [ ] The user approves the draft.

### [ ] T12 — Final voice and final render (~10 min, after the user's recording)

- **Layer:** docs
- **Files:** `portfolio/video/hexagonal-en-accion/timing.json`, `captions.json`
- **Depends on:** T11
- **What to do:**
  1. The user records the approved script and shares the audio (into `audio/`, not committed). Recording tips go in the README: quiet room, phone close, one take per block is fine.
  2. Clean it up if needed (ffmpeg: normalise loudness, trim silences), get timestamps, regenerate `timing.json` and the subtitles from the real voice.
  3. `node portfolio/video/render.mjs hexagonal-en-accion --final`.
- **Acceptance criteria:**
  - [ ] Final MP4 1920×1080 of 90 s or less (checked by `ffprobe`), with the user's voice and subtitles in sync.
  - [ ] The user approves the final video.

### [ ] T13 — Project docs (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `docs/ia/decisiones.md`, `docs/ia/pendientes.md`, `docs/ia/historial.md`
- **Depends on:** T12
- **What to do:**
  1. Status: #190 done, next step (the next video of #189).
  2. Decisions: the video conventions (skills only in the session, MP4/audio out of the repo, Kokoro for drafts and the user's voice for the final, burnt-in subtitles).
  3. Pending: the release with the MP4s when #189 closes; the base to reuse in #191-#194.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] Composition in `portfolio/video/hexagonal-en-accion/`, rendered to an MP4 1920×1080 of 90 s or less.
- [ ] At least three different input adapters and three output adapters appear, each with a real use case or port.
- [ ] Narration in the user's voice and burnt-in subtitles; no draft voice in the final video.
- [ ] monA Bank brand style; script, storyboard, draft and final video approved by the user.
- [ ] No MP4, audio file or HyperFrames skill is committed; `.claude/` unchanged.
- [ ] The shared base (`_shared/`, `render.mjs`, README) works for the next videos.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected: n/a (no Java changes), still verified by ArchUnit in `./mvnw verify`.
- [ ] The domain only depends on the JDK and does not use Lombok: n/a (no domain changes).
- [ ] New tests follow `src/test/CLAUDE.md`: n/a (no Java tests).
- [ ] No dependencies added beyond the planned ones: the HyperFrames skills, Kokoro and Whisper are session tools, not dependencies of the app or of Maven, and nothing of them is committed.
- [ ] If the API changes: n/a.
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a.
- [ ] Documentation written or changed by this plan is in English (the script and on-screen texts are in Spanish: they are content for the user's audience, agreed in #189).
- [ ] Project status updated: `estado-del-proyecto.md`, `decisiones.md`, `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **The skills could install inside the repo.** T1 checks it before anything else; if so, they go to the user directory.
- **Fonts and logo outside the composition folder.** If the renderer does not read them, `render.mjs` copies them at render time (T2-T3), without duplicating them in git.
- **Kokoro in Spanish** has few voices and sounds robotic; that is fine for drafts. If it cannot be installed, the drafts are timed with silent placeholders at 150 words per minute.
- **Render time in the cloud container**: a 90 s video at 30 fps is ~2,700 frames; the drafts can render at lower resolution or frame rate to iterate faster.
- **The user's reviews are gates** (T4, T5, T11, T12): the work stops there until the user answers.
- **Each cloud session starts clean:** the skills and Kokoro are installed again in each session (documented in the README).
