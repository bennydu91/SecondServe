'use strict';

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {
  parseConfLines,
  updateConfLines,
  buildDeployArgs,
  buildReleaseEnv,
  parseAwaitingInputMarker,
  splitCompleteLines,
} = require('./lib.js');
const { spawn } = require('node:child_process');
const crypto = require('node:crypto');

const SCRIPTS_DIR = path.join(__dirname, '..');
const CONF_FILE = path.join(SCRIPTS_DIR, 'deploy-devices.conf');
const CONF_EXAMPLE_FILE = path.join(SCRIPTS_DIR, 'deploy-devices.conf.example');
const PUBLIC_DIR = path.join(__dirname, 'public');
const PORT = 4400;
const DEPLOY_SCRIPT_PATH = path.join(SCRIPTS_DIR, 'deploy-devices.sh');

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

let currentRun = null; // { id, child, sseClients: Set, awaitingInput: string|null, stdoutCarry, stderrCarry }

function broadcast(run, event, data) {
  run.history.push({ event, data });
  const payload = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
  for (const client of run.sseClients) {
    client.write(payload);
  }
}

function handleChildOutput(run, chunk, streamName) {
  const carryKey = streamName === 'stdout' ? 'stdoutCarry' : 'stderrCarry';
  const { lines, carry } = splitCompleteLines(run[carryKey], chunk.toString('utf8'));
  run[carryKey] = carry;
  for (const line of lines) {
    if (streamName === 'stderr') {
      const marker = parseAwaitingInputMarker(line);
      if (marker) {
        run.awaitingInput = marker;
        broadcast(run, 'awaiting-input', { device: marker });
        continue;
      }
    }
    broadcast(run, 'log', { line, stream: streamName });
  }
}

async function handlePostDeploy(req, res) {
  if (currentRun && !currentRun.done) {
    res.writeHead(409, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Un déploiement est déjà en cours.' }));
    return;
  }
  let body;
  try {
    body = await readJsonBody(req);
  } catch (err) {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'JSON invalide' }));
    return;
  }
  let args;
  try {
    args = buildDeployArgs({ phone: !!body.phone, watch: !!body.watch, release: !!body.release });
  } catch (err) {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: err.message }));
    return;
  }
  const releaseEnv = buildReleaseEnv({
    release: !!body.release,
    keystorePassword: body.keystorePassword || '',
    keyPassword: body.keyPassword || '',
  });

  const child = spawn(DEPLOY_SCRIPT_PATH, args, {
    cwd: SCRIPTS_DIR,
    env: { ...process.env, ...releaseEnv },
    stdio: ['pipe', 'pipe', 'pipe'],
  });

  const run = {
    id: crypto.randomUUID(),
    child,
    sseClients: new Set(),
    awaitingInput: null,
    stdoutCarry: '',
    stderrCarry: '',
    history: [],
    done: false,
  };
  currentRun = run;

  child.stdout.on('data', (chunk) => handleChildOutput(run, chunk, 'stdout'));
  child.stderr.on('data', (chunk) => handleChildOutput(run, chunk, 'stderr'));
  child.on('close', (code) => {
    if (run.done) return;
    run.done = true;
    broadcast(run, 'done', { code });
    for (const client of run.sseClients) {
      client.end();
    }
  });
  child.on('error', (err) => {
    if (run.done) return;
    run.done = true;
    broadcast(run, 'log', { line: `❌ Erreur au lancement du script : ${err.message}`, stream: 'stderr' });
    broadcast(run, 'done', { code: -1 });
    for (const client of run.sseClients) {
      client.end();
    }
  });

  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ runId: run.id }));
}

function handleGetDeployStream(req, res, url) {
  const runId = url.searchParams.get('runId');
  if (!currentRun || currentRun.id !== runId) {
    res.writeHead(404);
    res.end('Aucun déploiement en cours avec cet identifiant.');
    return;
  }
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
  });
  currentRun.sseClients.add(res);
  for (const { event, data } of currentRun.history) {
    res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
  }
  req.on('close', () => {
    if (currentRun) currentRun.sseClients.delete(res);
  });
}

async function handlePostDeployInput(req, res) {
  let body;
  try {
    body = await readJsonBody(req);
  } catch (err) {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'JSON invalide' }));
    return;
  }
  if (!currentRun || currentRun.id !== body.runId || !currentRun.awaitingInput) {
    res.writeHead(409, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Aucune saisie attendue pour le moment.' }));
    return;
  }
  currentRun.child.stdin.write(`${body.value || ''}\n`);
  currentRun.awaitingInput = null;
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ ok: true }));
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
  if (req.method === 'POST' && url.pathname === '/api/deploy') {
    handlePostDeploy(req, res);
    return;
  }
  if (req.method === 'GET' && url.pathname === '/api/deploy/stream') {
    handleGetDeployStream(req, res, url);
    return;
  }
  if (req.method === 'POST' && url.pathname === '/api/deploy/input') {
    handlePostDeployInput(req, res);
    return;
  }

  res.writeHead(404);
  res.end('Not found');
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Deploy UI disponible sur http://127.0.0.1:${PORT}`);
});
