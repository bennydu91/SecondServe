'use strict';

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { parseConfLines, updateConfLines } = require('./lib.js');

const SCRIPTS_DIR = path.join(__dirname, '..');
const CONF_FILE = path.join(SCRIPTS_DIR, 'deploy-devices.conf');
const CONF_EXAMPLE_FILE = path.join(SCRIPTS_DIR, 'deploy-devices.conf.example');
const PUBLIC_DIR = path.join(__dirname, 'public');
const PORT = 4400;

const CONFIG_FIELDS = ['VPS_HOST', 'VPS_USER', 'VPS_SSH_PORT', 'VPS_REPO_PATH', 'PHONE_IP', 'WATCH_IP'];

const STATIC_FILES = {
  '/': { file: 'index.html', type: 'text/html; charset=utf-8' },
  '/app.js': { file: 'app.js', type: 'application/javascript; charset=utf-8' },
  '/style.css': { file: 'style.css', type: 'text/css; charset=utf-8' },
};

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (chunk) => { data += chunk; });
    req.on('end', () => {
      if (data === '') return resolve({});
      try {
        resolve(JSON.parse(data));
      } catch (err) {
        reject(err);
      }
    });
    req.on('error', reject);
  });
}

function readConfLines() {
  if (fs.existsSync(CONF_FILE)) {
    return fs.readFileSync(CONF_FILE, 'utf8').split('\n');
  }
  if (fs.existsSync(CONF_EXAMPLE_FILE)) {
    return fs.readFileSync(CONF_EXAMPLE_FILE, 'utf8').split('\n');
  }
  return [];
}

function handleGetConfig(req, res) {
  const lines = readConfLines();
  const parsed = parseConfLines(lines);
  const result = {};
  for (const field of CONFIG_FIELDS) {
    result[field] = parsed[field] || '';
  }
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(result));
}

async function handlePostConfig(req, res) {
  let body;
  try {
    body = await readJsonBody(req);
  } catch (err) {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'JSON invalide' }));
    return;
  }
  const updates = {};
  for (const field of CONFIG_FIELDS) {
    if (Object.prototype.hasOwnProperty.call(body, field)) {
      updates[field] = body[field];
    }
  }
  const lines = readConfLines();
  const updatedLines = updateConfLines(lines, updates);
  fs.writeFileSync(CONF_FILE, updatedLines.join('\n'));
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ ok: true }));
}

function serveStaticFile(pathname, res) {
  const entry = STATIC_FILES[pathname];
  if (!entry) {
    res.writeHead(404);
    res.end('Not found');
    return;
  }
  const filePath = path.join(PUBLIC_DIR, entry.file);
  fs.readFile(filePath, (err, content) => {
    if (err) {
      res.writeHead(500);
      res.end('Erreur de lecture du fichier');
      return;
    }
    res.writeHead(200, { 'Content-Type': entry.type });
    res.end(content);
  });
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);

  if (req.method === 'GET' && url.pathname === '/api/config') {
    handleGetConfig(req, res);
    return;
  }
  if (req.method === 'POST' && url.pathname === '/api/config') {
    handlePostConfig(req, res);
    return;
  }
  if (req.method === 'GET' && Object.prototype.hasOwnProperty.call(STATIC_FILES, url.pathname)) {
    serveStaticFile(url.pathname, res);
    return;
  }

  res.writeHead(404);
  res.end('Not found');
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Deploy UI disponible sur http://127.0.0.1:${PORT}`);
});
