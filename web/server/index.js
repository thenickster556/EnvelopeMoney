import 'dotenv/config';
import express from 'express';
import session from 'express-session';
import MongoStore from 'connect-mongo';
import multer from 'multer';
import bcrypt from 'bcryptjs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { connectDb, getDb, getReceiptBucket, ObjectId } from './db.js';
import { applyLaunchAndDisplay, emptyProfile, publicProfile, refreshBalances } from '../domain/profileEngine.js';
import { buildDemoProfile, shouldSeedDemoProfile } from '../domain/demoSeed.js';
import { parse as parseReceipt, ocrLine, ocrResult, ReceiptCaptureMode } from '../domain/receiptFieldParser.js';
import { readLearning, saveLearning } from './learningStore.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, '..');
const PORT = Number(process.env.PORT || 3000);
const mongoUri = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017';
const dbName = process.env.MONGODB_DB || 'mountain_money';
const sessionSecret = process.env.SESSION_SECRET || 'mountain-money-dev-secret-change-me';

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 8 * 1024 * 1024 },
});

function requireAuth(req, res, next) {
  if (!req.session.userId) {
    res.status(401).json({ error: 'Sign in required' });
    return;
  }
  next();
}

function normalizeLogin(login) {
  return String(login || '').trim().toLowerCase();
}

async function loadOrCreateProfile(userId) {
  const db = getDb();
  let doc = await db.collection('profiles').findOne({ userId });
  let raw = doc ? { ...doc } : emptyProfile();
  delete raw._id;
  delete raw.userId;
  const seeded = shouldSeedDemoProfile(raw);
  if (seeded) {
    raw = buildDemoProfile();
  }
  const { profile, rollover } = applyLaunchAndDisplay(raw);
  if (!doc || seeded || rollover.requiresPersistence) {
    await db.collection('profiles').updateOne(
      { userId },
      { $set: { ...profile, userId, updatedAt: new Date() } },
      { upsert: true },
    );
  }
  return profile;
}

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '2mb' }));
app.use(session({
  name: 'mm.sid',
  secret: sessionSecret,
  resave: false,
  saveUninitialized: false,
  cookie: { httpOnly: true, sameSite: 'lax', maxAge: 7 * 24 * 60 * 60 * 1000 },
  store: MongoStore.create({
    mongoUrl: mongoUri,
    dbName,
    collectionName: 'sessions',
  }),
}));

app.use(express.static(path.join(root, 'public')));
app.use('/domain', express.static(path.join(root, 'domain')));

app.get('/api/health', (_req, res) => {
  res.json({ ok: true });
});

app.post('/api/auth/register', async (req, res) => {
  try {
    const login = normalizeLogin(req.body?.login);
    const password = String(req.body?.password || '');
    if (login.length < 3 || login.length > 80) {
      res.status(400).json({ error: 'Use a username or email of at least 3 characters.' });
      return;
    }
    if (password.length < 6) {
      res.status(400).json({ error: 'Password must be at least 6 characters.' });
      return;
    }
    const db = getDb();
    const existing = await db.collection('users').findOne({ login });
    if (existing) {
      res.status(409).json({ error: 'That login is already registered.' });
      return;
    }
    const passwordHash = await bcrypt.hash(password, 10);
    const result = await db.collection('users').insertOne({
      login,
      passwordHash,
      createdAt: new Date(),
    });
    const userId = String(result.insertedId);
    req.session.userId = userId;
    req.session.login = login;
    const profile = await loadOrCreateProfile(userId);
    res.json({ user: { id: userId, login }, profile: publicProfile(profile) });
  } catch (err) {
    console.error('register failed', err.message);
    res.status(500).json({ error: 'Could not register.' });
  }
});

app.post('/api/auth/login', async (req, res) => {
  try {
    const login = normalizeLogin(req.body?.login);
    const password = String(req.body?.password || '');
    const db = getDb();
    const user = await db.collection('users').findOne({ login });
    if (!user || !(await bcrypt.compare(password, user.passwordHash))) {
      res.status(401).json({ error: 'Login or password is incorrect.' });
      return;
    }
    const userId = String(user._id);
    req.session.userId = userId;
    req.session.login = user.login;
    const profile = await loadOrCreateProfile(userId);
    res.json({ user: { id: userId, login: user.login }, profile: publicProfile(profile) });
  } catch (err) {
    console.error('login failed', err.message);
    res.status(500).json({ error: 'Could not sign in.' });
  }
});

app.post('/api/auth/logout', (req, res) => {
  req.session.destroy(() => {
    res.clearCookie('mm.sid');
    res.json({ ok: true });
  });
});

app.get('/api/auth/me', async (req, res) => {
  if (!req.session.userId) {
    res.json({ user: null, profile: null });
    return;
  }
  try {
    const profile = await loadOrCreateProfile(req.session.userId);
    res.json({ user: { id: req.session.userId, login: req.session.login }, profile: publicProfile(profile) });
  } catch (err) {
    console.error('me failed', err.message);
    res.status(500).json({ error: 'Could not load profile.' });
  }
});

app.get('/api/profile', requireAuth, async (req, res) => {
  try {
    const profile = await loadOrCreateProfile(req.session.userId);
    res.json({ profile: publicProfile(profile) });
  } catch (err) {
    console.error('profile get failed', err.message);
    res.status(500).json({ error: 'Could not load profile.' });
  }
});

app.put('/api/profile', requireAuth, async (req, res) => {
  try {
    const incoming = req.body?.profile;
    if (!incoming || typeof incoming !== 'object') {
      res.status(400).json({ error: 'Profile body required.' });
      return;
    }
    const merged = { ...emptyProfile(), ...incoming };
    delete merged._id;
    delete merged.userId;
    refreshBalances(merged);
    const db = getDb();
    await db.collection('profiles').updateOne(
      { userId: req.session.userId },
      { $set: { ...merged, userId: req.session.userId, updatedAt: new Date() } },
      { upsert: true },
    );
    res.json({ profile: publicProfile(merged) });
  } catch (err) {
    console.error('profile save failed', err.message);
    res.status(500).json({ error: 'Could not save profile.' });
  }
});

app.post('/api/receipts', requireAuth, upload.single('image'), async (req, res) => {
  try {
    if (!req.file) {
      res.status(400).json({ error: 'Image required.' });
      return;
    }
    const bucket = getReceiptBucket();
    const filename = req.file.originalname || 'receipt.jpg';
    const uploadStream = bucket.openUploadStream(filename, {
      contentType: req.file.mimetype || 'image/jpeg',
      metadata: { userId: req.session.userId },
    });
    await pipeline(Readable.from(req.file.buffer), uploadStream);
    const id = String(uploadStream.id);
    res.json({ id, uri: `/api/receipts/${id}` });
  } catch (err) {
    console.error('receipt upload failed', err.message);
    res.status(500).json({ error: 'Could not save receipt.' });
  }
});

app.get('/api/receipts/:id', requireAuth, async (req, res) => {
  try {
    const id = new ObjectId(req.params.id);
    const db = getDb();
    const file = await db.collection('receipts.files').findOne({ _id: id });
    if (!file || file.metadata?.userId !== req.session.userId) {
      res.status(404).json({ error: 'Could not open this image.' });
      return;
    }
    res.setHeader('Content-Type', file.contentType || 'image/jpeg');
    getReceiptBucket().openDownloadStream(id).pipe(res);
  } catch {
    res.status(404).json({ error: 'Could not open this image.' });
  }
});

app.put('/api/receipts/:id', requireAuth, upload.single('image'), async (req, res) => {
  try {
    if (!req.file) {
      res.status(400).json({ error: 'Image required.' });
      return;
    }
    const id = new ObjectId(req.params.id);
    const db = getDb();
    const file = await db.collection('receipts.files').findOne({ _id: id });
    if (!file || file.metadata?.userId !== req.session.userId) {
      res.status(404).json({ error: 'Could not open this image.' });
      return;
    }
    const bucket = getReceiptBucket();
    await bucket.delete(id);
    const uploadStream = bucket.openUploadStreamWithId(id, file.filename, {
      contentType: req.file.mimetype || 'image/jpeg',
      metadata: { userId: req.session.userId },
    });
    await pipeline(Readable.from(req.file.buffer), uploadStream);
    res.json({ id: String(id), uri: `/api/receipts/${id}` });
  } catch (err) {
    console.error('receipt replace failed', err.message);
    res.status(500).json({ error: 'Could not save the rotated image.' });
  }
});

app.post('/api/ocr', requireAuth, async (req, res) => {
  try {
    const rawLines = Array.isArray(req.body?.lines) ? req.body.lines : [];
    const mode = req.body?.mode || ReceiptCaptureMode.AUTO;
    const lines = rawLines.map((line) => ocrLine(line.text || '', line.confidence || 0.9, line.lineHeightPx || 0));
    const learning = await readLearning(req.session.userId);
    const draft = parseReceipt(ocrResult(lines), mode, learning.weights);
    res.json({ draft });
  } catch (err) {
    console.error('ocr parse failed', err.message);
    res.status(500).json({ error: 'Could not read receipt. Try again or enter manually.' });
  }
});

app.get('/api/learning', requireAuth, async (req, res) => {
  try {
    const data = await readLearning(req.session.userId);
    res.json(data);
  } catch (err) {
    console.error('learning load failed', err.message);
    res.status(500).json({ error: 'Could not load learning data.' });
  }
});

app.post('/api/learning', requireAuth, async (req, res) => {
  try {
    const data = await saveLearning(req.session.userId, req.body || {});
    res.json(data);
  } catch (err) {
    console.error('learning save failed', err.message);
    res.status(500).json({ error: 'Could not save learning data.' });
  }
});

async function start() {
  try {
    await connectDb();
  } catch (err) {
    console.error('MongoDB connection failed. Start Mongo locally or `docker compose up -d` in web/.');
    console.error(err.message);
    process.exit(1);
  }
  app.listen(PORT, '127.0.0.1', () => {
    console.log(`Mountain Money web demo: http://127.0.0.1:${PORT}`);
  });
}

start();
