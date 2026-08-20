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

3. Open `http://127.0.0.1:3000`
4. Sign in as **`alice-demo` / `secret1`** (after `npm run seed-demo`) or **Register** a new account. Empty new accounts get the same five-pond, 13-month demo dataset automatically.

The demo dataset is **Groceries, Gas, Fun, Bills, Savings** with spending, 2-bucket transfers, split purchases, and a monthly rent series across the **12 previous months plus the current month**. Go to a **future** month (or a filter with no rows) to see **No transactions to display**.

To refresh `alice-demo` (overwrites that profile):

```text
npm run seed-demo
```

## Stack

- `web/public` — mobile-first HTML/CSS/JS (Mountain palette, ponds copy)
- `web/domain` — JS ports of Android helpers (MoneyMath, BillsDayAnchor, payday Remaining, transfers/splits, rollover, ReceiptFieldParser, CommentHistory, OcrAmountLearner)
- `web/server` — Express session auth, profile JSON, GridFS receipts, per-user `web/data/learning/<userId>.db` (sql.js)
- MongoDB database `mountain_money`: `users`, `profiles`, `sessions`, `receipts` GridFS

## Behavior

Same as the Android app: ponds, transactions, Spending / Transfer / Split purchase, recurring, bills days vs paydays, payday Remaining = Account + unlocked Limit slices − month spend, bills-period filter, receipt camera/gallery + Tesseract.js OCR + `ReceiptFieldParser`, comment typeahead (3-row list), silent OCR amount-weight learning, preview rotate/save.

## Hosting later

Typical GoDaddy **shared** hosting cannot run Node + Mongo. Use a VPS (including GoDaddy VPS) or MongoDB Atlas + a Node host. Do not put `SESSION_SECRET` or `.env` in a public repo.

## Tests

`npm test` in `web/` runs Node tests ported from the Android JUnit goldens.
