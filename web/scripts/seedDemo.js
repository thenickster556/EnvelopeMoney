import 'dotenv/config';
import bcrypt from 'bcryptjs';
import { connectDb, getDb } from '../server/db.js';
import { buildDemoProfile } from '../domain/demoSeed.js';
import { applyLaunchAndDisplay } from '../domain/profileEngine.js';

const login = String(process.argv[2] || 'alice-demo').trim().toLowerCase();
const password = String(process.argv[3] || 'secret1');

async function main() {
  await connectDb();
  const db = getDb();
  let user = await db.collection('users').findOne({ login });
  if (!user) {
    const passwordHash = await bcrypt.hash(password, 10);
    const inserted = await db.collection('users').insertOne({
      login,
      passwordHash,
      createdAt: new Date(),
    });
    user = { _id: inserted.insertedId, login };
    console.log(`Created user ${login}`);
  }
  const userId = String(user._id);
  const { profile } = applyLaunchAndDisplay(buildDemoProfile());
  await db.collection('profiles').updateOne(
    { userId },
    { $set: { ...profile, userId, updatedAt: new Date() } },
    { upsert: true },
  );
  const months = new Set(profile.envelopes.flatMap((e) => (e.transactions || []).map((t) => t.month)));
  console.log(`Seeded ${login}: ${profile.envelopes.length} ponds, ${months.size} months of activity`);
  process.exit(0);
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
