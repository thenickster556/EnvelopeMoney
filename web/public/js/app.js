import { S } from './i18n.js';
import {
  formatDisplayDate,
  formatMonthTitle,
  parseDisplayDate,
  parseIsoDate,
  ymd,
  addMonthsYearMonth,
  firstDayOfMonth,
  lastDayOfMonth,
  realCurrentMonth,
} from '/domain/dates.js';
import { createEnvelope, createTransaction, addTransaction, getTransactions, initializeMonth, calculateRemaining } from '/domain/envelopeModel.js';
import { findByName, canonicalName } from '/domain/pondLookup.js';
import { computeAnchorDate } from '/domain/billsDayAnchor.js';
import { roundToCents, splitIntegerPercentsFirstCeiling, splitTotalByPercents } from '/domain/moneyMath.js';
import { excludingSource } from '/domain/transferDestinationList.js';
import { validate as validateTransfer, allocatedTotal as transferAllocated } from '/domain/transferGroup.js';
import { validate as validateSplit, allocatedTotal as splitAllocated } from '/domain/splitPurchase.js';
import { applyTransferGroup, detachTransferGroup, resolveAnchorTransaction, getAllocations } from '/domain/transferSync.js';
import { applyGroup, removeGroup, isSplitPurchase, findTransactionsInGroup, resolveForEdit, formatBreakdownLine, groupTotal } from '/domain/splitSync.js';
import { computeSliderMaximum, resolveAmountAtSliderMax, buildScaleLabels } from '/domain/transferBucketUi.js';
import { parse as parseReceipt, ocrLine, ocrResult, ReceiptCaptureMode } from '/domain/receiptFieldParser.js';
import { suggestions } from '/domain/commentHistory.js';
import { isIsoDateOutsideFilterRange } from '/domain/receiptDateFilter.js';
import { randomUUID } from '/domain/id.js';
import { footerTotals, pondReconciliation, refreshBalances, recalculateBalances } from '/domain/profileEngine.js';
import { ensureRecurringTransactions } from '/domain/recurring.js';

const state = {
  user: null,
  profile: null,
  reorder: false,
  expandedSplits: new Set(),
  previewUri: null,
  previewRotation: 0,
  previewScale: 1,
  previewX: 0,
  previewY: 0,
};

const $ = (id) => document.getElementById(id);

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body && !(options.body instanceof FormData) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }
  const res = await fetch(path, { credentials: 'include', ...options, headers });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || 'Request failed');
  return data;
}

async function saveProfile() {
  refreshBalances(state.profile);
  const data = await api('/api/profile', { method: 'PUT', body: JSON.stringify({ profile: state.profile }) });
  state.profile = data.profile;
  render();
}

function toast(message) {
  const el = $('toast');
  el.textContent = message;
  el.classList.remove('hidden');
  setTimeout(() => el.classList.add('hidden'), 2800);
}

function money(n) {
  return `$${Number(n || 0).toFixed(2)}`;
}

function showAuth(show) {
  $('authScreen').classList.toggle('hidden', !show);
  $('appScreen').classList.toggle('hidden', show);
}

function displayedMonth() {
  return state.profile.displayedMonth || state.profile.currentMonth || realCurrentMonth();
}

function isoFromDisplay(display) {
  const d = parseDisplayDate(display) || parseIsoDate(display);
  return d ? ymd(d) : '';
}

function displayFromIso(iso) {
  const d = parseIsoDate(iso);
  return d ? formatDisplayDate(d) : iso || '';
}

function inDateRange(isoOrDisplay) {
  const d = parseIsoDate(isoOrDisplay) || parseDisplayDate(isoOrDisplay);
  if (!d) return true;
  const start = parseDisplayDate(state.profile.dateFilterStartDisplay);
  const end = parseDisplayDate(state.profile.dateFilterEndDisplay);
  if (start && d < start) return false;
  if (end && d > end) return false;
  return true;
}

function ensureDateFilterIncludes(iso) {
  const d = parseIsoDate(iso);
  if (!d) return;
  const start = parseDisplayDate(state.profile.dateFilterStartDisplay);
  const end = parseDisplayDate(state.profile.dateFilterEndDisplay);
  let widened = false;
  if (start && d < start) {
    state.profile.dateFilterStartDisplay = formatDisplayDate(d);
    widened = true;
  }
  if (end && d > end) {
    state.profile.dateFilterEndDisplay = formatDisplayDate(d);
    widened = true;
  }
  if (widened && state.profile.billsFilterActive) {
    state.profile.billsFilterActive = false;
  }
}

function selectedPonds() {
  return (state.profile.envelopes || []).filter((e) => e.selected);
}

function allTransactions() {
  const month = displayedMonth();
  const selected = selectedPonds();
  const names = new Set(selected.map((e) => e.name));
  const rows = [];
  for (const env of state.profile.envelopes || []) {
    if (names.size && !names.has(env.name)) continue;
    for (const t of getTransactions(env)) {
      if (t.month !== month) continue;
      if (!inDateRange(t.date)) continue;
      rows.push(t);
    }
  }
  rows.sort((a, b) => String(b.date).localeCompare(String(a.date)));
  return rows;
}

function render() {
  if (!state.profile) return;
  const month = displayedMonth();
  $('tvCurrentMonth').textContent = formatMonthTitle(month);
  $('btnStartDate').textContent = `${S.start}: ${state.profile.dateFilterStartDisplay || ''}`;
  $('btnEndDate').textContent = `${S.end}: ${state.profile.dateFilterEndDisplay || ''}`;
  $('btnBillsFilter').classList.toggle('active', !!state.profile.billsFilterActive);
  $('btnToggleTransfers').classList.toggle('active', !!state.profile.transfersVisible);
  $('transferTotalsWrap').classList.toggle('hidden', !state.profile.transfersVisible);
  $('pondList').classList.toggle('hidden', !!state.profile.envelopesCollapsed);
  $('btnToggleEnvelopes').textContent = state.profile.envelopesCollapsed ? '▸' : '▾';
  $('btnPondReorder').textContent = state.reorder ? '✓' : '↕';
  $('btnPondReorder').classList.toggle('active', state.reorder);
  const selected = selectedPonds().length;
  $('tvPondSelectedCount').textContent = selected ? S.selectedCount(selected) : '';
  renderPonds();
  renderTransactions();
  const foot = footerTotals(state.profile);
  if (foot.mode === 'reconcile') $('tvPondTotalsFooter').textContent = S.footerReconcile(foot.inBank, foot.stillToDeposit);
  else if (foot.mode === 'full') $('tvPondTotalsFooter').textContent = S.footerFull(foot.account, foot.remaining, foot.difference);
  else $('tvPondTotalsFooter').textContent = S.footerPartial(foot.remaining);
}

function renderPonds() {
  const list = $('pondList');
  list.innerHTML = '';
  (state.profile.envelopes || []).forEach((pond, index) => {
    const li = document.createElement('li');
    li.className = 'card';
    const recon = pondReconciliation(pond, state.profile);
    const details = [];
    details.push(`${S.limit}: ${money(pond.limit)}`);
    details.push(`${S.remaining}: ${money(pond.remaining)}`);
    if (pond.accountBalance != null) details.push(`${S.accountLabel}: ${money(pond.accountBalance)}`);
    if (recon && recon.active) {
      details.push(S.rowReconcile(recon.inBank, recon.stillToDepositForMonth));
      details.push(S.paydayProgress(recon.paydaysPassed, recon.paydaysInMonth));
    }
    li.innerHTML = `
      <div class="card-top">
        <input type="checkbox" ${pond.selected ? 'checked' : ''} data-act="select">
        ${state.reorder ? `<button type="button" class="icon-btn" data-act="up" aria-label="Move up">▲</button>
          <button type="button" class="icon-btn" data-act="down" aria-label="Move down">▼</button>` : ''}
        <strong>${escapeHtml(pond.name)}</strong>
        <div class="row-actions">
          <button type="button" class="text-btn" data-act="edit">${S.edit}</button>
          <button type="button" class="text-btn" data-act="delete">${S.delete}</button>
        </div>
      </div>
      <div>${details.join('<br>')}</div>`;
    li.querySelector('[data-act="select"]').addEventListener('change', async (e) => {
      pond.selected = e.target.checked;
      await saveProfile();
    });
    li.querySelector('[data-act="edit"]').addEventListener('click', () => openPondDialog(pond));
    li.querySelector('[data-act="delete"]').addEventListener('click', () => confirmDeletePond(pond));
    if (state.reorder) {
      li.querySelector('[data-act="up"]').addEventListener('click', () => movePond(index, -1));
      li.querySelector('[data-act="down"]').addEventListener('click', () => movePond(index, 1));
    }
    list.appendChild(li);
  });
}

function renderTransactions() {
  const list = $('transactionList');
  list.innerHTML = '';
  const rows = allTransactions();
  let total = 0;
  const destTotals = {};
  if (rows.length === 0) {
    list.innerHTML = `<li class="empty-hint">${S.noTransactions}</li>`;
    $('tvTransactionsTotal').textContent = S.total(0);
    $('spinnerTransferTotals').innerHTML = '';
    $('tvTransferTotalsSummary').textContent = S.transfers(0);
    return;
  }
  for (const t of rows) {
    if (t.transferBucketId && t.amount < 0) {
      destTotals[t.envelopeName] = (destTotals[t.envelopeName] || 0) + Math.abs(t.amount);
    }
    if (!t.transferBucketId) total += Number(t.amount) || 0;
    const li = document.createElement('li');
    li.className = 'card';
    const split = isSplitPurchase(t);
    const group = split ? findTransactionsInGroup(state.profile.envelopes, t.splitPurchaseGroupId) : [];
    const expanded = split && state.expandedSplits.has(t.splitPurchaseGroupId);
    li.innerHTML = `
      <div class="card-top">
        <strong>${escapeHtml(t.envelopeName)}</strong>
        <span>${money(t.amount)}</span>
      </div>
      <div>${escapeHtml(displayFromIso(t.date))} · ${escapeHtml(t.comment || '')}</div>
      ${split ? `<div class="muted">${S.splitBreakdown(groupTotal(group))}</div>
        ${expanded ? `<pre>${escapeHtml(formatBreakdownLine(group))}</pre>` : ''}
        <button type="button" class="text-btn" data-act="split">${expanded ? 'Hide' : 'Show'} split</button>` : ''}
      <div class="row-actions">
        ${t.receiptImageUri ? `<button type="button" class="icon-btn" data-act="photo" aria-label="View receipt image">🖼</button>` : ''}
        <button type="button" class="text-btn" data-act="edit">${S.edit}</button>
        <button type="button" class="text-btn" data-act="delete">${S.delete}</button>
      </div>`;
    li.querySelector('[data-act="edit"]').addEventListener('click', () => openTransactionDialog(t));
    li.querySelector('[data-act="delete"]').addEventListener('click', () => deleteTransaction(t));
    const photo = li.querySelector('[data-act="photo"]');
    if (photo) photo.addEventListener('click', () => openPreview(t.receiptImageUri));
    const splitBtn = li.querySelector('[data-act="split"]');
    if (splitBtn) {
      splitBtn.addEventListener('click', () => {
        if (state.expandedSplits.has(t.splitPurchaseGroupId)) state.expandedSplits.delete(t.splitPurchaseGroupId);
        else state.expandedSplits.add(t.splitPurchaseGroupId);
        render();
      });
    }
    list.appendChild(li);
  }
  $('tvTransactionsTotal').textContent = S.total(roundToCents(total));
  const spinner = $('spinnerTransferTotals');
  spinner.innerHTML = '';
  let transferSum = 0;
  for (const [name, amount] of Object.entries(destTotals)) {
    transferSum += amount;
    const opt = document.createElement('option');
    opt.value = name;
    opt.textContent = `${name}: ${money(amount)}`;
    spinner.appendChild(opt);
  }
  $('tvTransferTotalsSummary').textContent = S.transfers(roundToCents(transferSum));
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

async function movePond(index, delta) {
  const next = index + delta;
  const list = state.profile.envelopes;
  if (next < 0 || next >= list.length) return;
  [list[index], list[next]] = [list[next], list[index]];
  await saveProfile();
}

function closeSheet() {
  $('sheet').classList.add('hidden');
  $('sheetCard').innerHTML = '';
}

function openSheet(html) {
  $('sheetCard').innerHTML = html;
  $('sheet').classList.remove('hidden');
}

function confirmDeletePond(pond) {
  openSheet(`
    <h3>${S.pondOptions}</h3>
    <p>${S.deletePond}</p>
    <div class="sheet-actions">
      <button type="button" class="btn-secondary" id="sheetCancel">${S.cancel}</button>
      <button type="button" class="btn-primary" id="sheetOk">${S.delete}</button>
    </div>`);
  $('sheetCancel').onclick = closeSheet;
  $('sheetOk').onclick = async () => {
    state.profile.envelopes = state.profile.envelopes.filter((e) => e !== pond);
    closeSheet();
    await saveProfile();
  };
}

function openPondDialog(existing) {
  const pond = existing || createEnvelope('', 0);
  openSheet(`
    <h3>${existing ? S.editPond : S.newPond}</h3>
    <label>${S.pondName}<input id="pondName" value="${escapeHtml(pond.name || '')}"></label>
    <label>${S.limit}<input id="pondLimit" type="number" step="0.01" value="${pond.limit || ''}"></label>
    <label>${S.accountLabel}<input id="pondAccount" type="number" step="0.01" placeholder="${S.accountHint}" value="${pond.accountBalance ?? ''}"></label>
    <p id="pondPreview" class="muted"></p>
    <div class="sheet-actions">
      <button type="button" class="btn-secondary" id="sheetCancel">${S.cancel}</button>
      <button type="button" class="btn-primary" id="sheetOk">${S.save}</button>
    </div>`);
  const updatePreview = () => {
    const accountText = $('pondAccount').value.trim();
    const limit = Number($('pondLimit').value) || 0;
    if (!accountText || !(state.profile.paydays || []).length) {
      $('pondPreview').textContent = '';
      return;
    }
    const clone = { ...pond, limit, accountBalance: Number(accountText) };
    const r = pondReconciliation(clone, state.profile);
    $('pondPreview').textContent = r && r.active ? S.footerReconcile(r.inBank, r.stillToDepositForMonth) : '';
  };
  $('pondAccount').oninput = updatePreview;
  $('pondLimit').oninput = updatePreview;
  updatePreview();
  $('sheetCancel').onclick = closeSheet;
  $('sheetOk').onclick = async () => {
    const name = $('pondName').value.trim();
    const limit = Number($('pondLimit').value);
    if (!name || !Number.isFinite(limit)) return;
    const accountRaw = $('pondAccount').value.trim();
    pond.name = name;
    pond.limit = limit;
    pond.originalLimit = limit;
    pond.accountBalance = accountRaw === '' ? null : Number(accountRaw);
    if (!existing) {
      pond.remaining = limit;
      pond.selected = true;
      state.profile.envelopes.push(pond);
    }
    closeSheet();
    await saveProfile();
  };
}

function pondOptions() {
  return (state.profile.envelopes || []).map((e) => `<option value="${escapeHtml(e.name)}">${escapeHtml(e.name)}</option>`).join('');
}

function openTransactionDialog(existing) {
  const month = displayedMonth();
  const isEdit = !!existing;
  let source = existing ? resolveAnchorTransaction(state.profile.envelopes, existing) : null;
  if (existing && isSplitPurchase(existing)) source = resolveForEdit(state.profile.envelopes, existing);
  const todayIso = ymd(new Date());
  let type = 'spending';
  if (source && isSplitPurchase(source)) type = 'split';
  else if (source && source.transferId) type = 'transfer';
  let timeMode = source && source.recurring ? 'recurring' : 'one';
  let receiptUri = source ? source.receiptImageUri : null;
  let ocrMode = ReceiptCaptureMode.AUTO;
  const defaultPond = state.profile.lastAddTransactionEnvelope || (state.profile.envelopes[0] && state.profile.envelopes[0].name) || '';
  let buckets = source && source.transferId
    ? getAllocations(state.profile.envelopes, source.transferId).map((b) => ({ ...b }))
    : [{ bucketId: randomUUID(), toEnvelope: '', amount: 0 }];
  let slices = source && isSplitPurchase(source)
    ? findTransactionsInGroup(state.profile.envelopes, source.splitPurchaseGroupId).map((t) => ({
      bucketId: t.splitPurchaseBucketId || randomUUID(),
      pondName: t.envelopeName,
      amount: t.amount,
    }))
    : [
      { bucketId: randomUUID(), pondName: defaultPond, amount: 0 },
      { bucketId: randomUUID(), pondName: '', amount: 0 },
    ];
  let freq = source && source.recurringFrequency ? source.recurringFrequency : 'weekly';
  let days = source && source.recurringDays ? [...source.recurringDays] : [];
  let commentHistory = [];
  let ocrWeights = null;
  let lastOcrAmount = null;
  let lastOcrLines = [];

  const draw = () => {
    openSheet(`
      <h3>${isEdit ? 'Edit transaction' : 'Add transaction'}</h3>
      <div class="icon-row">
        <button type="button" class="icon-btn" id="rxCamera" aria-label="${S.camera}">📷</button>
        <button type="button" class="icon-btn" id="rxGallery" aria-label="${S.gallery}">🖼</button>
        <button type="button" class="icon-btn" id="rxPreview" ${receiptUri ? '' : 'disabled'} aria-label="${S.preview}">👁</button>
        <button type="button" class="icon-btn" id="rxRemove" ${receiptUri ? '' : 'disabled'} aria-label="${S.remove}">🗑</button>
        <select id="ocrMode">
          <option value="AUTO">${S.modeAuto}</option>
          <option value="RECEIPT">${S.modeReceipt}</option>
          <option value="RESTAURANT">${S.modeRestaurant}</option>
          <option value="GAS">${S.modeGas}</option>
        </select>
      </div>
      <input id="rxCamFile" type="file" accept="image/*" capture="environment" class="visually-hidden">
      <input id="rxGalFile" type="file" accept="image/*" class="visually-hidden">
      <p id="rxStatus" class="muted"></p>
      <label>${S.selectPond}
        <select id="txPond">${pondOptions()}</select>
      </label>
      <label>${S.date}<input id="txDate" type="date" value="${source ? isoFromDisplay(source.date) || source.date : todayIso}"></label>
      <label>${S.amount}<input id="txAmount" type="number" step="0.01" value="${source && type !== 'split' ? source.amount : ''}"></label>
      <label>${S.comment}<input id="txComment" autocomplete="off" value="${escapeHtml(source ? source.comment || '' : '')}"></label>
      <div id="txCommentSuggest" class="comment-suggest" hidden role="listbox" aria-label="Comment suggestions"></div>
      <div class="tabs" id="typeTabs">
        <button type="button" class="tab ${type === 'spending' ? 'on' : ''}" data-type="spending">${S.spending}</button>
        <button type="button" class="tab ${type === 'transfer' ? 'on' : ''}" data-type="transfer">${S.transfer}</button>
        <button type="button" class="tab ${type === 'split' ? 'on' : ''}" data-type="split">${S.split}</button>
      </div>
      <div id="timeTabs" class="tabs ${type === 'spending' ? '' : 'hidden'}">
        <button type="button" class="tab ${timeMode === 'one' ? 'on' : ''}" data-time="one">${S.oneTime}</button>
        <button type="button" class="tab ${timeMode === 'recurring' ? 'on' : ''}" data-time="recurring">${S.recurring}</button>
      </div>
      <div id="recurringBlock" class="${type === 'spending' && timeMode === 'recurring' ? '' : 'hidden'}">
        <div class="tabs">
          <button type="button" class="tab ${freq === 'weekly' ? 'on' : ''}" data-freq="weekly">${S.weekly}</button>
          <button type="button" class="tab ${freq === 'bi-weekly' ? 'on' : ''}" data-freq="bi-weekly">${S.biWeekly}</button>
          <button type="button" class="tab ${freq === 'monthly' ? 'on' : ''}" data-freq="monthly">${S.monthly}</button>
        </div>
        <div id="dayPicker" class="day-grid"></div>
      </div>
      <div id="transferBlock" class="${type === 'transfer' ? '' : 'hidden'}"></div>
      <div id="splitBlock" class="${type === 'split' ? '' : 'hidden'}"></div>
      <p id="txError" class="error" hidden></p>
      <div class="sheet-actions">
        <button type="button" class="btn-secondary" id="sheetCancel">${S.cancel}</button>
        <button type="button" class="btn-primary" id="sheetOk">${S.save}</button>
      </div>`);
    $('ocrMode').value = ocrMode;
    if (source) $('txPond').value = source.envelopeName || defaultPond;
    else if (defaultPond) $('txPond').value = defaultPond;
    wireTypeTabs();
    renderDayPicker();
    renderTransferBlock();
    renderSplitBlock();
    $('sheetCancel').onclick = closeSheet;
    $('sheetOk').onclick = saveTx;
    $('rxCamera').onclick = () => $('rxCamFile').click();
    $('rxGallery').onclick = () => $('rxGalFile').click();
    $('rxCamFile').onchange = (e) => attachReceipt(e.target.files[0]);
    $('rxGalFile').onchange = (e) => attachReceipt(e.target.files[0]);
    $('rxPreview').onclick = () => receiptUri && openPreview(receiptUri);
    $('rxRemove').onclick = () => {
      receiptUri = null;
      $('rxPreview').disabled = true;
      $('rxRemove').disabled = true;
    };
    $('ocrMode').onchange = (e) => { ocrMode = e.target.value; };
    wireCommentSuggest();
  };

  function wireCommentSuggest() {
    const input = $('txComment');
    const box = $('txCommentSuggest');
    if (!input || !box) return;
    const renderSuggest = () => {
      const matches = suggestions(commentHistory, input.value);
      if (document.activeElement !== input || !String(input.value || '').trim() || matches.length === 0) {
        box.hidden = true;
        box.innerHTML = '';
        return;
      }
      box.hidden = false;
      box.innerHTML = matches.map((comment) => (
        `<button type="button" class="comment-suggest-item" role="option">${escapeHtml(comment)}</button>`
      )).join('');
      box.querySelectorAll('button').forEach((btn) => {
        btn.onmousedown = (event) => event.preventDefault();
        btn.onclick = () => {
          input.value = btn.textContent;
          box.hidden = true;
          box.innerHTML = '';
        };
      });
    };
    input.addEventListener('input', renderSuggest);
    input.addEventListener('focus', renderSuggest);
    input.addEventListener('blur', () => {
      setTimeout(() => {
        if (document.activeElement !== input) {
          box.hidden = true;
          box.innerHTML = '';
        }
      }, 160);
    });
  }

  function wireTypeTabs() {
    $('sheetCard').querySelectorAll('[data-type]').forEach((btn) => {
      btn.onclick = () => {
        if (isEdit && type === 'split' && btn.dataset.type !== 'split') return;
        if (isEdit && type !== 'split' && btn.dataset.type === 'split') return;
        type = btn.dataset.type;
        $('typeTabs').querySelectorAll('.tab').forEach((b) => b.classList.toggle('on', b.dataset.type === type));
        $('timeTabs').classList.toggle('hidden', type !== 'spending');
        $('recurringBlock').classList.toggle('hidden', !(type === 'spending' && timeMode === 'recurring'));
        $('transferBlock').classList.toggle('hidden', type !== 'transfer');
        $('splitBlock').classList.toggle('hidden', type !== 'split');
        if (type === 'transfer') seedTransfer();
        if (type === 'split') seedSplit();
        renderTransferBlock();
        renderSplitBlock();
      };
    });
    $('sheetCard').querySelectorAll('[data-time]').forEach((btn) => {
      btn.onclick = () => {
        timeMode = btn.dataset.time;
        $('timeTabs').querySelectorAll('.tab').forEach((b) => b.classList.toggle('on', b.dataset.time === timeMode));
        $('recurringBlock').classList.toggle('hidden', !(type === 'spending' && timeMode === 'recurring'));
      };
    });
    $('sheetCard').querySelectorAll('[data-freq]').forEach((btn) => {
      btn.onclick = () => {
        freq = btn.dataset.freq;
        days = [];
        $('recurringBlock').querySelectorAll('[data-freq]').forEach((b) => b.classList.toggle('on', b.dataset.freq === freq));
        renderDayPicker();
      };
    });
  }

  function renderDayPicker() {
    const grid = $('dayPicker');
    if (!grid) return;
    grid.innerHTML = '';
    if (freq === 'monthly') {
      for (let d = 1; d <= 31; d++) {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = `day-cell ${days.includes(d) ? 'on' : ''}`;
        b.textContent = String(d);
        b.onclick = () => {
          if (days.includes(d)) days = days.filter((x) => x !== d);
          else days.push(d);
          renderDayPicker();
        };
        grid.appendChild(b);
      }
    } else {
      const labels = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
      labels.forEach((label, i) => {
        const value = i + 1;
        const b = document.createElement('button');
        b.type = 'button';
        b.className = `day-cell ${days.includes(value) ? 'on' : ''}`;
        b.textContent = label;
        b.onclick = () => {
          if (days.includes(value)) days = days.filter((x) => x !== value);
          else days.push(value);
          renderDayPicker();
        };
        grid.appendChild(b);
      });
    }
  }

  function seedTransfer() {
    const amount = Number($('txAmount').value) || 0;
    if (buckets.length === 1 && buckets[0].amount === 0 && amount > 0) {
      buckets[0].amount = amount;
    }
  }

  function seedSplit() {
    const amount = Number($('txAmount').value) || 0;
    const purchase = Number($('splitTotal')?.value) || amount;
    if (splitAllocated(slices) === 0 && purchase > 0) {
      const percents = splitIntegerPercentsFirstCeiling(Math.max(2, slices.length));
      const parts = splitTotalByPercents(purchase, percents);
      slices.forEach((s, i) => { s.amount = parts[i] || 0; });
    }
  }

  function redistribute(list, total, key) {
    const percents = splitIntegerPercentsFirstCeiling(list.length);
    const parts = splitTotalByPercents(total, percents);
    list.forEach((item, i) => { item[key] = parts[i] || 0; });
  }

  function renderTransferBlock() {
    const host = $('transferBlock');
    if (!host) return;
    const sourceName = $('txPond').value;
    const dests = excludingSource(state.profile.envelopes, sourceName);
    const amount = Number($('txAmount').value) || 0;
    host.innerHTML = buckets.map((bucket, i) => {
      const max = amount;
      const sliderMax = computeSliderMaximum(max, 0.5);
      const labels = buildScaleLabels(max, 3).join(' ');
      return `<div class="bucket">
        <label>${S.transferTo}
          <select data-bucket-dest="${i}">${dests.map((n) => `<option ${n === bucket.toEnvelope ? 'selected' : ''}>${escapeHtml(n)}</option>`).join('')}</select>
        </label>
        <label>${S.amount}<input type="number" step="0.01" data-bucket-amt="${i}" value="${bucket.amount || 0}"></label>
        <input type="range" min="0" max="${sliderMax}" step="0.5" value="${Math.min(bucket.amount || 0, sliderMax)}" data-bucket-sl="${i}">
        <div class="scale">${labels}</div>
        <button type="button" class="text-btn" data-bucket-del="${i}">Remove</button>
      </div>`;
    }).join('') + `<button type="button" class="btn-secondary" id="addBucket">${S.addBucket}</button>
      <p class="muted">${S.allocated(transferAllocated(buckets), amount)}</p>`;
    host.querySelectorAll('[data-bucket-dest]').forEach((el) => {
      el.onchange = () => { buckets[Number(el.dataset.bucketDest)].toEnvelope = el.value; };
    });
    host.querySelectorAll('[data-bucket-amt]').forEach((el) => {
      el.onchange = () => { buckets[Number(el.dataset.bucketAmt)].amount = Number(el.value) || 0; renderTransferBlock(); };
    });
    host.querySelectorAll('[data-bucket-sl]').forEach((el) => {
      el.oninput = () => {
        const i = Number(el.dataset.bucketSl);
        const sliderMax = Number(el.max);
        buckets[i].amount = resolveAmountAtSliderMax(Number(el.value), sliderMax, amount);
        renderTransferBlock();
      };
    });
    host.querySelectorAll('[data-bucket-del]').forEach((el) => {
      el.onclick = () => {
        if (buckets.length === 1) return;
        buckets.splice(Number(el.dataset.bucketDel), 1);
        renderTransferBlock();
      };
    });
    const add = $('addBucket');
    if (add) {
      add.onclick = () => {
        buckets.push({ bucketId: randomUUID(), toEnvelope: dests[0] || '', amount: 0 });
        redistribute(buckets, amount, 'amount');
        renderTransferBlock();
      };
    }
  }

  function renderSplitBlock() {
    const host = $('splitBlock');
    if (!host) return;
    const purchase = Number($('txAmount').value) || splitAllocated(slices) || 0;
    host.innerHTML = `<label>${S.purchaseTotal}<input id="splitTotal" type="number" step="0.01" value="${purchase}"></label>`
      + slices.map((slice, i) => `<div class="bucket">
        <label>${S.selectPond}
          <select data-slice-pond="${i}">${pondOptions().replace(`value="${escapeHtml(slice.pondName)}"`, `value="${escapeHtml(slice.pondName)}" selected`)}</select>
        </label>
        <label>${S.amount}<input type="number" step="0.01" data-slice-amt="${i}" value="${slice.amount || 0}"></label>
        <button type="button" class="text-btn" data-slice-del="${i}">Remove</button>
      </div>`).join('')
      + `<button type="button" class="btn-secondary" id="addSlice">${S.addSlice}</button>
         <p class="muted">${S.allocated(splitAllocated(slices), purchase)}</p>`;
    host.querySelectorAll('[data-slice-pond]').forEach((el) => {
      const i = Number(el.dataset.slicePond);
      el.value = slices[i].pondName;
      el.onchange = () => { slices[i].pondName = el.value; };
    });
    host.querySelectorAll('[data-slice-amt]').forEach((el) => {
      el.onchange = () => { slices[Number(el.dataset.sliceAmt)].amount = Number(el.value) || 0; renderSplitBlock(); };
    });
    host.querySelectorAll('[data-slice-del]').forEach((el) => {
      el.onclick = () => {
        if (slices.length <= 2) return;
        slices.splice(Number(el.dataset.sliceDel), 1);
        renderSplitBlock();
      };
    });
    const add = $('addSlice');
    if (add) {
      add.onclick = () => {
        const total = Number($('splitTotal').value) || 0;
        slices.push({ bucketId: randomUUID(), pondName: state.profile.envelopes[0]?.name || '', amount: 0 });
        redistribute(slices, total, 'amount');
        renderSplitBlock();
      };
    }
  }

  async function attachReceipt(file) {
    if (!file) return;
    $('rxStatus').textContent = S.ocrReading;
    try {
      const form = new FormData();
      form.append('image', file);
      const uploaded = await api('/api/receipts', { method: 'POST', body: form });
      receiptUri = uploaded.uri;
      $('rxPreview').disabled = false;
      $('rxRemove').disabled = false;
      const draft = await runOcr(file, ocrMode, ocrWeights);
      lastOcrAmount = draft?.totalAmount != null ? draft.totalAmount : null;
      lastOcrLines = Array.isArray(draft?.sourceLines) ? draft.sourceLines : [];
      if (draft?.totalAmount && !$('txAmount').value) $('txAmount').value = draft.totalAmount;
      if (draft?.dateYyyyMmDd) $('txDate').value = draft.dateYyyyMmDd;
      if (draft?.merchantForComment && !$('txComment').value) $('txComment').value = draft.merchantForComment;
      if (draft?.dateYyyyMmDd && isIsoDateOutsideFilterRange(draft.dateYyyyMmDd, state.profile.dateFilterStartDisplay, state.profile.dateFilterEndDisplay)) {
        $('rxStatus').textContent = S.dateOutside;
      } else {
        $('rxStatus').textContent = '';
      }
    } catch {
      $('rxStatus').textContent = S.ocrFailed;
    }
  }

  async function saveTx() {
    const err = $('txError');
    err.hidden = true;
    const pondName = canonicalName(state.profile.envelopes, $('txPond').value);
    if (!pondName) {
      err.hidden = false;
      err.textContent = S.noPond;
      return;
    }
    const date = $('txDate').value;
    const amount = Number($('txAmount').value);
    const comment = $('txComment').value.trim();
    if (!date || !Number.isFinite(amount) || amount <= 0 && type !== 'split') {
      err.hidden = false;
      err.textContent = 'Enter a date and amount.';
      return;
    }
    ensureDateFilterIncludes(date);
    state.profile.lastAddTransactionEnvelope = pondName;
    const month = displayedMonth();
    if (type === 'split') {
      const purchase = Number($('splitTotal')?.value) || amount;
      const result = validateSplit(purchase, slices);
      if (!result.valid) {
        err.hidden = false;
        err.textContent = result.message;
        return;
      }
      applyGroup(state.profile.envelopes, source?.splitPurchaseGroupId, date, comment, receiptUri, slices, month);
    } else {
      let tx = source;
      if (!tx || isSplitPurchase(source) || (source && source.envelopeName !== pondName && !source.transferId)) {
        tx = createTransaction(pondName, amount, date, comment);
        const env = findByName(state.profile.envelopes, pondName);
        addTransaction(env, tx, month);
        if (isEdit && existing && existing !== tx) {
          removePlainTransaction(existing);
        }
      } else {
        tx.amount = amount;
        tx.date = date;
        tx.month = date.slice(0, 7);
        tx.comment = comment;
        tx.envelopeName = pondName;
      }
      tx.receiptImageUri = receiptUri;
      if (type === 'transfer') {
        buckets.forEach((b) => { if (!b.toEnvelope) b.toEnvelope = excludingSource(state.profile.envelopes, pondName)[0]; });
        const result = validateTransfer(amount, pondName, buckets);
        if (!result.valid) {
          err.hidden = false;
          err.textContent = result.message;
          return;
        }
        applyTransferGroup(state.profile.envelopes, tx, pondName, buckets);
      } else if (tx.transferId) {
        detachTransferGroup(state.profile.envelopes, tx);
      }
      if (type === 'spending' && timeMode === 'recurring') {
        tx.recurring = true;
        tx.recurringTemplate = true;
        tx.recurringFrequency = freq;
        tx.recurringDays = [...days];
        if (!tx.recurringSeriesId) tx.recurringSeriesId = randomUUID();
        ensureRecurringTransactions(state.profile.envelopes, month);
      } else {
        tx.recurring = false;
        tx.recurringTemplate = false;
      }
    }
    const savedAmount = type === 'split' ? (Number($('splitTotal')?.value) || amount) : amount;
    try {
      const learned = await api('/api/learning', {
        method: 'POST',
        body: JSON.stringify({
          comment,
          ocrAmount: lastOcrAmount,
          savedAmount,
          lines: lastOcrLines,
          mode: ocrMode,
        }),
      });
      commentHistory = learned.comments || commentHistory;
    } catch {
      /* sidecar is optional; transaction save still proceeds */
    }
    closeSheet();
    await saveProfile();
  }

  api('/api/learning').then((data) => {
    commentHistory = data.comments || [];
    ocrWeights = data.weights || null;
  }).catch(() => {}).finally(() => {
    draw();
  });
}

function removePlainTransaction(tx) {
  for (const env of state.profile.envelopes) {
    env.transactions = getTransactions(env).filter((t) => t !== tx);
  }
}

async function deleteTransaction(tx) {
  if (isSplitPurchase(tx)) {
    openSheet(`<h3>${S.delete}</h3><p>${S.deleteSplit}</p>
      <div class="sheet-actions">
        <button type="button" class="btn-secondary" id="sheetCancel">${S.cancel}</button>
        <button type="button" class="btn-primary" id="sheetOk">${S.delete}</button>
      </div>`);
    $('sheetCancel').onclick = closeSheet;
    $('sheetOk').onclick = async () => {
      removeGroup(state.profile.envelopes, tx.splitPurchaseGroupId);
      closeSheet();
      await saveProfile();
    };
    return;
  }
  const source = resolveAnchorTransaction(state.profile.envelopes, tx);
  if (source.transferId) detachTransferGroup(state.profile.envelopes, source);
  removePlainTransaction(source);
  await saveProfile();
}

async function runOcr(file, mode, weights) {
  if (!window.Tesseract) {
    return api('/api/ocr', { method: 'POST', body: JSON.stringify({ lines: [], mode }) }).then((d) => d.draft);
  }
  const result = await window.Tesseract.recognize(file, 'eng');
  const lines = (result.data.lines || []).map((line) => ocrLine(
    line.text || '',
    (line.confidence || 80) / 100,
    line.bbox ? Math.abs(line.bbox.y1 - line.bbox.y0) : 0,
  ));
  const local = parseReceipt(ocrResult(lines), mode, weights);
  try {
    const remote = await api('/api/ocr', { method: 'POST', body: JSON.stringify({ lines, mode }) });
    return remote.draft || local;
  } catch {
    return local;
  }
}

function openPreview(uri) {
  state.previewUri = uri;
  state.previewRotation = 0;
  state.previewScale = 1;
  state.previewX = 0;
  state.previewY = 0;
  const img = $('previewImage');
  img.src = uri;
  img.onerror = () => toast(S.previewFailed);
  applyPreviewTransform();
  $('previewSaveRot').disabled = true;
  $('preview').classList.remove('hidden');
}

function applyPreviewTransform() {
  const img = $('previewImage');
  img.style.transform = `translate(${state.previewX}px, ${state.previewY}px) scale(${state.previewScale}) rotate(${state.previewRotation}deg)`;
}

async function savePreviewRotation() {
  if (!state.previewUri || state.previewRotation % 360 === 0) return;
  const img = $('previewImage');
  const canvas = document.createElement('canvas');
  const rad = ((state.previewRotation % 360) + 360) % 360;
  const swap = rad === 90 || rad === 270;
  canvas.width = swap ? img.naturalHeight : img.naturalWidth;
  canvas.height = swap ? img.naturalWidth : img.naturalHeight;
  const ctx = canvas.getContext('2d');
  ctx.translate(canvas.width / 2, canvas.height / 2);
  ctx.rotate(rad * Math.PI / 180);
  ctx.drawImage(img, -img.naturalWidth / 2, -img.naturalHeight / 2);
  const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92));
  const id = state.previewUri.split('/').pop();
  const form = new FormData();
  form.append('image', blob, 'receipt.jpg');
  try {
    await api(`/api/receipts/${id}`, { method: 'PUT', body: form });
    state.previewRotation = 0;
    img.src = `${state.previewUri}?t=${Date.now()}`;
    $('previewSaveRot').disabled = true;
  } catch {
    toast(S.saveFailed);
  }
}

function closePreview() {
  if (state.previewRotation % 360 !== 0) {
    if (!confirm(S.discardMessage)) return;
  }
  $('preview').classList.add('hidden');
}

function openBillsDialog() {
  const bills = new Set(state.profile.billsDays || []);
  const pays = new Set(state.profile.paydays || []);
  let tab = 'bills';
  const draw = () => {
    openSheet(`
      <h3>${S.billsPaydays}</h3>
      <div class="tabs">
        <button type="button" class="tab ${tab === 'bills' ? 'on' : ''}" id="tabBills">${S.billsDays}</button>
        <button type="button" class="tab ${tab === 'pay' ? 'on' : ''}" id="tabPay">${S.paydays}</button>
      </div>
      <p class="muted">${tab === 'bills' ? S.billsMessage : S.paydaysMessage}</p>
      <div id="dayGrid" class="day-grid"></div>
      <div class="sheet-actions">
        <button type="button" class="btn-secondary" id="sheetCancel">${S.cancel}</button>
        <button type="button" class="btn-primary" id="sheetOk">${S.save}</button>
      </div>`);
    const set = tab === 'bills' ? bills : pays;
    const grid = $('dayGrid');
    for (let d = 1; d <= 31; d++) {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = `day-cell ${set.has(d) ? 'on' : ''}`;
      b.textContent = String(d);
      b.onclick = () => {
        if (set.has(d)) set.delete(d);
        else set.add(d);
        draw();
      };
      grid.appendChild(b);
    }
    $('tabBills').onclick = () => { tab = 'bills'; draw(); };
    $('tabPay').onclick = () => { tab = 'pay'; draw(); };
    $('sheetCancel').onclick = closeSheet;
    $('sheetOk').onclick = async () => {
      state.profile.billsDays = Array.from(bills).sort((a, b) => a - b);
      state.profile.paydays = Array.from(pays).sort((a, b) => a - b);
      closeSheet();
      await saveProfile();
    };
  };
  draw();
}

function applyBillsFilter() {
  const days = state.profile.billsDays || [];
  if (!days.length) {
    toast(S.configureBills);
    return;
  }
  if (!state.profile.billsFilterActive) {
    state.profile.billsFilterSavedStartDisplay = state.profile.dateFilterStartDisplay;
    state.profile.billsFilterSavedEndDisplay = state.profile.dateFilterEndDisplay;
    const anchor = computeAnchorDate(new Date(), days);
    if (!anchor) {
      toast(S.noBillsAnchor);
      return;
    }
    state.profile.dateFilterStartDisplay = formatDisplayDate(anchor);
    state.profile.dateFilterEndDisplay = formatDisplayDate(new Date());
    state.profile.billsFilterActive = true;
  } else {
    state.profile.dateFilterStartDisplay = state.profile.billsFilterSavedStartDisplay;
    state.profile.dateFilterEndDisplay = state.profile.billsFilterSavedEndDisplay;
    state.profile.billsFilterActive = false;
  }
  saveProfile();
}

function resetMonthFilter(month) {
  const start = firstDayOfMonth(month);
  const end = lastDayOfMonth(month);
  state.profile.dateFilterStartDisplay = start ? formatDisplayDate(start) : null;
  state.profile.dateFilterEndDisplay = end ? formatDisplayDate(end) : null;
  state.profile.billsFilterActive = false;
}

function wirePreviewGestures() {
  const stage = $('previewStage');
  let lastDist = 0;
  let lastX = 0;
  let lastY = 0;
  stage.addEventListener('pointerdown', (e) => {
    lastX = e.clientX;
    lastY = e.clientY;
    stage.setPointerCapture(e.pointerId);
  });
  stage.addEventListener('pointermove', (e) => {
    if (e.buttons !== 1) return;
    state.previewX += e.clientX - lastX;
    state.previewY += e.clientY - lastY;
    lastX = e.clientX;
    lastY = e.clientY;
    applyPreviewTransform();
  });
  stage.addEventListener('wheel', (e) => {
    e.preventDefault();
    state.previewScale = Math.max(0.5, Math.min(5, state.previewScale + (e.deltaY < 0 ? 0.1 : -0.1)));
    applyPreviewTransform();
  }, { passive: false });
  stage.addEventListener('dblclick', () => {
    state.previewScale = 1;
    state.previewX = 0;
    state.previewY = 0;
    applyPreviewTransform();
  });
  stage.addEventListener('touchmove', (e) => {
    if (e.touches.length === 2) {
      const dx = e.touches[0].clientX - e.touches[1].clientX;
      const dy = e.touches[0].clientY - e.touches[1].clientY;
      const dist = Math.hypot(dx, dy);
      if (lastDist) {
        state.previewScale = Math.max(0.5, Math.min(5, state.previewScale * (dist / lastDist)));
        applyPreviewTransform();
      }
      lastDist = dist;
    }
  }, { passive: true });
  stage.addEventListener('touchend', () => { lastDist = 0; });
}

function bindUi() {
  $('btnLogin').onclick = async () => {
    $('authError').hidden = true;
    try {
      const data = await api('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ login: $('authLogin').value, password: $('authPassword').value }),
      });
      state.user = data.user;
      state.profile = data.profile;
      showAuth(false);
      render();
    } catch (e) {
      $('authError').hidden = false;
      $('authError').textContent = e.message;
    }
  };
  $('btnRegister').onclick = async () => {
    $('authError').hidden = true;
    try {
      const data = await api('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({ login: $('authLogin').value, password: $('authPassword').value }),
      });
      state.user = data.user;
      state.profile = data.profile;
      showAuth(false);
      render();
    } catch (e) {
      $('authError').hidden = false;
      $('authError').textContent = e.message;
    }
  };
  $('btnSignOut').onclick = async () => {
    await api('/api/auth/logout', { method: 'POST' });
    state.user = null;
    state.profile = null;
    showAuth(true);
  };
  $('btnAddEnvelope').onclick = () => openPondDialog(null);
  $('btnAddTransaction').onclick = () => {
    if (!state.profile.envelopes.length) {
      toast('Add a pond first.');
      return;
    }
    openTransactionDialog(null);
  };
  $('btnPondSelectToggle').onclick = async () => {
    const all = state.profile.envelopes.every((e) => e.selected);
    state.profile.envelopes.forEach((e) => { e.selected = !all; });
    await saveProfile();
  };
  $('btnPondReorder').onclick = () => {
    state.reorder = !state.reorder;
    if (state.profile.envelopesCollapsed) state.reorder = false;
    render();
  };
  $('btnToggleEnvelopes').onclick = async () => {
    state.profile.envelopesCollapsed = !state.profile.envelopesCollapsed;
    if (state.profile.envelopesCollapsed) state.reorder = false;
    await saveProfile();
  };
  $('btnPrevMonth').onclick = async () => {
    const next = addMonthsYearMonth(displayedMonth(), -1);
    state.profile.displayedMonth = next;
    resetMonthFilter(next);
    initializeMonthForAll(next);
    ensureRecurringTransactions(state.profile.envelopes, next);
    await saveProfile();
  };
  $('btnNextMonth').onclick = async () => {
    const next = addMonthsYearMonth(displayedMonth(), 1);
    state.profile.displayedMonth = next;
    resetMonthFilter(next);
    initializeMonthForAll(next);
    ensureRecurringTransactions(state.profile.envelopes, next);
    await saveProfile();
  };
  $('btnStartDate').onclick = () => $('hiddenStart').showPicker?.() || $('hiddenStart').click();
  $('btnEndDate').onclick = () => $('hiddenEnd').showPicker?.() || $('hiddenEnd').click();
  $('hiddenStart').onchange = async (e) => {
    const d = parseIsoDate(e.target.value);
    if (d) state.profile.dateFilterStartDisplay = formatDisplayDate(d);
    state.profile.billsFilterActive = false;
    await saveProfile();
  };
  $('hiddenEnd').onchange = async (e) => {
    const d = parseIsoDate(e.target.value);
    if (d) state.profile.dateFilterEndDisplay = formatDisplayDate(d);
    state.profile.billsFilterActive = false;
    await saveProfile();
  };
  $('btnBillsSetup').onclick = openBillsDialog;
  $('btnBillsFilter').onclick = applyBillsFilter;
  $('btnToggleTransfers').onclick = async () => {
    state.profile.transfersVisible = !state.profile.transfersVisible;
    await saveProfile();
  };
  $('btnRecalculate').onclick = () => {
    openSheet(`<h3>${S.recalculateTitle}</h3><p>${S.recalculateMessage}</p>
      <div class="sheet-actions">
        <button type="button" class="btn-secondary" id="sheetCancel">${S.cancel}</button>
        <button type="button" class="btn-primary" id="sheetOk">${S.recalculate}</button>
      </div>`);
    $('sheetCancel').onclick = closeSheet;
    $('sheetOk').onclick = async () => {
      recalculateBalances(state.profile);
      closeSheet();
      await saveProfile();
      toast(S.recalculated);
    };
  };
  $('sheet').addEventListener('click', (e) => {
    if (e.target.id === 'sheet') closeSheet();
  });
  $('previewClose').onclick = closePreview;
  $('previewRotLeft').onclick = () => {
    state.previewRotation -= 90;
    $('previewSaveRot').disabled = state.previewRotation % 360 === 0;
    applyPreviewTransform();
  };
  $('previewRotRight').onclick = () => {
    state.previewRotation += 90;
    $('previewSaveRot').disabled = state.previewRotation % 360 === 0;
    applyPreviewTransform();
  };
  $('previewSaveRot').onclick = () => {
    if (!confirm(S.replaceMessage)) return;
    savePreviewRotation();
  };
  wirePreviewGestures();
}

function initializeMonthForAll(month) {
  for (const env of state.profile.envelopes) {
    initializeMonth(env, month, false);
    calculateRemaining(env, month);
  }
}

async function boot() {
  bindUi();
  try {
    const data = await api('/api/auth/me');
    if (data.user) {
      state.user = data.user;
      state.profile = data.profile;
      showAuth(false);
      render();
      return;
    }
  } catch {
    /* signed out */
  }
  showAuth(true);
}

boot();
