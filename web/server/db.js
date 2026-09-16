import { MongoClient, GridFSBucket, ObjectId } from 'mongodb';

const uri = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017';
const dbName = process.env.MONGODB_DB || 'mountain_money';

let client;
let db;
let bucket;

export async function connectDb() {
  if (db) return db;
  client = new MongoClient(uri);
  await client.connect();
  db = client.db(dbName);
  await db.collection('users').createIndex({ login: 1 }, { unique: true });
  await db.collection('profiles').createIndex({ userId: 1 }, { unique: true });
  bucket = new GridFSBucket(db, { bucketName: 'receipts' });
  return db;
}

export function getDb() {
  if (!db) throw new Error('Database not connected');
  return db;
}

export function getReceiptBucket() {
  if (!bucket) throw new Error('Database not connected');
  return bucket;
}

export { ObjectId };
