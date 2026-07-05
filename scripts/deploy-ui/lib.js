'use strict';

function parseConfLines(lines) {
  const config = {};
  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed === '' || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq === -1) continue;
    const key = trimmed.slice(0, eq);
    const value = trimmed.slice(eq + 1);
    config[key] = value;
  }
  return config;
}

function updateConfLines(lines, updates) {
  const remainingKeys = new Set(Object.keys(updates));
  const result = lines.map((line) => {
    const trimmed = line.trim();
    if (trimmed === '' || trimmed.startsWith('#')) return line;
    const eq = trimmed.indexOf('=');
    if (eq === -1) return line;
    const key = trimmed.slice(0, eq);
    if (Object.prototype.hasOwnProperty.call(updates, key)) {
      remainingKeys.delete(key);
      return `${key}=${updates[key]}`;
    }
    return line;
  });
  for (const key of remainingKeys) {
    result.push(`${key}=${updates[key]}`);
  }
  return result;
}

function buildDeployArgs({ phone, watch, release }) {
  if (!phone && !watch) {
    throw new Error('Il faut cibler au moins un appareil (phone ou watch).');
  }
  const args = [];
  if (phone && !watch) args.push('--phone-only');
  if (watch && !phone) args.push('--watch-only');
  if (release) args.push('--release');
  return args;
}

function buildReleaseEnv({ release, keystorePassword, keyPassword }) {
  if (!release) return {};
  return {
    KEYSTORE_PASSWORD: keystorePassword,
    KEY_PASSWORD: keyPassword,
  };
}

const AWAITING_INPUT_RE = /^AWAITING_INPUT:([A-Z_]+)$/;

function parseAwaitingInputMarker(line) {
  const match = AWAITING_INPUT_RE.exec(line.trim());
  return match ? match[1] : null;
}

function splitCompleteLines(carry, chunk) {
  const combined = carry + chunk;
  const parts = combined.split('\n');
  const carryOut = parts.pop();
  return { lines: parts, carry: carryOut };
}

module.exports = {
  parseConfLines,
  updateConfLines,
  buildDeployArgs,
  buildReleaseEnv,
  parseAwaitingInputMarker,
  splitCompleteLines,
};
