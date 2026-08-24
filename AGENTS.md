# AGENTS.md

## Cursor Cloud specific instructions

This repo has two parts:

- `app/` — the **Mountain Money Android app** (Gradle 6.7.1 / AGP 4.2.1). It requires **JDK 8–15** and the **Android SDK**, neither of which is available in the Cloud VM (only JDK 21 is installed, and there is no Android SDK/emulator). It is a GUI Android app, so it cannot be built or run end-to-end in this headless environment. Treat the Android app as **out of runnable scope** here; make source changes carefully and rely on the ported `web/domain` JS unit tests plus code review for verification. Do not add heavy Android SDK/old-JDK installs to the update script.
- `web/` — the **runnable web demo** (Node/Express + MongoDB), a localhost HTML/CSS replica of the Android app. This is what to run and test in the Cloud VM.

### Web demo (primary runnable service)

Standard commands live in `web/package.json` and `docs/WEB_DEMO.md`. All web commands run from `web/`.

- Dependencies: `npm install` (handled by the startup update script).
- Tests: `npm test` (Node's built-in test runner; ports of the Android JUnit goldens). No DB required.
- Seed demo account: `npm run seed-demo` (creates `alice-demo` / `secret1`). Requires MongoDB running.
- Run: `npm start` → serves http://127.0.0.1:3000 (binds to `127.0.0.1` only).

### MongoDB is required to run the app (not for tests)

The web server exits immediately if it cannot reach MongoDB. `docs/WEB_DEMO.md` suggests `docker compose up -d`, but **Docker is not installed** in the Cloud VM. Instead, MongoDB Community Server is installed directly (via the mongodb-org apt repo). It is **not** managed by systemd here — start it manually before running the app or `seed-demo`:

```
mongod --dbpath /var/lib/mongodb --bind_ip 127.0.0.1 --port 27017
```

Run it in the background (e.g. a dedicated tmux session). The data dir `/var/lib/mongodb` is owned by the `ubuntu` user. If `mongod` is not installed on a fresh VM, install it from the `mongodb-org` 8.0 apt repo for Ubuntu 24.04 (`noble`).

### Notes

- `web/.env` is git-ignored; copy it from `web/.env.example` (defaults point at local Mongo on `27017`, DB `mountain_money`). The server also falls back to these defaults if `.env` is absent.
- Empty/new accounts are auto-seeded with a five-pond, 13-month demo dataset on first login, so registering a fresh account is enough to see data.
- OCR in the browser loads `tesseract.js` from a CDN; core budgeting flows (login, ponds, transactions, transfers, splits) do not depend on it.
