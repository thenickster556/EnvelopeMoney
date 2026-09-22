# Mountain Money web demo

Localhost HTML/CSS replica of the Android app with a small Node/Express server and MongoDB. The Android app is unchanged. This demo does **not** sync with SharedPreferences.

## Run on localhost

1. Install Node.js 18+ and MongoDB (local `mongod` or Docker).
2. From `web/`:

```text
copy .env.example .env
docker compose up -d
npm install
npm test
npm start
```

3. Open the **This PC** URL from the console (`http://localhost:3001`). The process binds `HOST` (default `0.0.0.0`). On a phone, type the full address under **On your phone, open this exact address** (includes `http://`, the PC’s LAN IP, and port `3001`). Mongo stays on `127.0.0.1:27017` and is not exposed to the LAN.
4. Sign in as **`alice-demo` / `secret1`** (after `npm run seed-demo`) or **Register** a new account. Empty new accounts get the same five-pond, 13-month demo dataset automatically.

The demo dataset is **Groceries, Gas, Fun, Bills, Savings** with spending, 2-bucket transfers, split purchases, and a monthly rent series across the **12 previous months plus the current month**. Go to a **future** month (or a filter with no rows) to see **No transactions to display**.

To refresh `alice-demo` (overwrites that profile):

```text
npm run seed-demo
```

## Stack

- `web/public` — mobile-first HTML/CSS/JS (Mountain palette, ponds copy)
- `web/domain` — JS ports of Android helpers (MoneyMath, BillsDayAnchor, payday Remaining, transfers/splits, HistoryTransferTotals, historyFilter, receiptOcrPrep, rollover, ReceiptFieldParser, CommentHistory, OcrAmountLearner, SpendAnalysisHelper, BudgetBackup, receiptRelink)
- `web/server` — Express session auth, profile JSON, GridFS receipts, per-user `web/data/learning/<userId>.db` (sql.js). Listens on `HOST` (default `0.0.0.0`) and prints Local/Network URLs. Optional HTTPS via `HTTPS_KEY` / `HTTPS_CERT`.
- MongoDB database `mountain_money`: `users`, `profiles`, `sessions`, `receipts` GridFS
- Client local files (optional): `web/public/js/storage` + `ReceiptStorage`. Not a Mongo replacement.

## Behavior

Same as the Android app: ponds, transactions, Spending / Transfer / Split purchase, recurring, bills days vs paydays, payday Remaining = Account + unlocked Limit slices − month spend, bills-period filter, receipt camera/gallery + one reused Tesseract.js worker on the original photo + `ReceiptFieldParser`, comment typeahead (3-row list), silent OCR amount-weight learning, preview rotate/save (GridFS keeps the original id until the replacement exists; local mode overwrites the device file or working copy), transfer save validates before insert and moves edited transfer sources between ponds, **Analysis** charts (Last 3/6/12, pond chips, include-transfers). History **Showing:** follows pond checkboxes (none checked is an empty list; transfers follow the toggle). Phone layout is one column; from 800px wide, history and ponds sit side by side. **Local Files** is opt-in per browser: default receipt storage remains GridFS. **Backup** (download icon) saves or restores the ledger JSON via `GET`/`POST /api/backup`. That file does not include pictures. Local Files **Export** remains a picture zip. In device mode, a unique filename already in the folder is relinked to `local://` after restore or when the folder is connected again. The file shape is shared by `BudgetBackup` and `web/domain/budgetBackup.js`, pinned by `shared/fixtures/budgetBackup.fixtures.json`.

## LAN phone / second-computer demo

1. Start MongoDB on the development PC only (`docker compose up -d` in `web/` or local `mongod`). Do not publish port 27017 to other devices.
2. From `web/`: `npm start`. Console example:

```text
Mountain Money web demo
  This PC:  http://localhost:3001
  Computer name: YOUR-PC
  On your phone, open this exact address:
    http://192.168.x.x:3001
  Mongo:    mongodb://127.0.0.1:27017 (not exposed to LAN)
```

3. On the PC browser open the **This PC** URL. On the phone, type the full line under **On your phone, open this exact address** (allow port 3001 through the PC firewall if needed).
4. Sign in through Express. That other device talks to Express; Express talks to Mongo on localhost.
5. **Live folder** APIs need a secure context. `http://localhost` is fine on the PC. A phone opening `http://192.168.x.x:3001` can still use camera and **Choose Files**. To test **Choose Folder** on that phone, enable local HTTPS (below) and trust the certificate.
6. Confirm the folder/file picker shows **that device’s** storage, not the PC’s. Local mode does not upload the JPEG to GridFS.

## Local HTTPS (optional)

Do not force HTTPS for GridFS-only localhost tests. For LAN folder access:

1. Install [mkcert](https://github.com/FiloSottile/mkcert) and run `mkcert -install`.
2. Issue a cert that includes `localhost` and the PC’s current LAN IP (the Network line from `npm start`). Example: `mkcert localhost 127.0.0.1 ::1 192.168.1.42`
3. In `web/.env` set `HTTPS_KEY` and `HTTPS_CERT` to those PEM paths. Session cookies become `secure` only when HTTPS is on.
4. Restart `npm start` and open the printed `https://` Local/Network URLs. Trust the warning on the phone if the cert is locally trusted via mkcert.

## Tests

`npm test` in `web/` runs Node tests ported from the Android JUnit goldens, plus `transactionSave`, `gridFsReplace`, file-reference/local-storage/receipt-storage helpers, and listen URL formatting.

## Hosting later

Typical GoDaddy **shared** hosting cannot run Node + Mongo. Use a VPS (including GoDaddy VPS) or MongoDB Atlas + a Node host. Do not put `SESSION_SECRET` or `.env` in a public repo.
