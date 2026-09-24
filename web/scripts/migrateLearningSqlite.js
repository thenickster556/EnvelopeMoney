/**
 * One-time copy of web/data/learning/*.db into the Mongo `learning` collection.
 * Not used at server startup. Leaves the .db files on disk.
 *
 *   node scripts/migrateLearningSqlite.js
 */
import 'dotenv/config';
import initSqlJs from 'sql.js';
import { createRequire } from 'node:module';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { connectDb, getDb } from '../server/db.js';
import { createSchema, loadComments, loadWeights } from '../domain/learningDb.js';

const require = createRequire(import.meta.url);
const wasmPath = join(dirname(require.resolve('sql.js')), 'sql-wasm.wasm');
const learningDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'data', 'learning');

async function main() {
  if (!existsSync(learningDir)) {
    console.log(`No learning folder at ${learningDir}. Nothing to migrate.`);
    return;
  }
  const files = readdirSync(learningDir).filter((name) => name.endsWith('.db'));
  if (!files.length) {
    console.log('No .db files to migrate.');
    return;
  }
  const SQL = await initSqlJs({ locateFile: () => wasmPath });
  await connectDb();
  const collection = getDb().collection('learning');
  let copied = 0;
  for (const name of files) {
    const userId = name.slice(0, -3);
    const sqlite = new SQL.Database(readFileSync(join(learningDir, name)));
    try {
      createSchema(sqlite);
      const comments = loadComments(sqlite);
      const weights = loadWeights(sqlite);
      await collection.updateOne(
        { userId },
        { $set: { comments, weights, updatedAt: new Date() }, $setOnInsert: { userId } },
        { upsert: true },
      );
      copied += 1;
      console.log(`Copied ${name} (${comments.length} comments)`);
    } finally {
      sqlite.close();
    }
  }
  console.log(`Migrated ${copied} learning file(s). The .db files were left in place.`);
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
