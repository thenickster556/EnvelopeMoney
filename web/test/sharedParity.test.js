// Web half of the cross-platform parity tether: every case in shared/fixtures/ must produce
// the same numbers here as the Android suite does in SharedParityFixturesTest.java. Seeded from
// cases already green in both suites; an unknown helper or function fails loudly so a typo can
// never pass silently. See the parity matrix in docs/WEB_DEMO.md.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import {
  roundToCents,
  splitIntegerPercentsFirstCeiling,
  splitTotalByPercents,
} from '../domain/moneyMath.js';
import {
  parse,
  ocrLine,
  ocrResult,
  ReceiptCaptureMode,
} from '../domain/receiptFieldParser.js';
import { summarizeBudget } from '../domain/budgetBackup.js';

const fixturesDir = fileURLToPath(new URL('../../shared/fixtures/', import.meta.url));

function fixtureFiles() {
  return readdirSync(fixturesDir)
    .filter((name) => name.endsWith('.fixtures.json'))
    .sort();
}

function assertMoneyMath(where, fn, args, expected) {
  if (fn === 'roundToCents') {
    assert.equal(roundToCents(args[0]), expected, where);
  } else if (fn === 'splitIntegerPercentsFirstCeiling') {
    assert.deepEqual(splitIntegerPercentsFirstCeiling(args[0]), expected, where);
  } else if (fn === 'splitTotalByPercents') {
    assert.deepEqual(splitTotalByPercents(args[0], args[1]), expected, where);
  } else {
    assert.fail(`Unknown MoneyMath function '${fn}' in ${where}`);
  }
}

function assertBudgetBackup(where, fn, args, expected) {
  assert.equal(fn, 'summarize', `Unknown BudgetBackup function '${fn}' in ${where}`);
  const actual = summarizeBudget(args.document);
  for (const [key, value] of Object.entries(expected)) {
    assert.equal(actual[key], value, `${where} ${key}`);
  }
}

function assertReceiptFieldParser(where, fn, args, expected) {
  assert.equal(fn, 'parse', `Unknown ReceiptFieldParser function '${fn}' in ${where}`);
  assert.ok(args && typeof args === 'object' && !Array.isArray(args),
    `ReceiptFieldParser fixture args must be an object with lines/mode (${where})`);
  const lines = ocrResult(args.lines.map((row) => ocrLine(row)));
  const draft = parse(lines, ReceiptCaptureMode[args.mode], null);
  // Only listed fields are compared so parser additions do not break parity.
  if ('totalAmount' in expected) {
    assert.equal(draft.totalAmount, expected.totalAmount, where);
  }
  if ('merchantForComment' in expected) {
    assert.equal(draft.merchantForComment, expected.merchantForComment, where);
  }
  if ('dateYyyyMmDd' in expected) {
    assert.equal(draft.dateYyyyMmDd, expected.dateYyyyMmDd, where);
  }
}

test('shared parity fixtures match the web port', () => {
  const files = fixtureFiles();
  assert.ok(files.length > 0, 'No parity fixtures found in shared/fixtures — the tether cannot be empty');
  for (const file of files) {
    const fixture = JSON.parse(readFileSync(path.join(fixturesDir, file), 'utf8'));
    assert.ok(Array.isArray(fixture.cases) && fixture.cases.length > 0, `${file} has no cases`);
    for (const parityCase of fixture.cases) {
      const where = `${file}/${parityCase.name}`;
      assert.ok(parityCase.name, `case without a name in ${file}`);
      if (fixture.helper === 'MoneyMath') {
        assertMoneyMath(where, parityCase.function, parityCase.args, parityCase.expected);
      } else if (fixture.helper === 'ReceiptFieldParser') {
        assertReceiptFieldParser(where, parityCase.function, parityCase.args, parityCase.expected);
      } else if (fixture.helper === 'BudgetBackup') {
        assertBudgetBackup(where, parityCase.function, parityCase.args, parityCase.expected);
      } else {
        assert.fail(`Unknown parity helper '${fixture.helper}' in ${where}`);
      }
    }
  }
});
