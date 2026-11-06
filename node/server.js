const express = require('express');
const fs = require('node:fs');
const path = require('node:path');
const app = express();
const file = '/data/entries.jsonl';
fs.mkdirSync('/data', { recursive: true });
app.use(express.urlencoded({ extended: false, limit: '16kb' }));
app.use(express.static(path.join(__dirname, 'public')));
app.get('/api/entries', (req, res, next) => {
  try {
    const rows = fs.existsSync(file)
      ? fs.readFileSync(file, 'utf8').split('\n').filter(Boolean).map(JSON.parse)
      : [];
    res.set('Cache-Control', 'no-store').json(rows.slice(-100).reverse()
      .map(({ comment, createdAt }) => ({ comment, createdAt })));
  } catch (error) { next(error); }
});
app.post('/api/entries', (req, res, next) => {
  if (!req.is('application/x-www-form-urlencoded'))
    return res.status(415).json({ error: 'Use a URL-encoded form.' });
  const email = typeof req.body.email === 'string' ? req.body.email.trim() : '';
  const comment = typeof req.body.comment === 'string' ? req.body.comment.trim() : '';
  if (email.length > 254 || !/^[\x21-\x7e]+$/.test(email) ||
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ||
      !comment || Buffer.byteLength(comment, 'utf8') > 2000)
    return res.status(400).json({
      error: 'Use a valid email and a comment of 1-2000 UTF-8 bytes.'
    });
  try {
    fs.appendFileSync(file, JSON.stringify({
      email, comment, createdAt: new Date().toISOString()
    }) + '\n');
    res.status(201).json({ ok: true });
  } catch (error) { next(error); }
});
app.use('/api/entries', (req, res) => {
  res.status(405).json({ error: 'Method not allowed.' });
});
app.use((error, req, res, next) => {
  console.error(error.message);
  const status = error.status === 413 ? 413 : error.status === 400 ? 400 : 500;
  res.status(status).json({ error: status === 413 ? 'Request too large.' :
    status === 400 ? 'Invalid form.' : 'Storage unavailable.' });
});
app.listen(3000, '0.0.0.0', () => console.log('Guestbook on port 3000'));
