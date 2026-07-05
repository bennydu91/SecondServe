'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
  parseConfLines,
  updateConfLines,
  buildDeployArgs,
  buildReleaseEnv,
  parseAwaitingInputMarker,
  splitCompleteLines,
} = require('./lib.js');

test('parseConfLines ignores comments and blank lines', () => {
  const lines = ['# commentaire', '', 'VPS_HOST=1.2.3.4', 'VPS_USER=root'];
  assert.deepEqual(parseConfLines(lines), { VPS_HOST: '1.2.3.4', VPS_USER: 'root' });
});

test('parseConfLines returns an empty object for an empty file', () => {
  assert.deepEqual(parseConfLines([]), {});
});

test('updateConfLines replaces an existing key without touching others', () => {
  const lines = ['VPS_HOST=1.2.3.4', '# commentaire', 'WATCH_IP=10.0.0.1'];
  const result = updateConfLines(lines, { WATCH_IP: '10.0.0.99' });
  assert.deepEqual(result, ['VPS_HOST=1.2.3.4', '# commentaire', 'WATCH_IP=10.0.0.99']);
});

test('updateConfLines appends a missing key at the end', () => {
  const lines = ['VPS_HOST=1.2.3.4'];
  const result = updateConfLines(lines, { PHONE_IP: '10.0.0.5' });
  assert.deepEqual(result, ['VPS_HOST=1.2.3.4', 'PHONE_IP=10.0.0.5']);
});

test('updateConfLines handles multiple keys, some existing some missing', () => {
  const lines = ['VPS_HOST=1.2.3.4', 'WATCH_IP=10.0.0.1'];
  const result = updateConfLines(lines, { WATCH_IP: '10.0.0.2', PHONE_IP: '10.0.0.5' });
  assert.deepEqual(result, ['VPS_HOST=1.2.3.4', 'WATCH_IP=10.0.0.2', 'PHONE_IP=10.0.0.5']);
});

test('buildDeployArgs returns no flag when both phone and watch are targeted (staging)', () => {
  assert.deepEqual(buildDeployArgs({ phone: true, watch: true, release: false }), []);
});

test('buildDeployArgs returns --phone-only when only phone is targeted', () => {
  assert.deepEqual(buildDeployArgs({ phone: true, watch: false, release: false }), ['--phone-only']);
});

test('buildDeployArgs returns --watch-only when only watch is targeted', () => {
  assert.deepEqual(buildDeployArgs({ phone: false, watch: true, release: false }), ['--watch-only']);
});

test('buildDeployArgs adds --release when release is true', () => {
  assert.deepEqual(buildDeployArgs({ phone: true, watch: true, release: true }), ['--release']);
});

test('buildDeployArgs throws when neither phone nor watch is targeted', () => {
  assert.throws(() => buildDeployArgs({ phone: false, watch: false, release: false }));
});

test('buildReleaseEnv returns an empty object when release is false', () => {
  assert.deepEqual(buildReleaseEnv({ release: false, keystorePassword: 'x', keyPassword: 'y' }), {});
});

test('buildReleaseEnv returns keystore env vars when release is true', () => {
  assert.deepEqual(
    buildReleaseEnv({ release: true, keystorePassword: 'a', keyPassword: 'b' }),
    { KEYSTORE_PASSWORD: 'a', KEY_PASSWORD: 'b' }
  );
});

test('parseAwaitingInputMarker matches a valid marker line', () => {
  assert.equal(parseAwaitingInputMarker('AWAITING_INPUT:PHONE_IP'), 'PHONE_IP');
});

test('parseAwaitingInputMarker returns null for a normal log line', () => {
  assert.equal(parseAwaitingInputMarker('📱 Installation sur le phone (ABCD)…'), null);
});

test('parseAwaitingInputMarker tolerates surrounding whitespace', () => {
  assert.equal(parseAwaitingInputMarker('  AWAITING_INPUT:WATCH_IP  '), 'WATCH_IP');
});

test('splitCompleteLines splits a chunk containing two full lines', () => {
  assert.deepEqual(splitCompleteLines('', 'foo\nbar\n'), { lines: ['foo', 'bar'], carry: '' });
});

test('splitCompleteLines keeps an incomplete trailing line as carry', () => {
  assert.deepEqual(splitCompleteLines('', 'foo\nbar'), { lines: ['foo'], carry: 'bar' });
});

test('splitCompleteLines completes a carried partial line with the next chunk', () => {
  assert.deepEqual(splitCompleteLines('bar', '\nbaz\n'), { lines: ['bar', 'baz'], carry: '' });
});

test('splitCompleteLines handles an empty chunk without losing the carry', () => {
  assert.deepEqual(splitCompleteLines('abc', ''), { lines: [], carry: 'abc' });
});
