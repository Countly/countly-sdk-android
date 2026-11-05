#!/usr/bin/env node
/**
 * The CI side of the Device Farm, run inside the farm workflow on a GitHub runner.
 *
 *   node client.mjs oidc [sdk] [phone]          join the farm's queue with this job's GitHub OIDC token;
 *                                               exports FARM_TOKEN and FARM_ID for the steps after it
 *   node client.mjs open                        wait for the phone, then tunnel adb to it in the background
 *   node client.mjs tunnel                      the tunnel itself: 127.0.0.1:5037 here is the farm's adb server
 *   node client.mjs ios <artifact> <runner> [junit]
 *                                               wait for the iPhone, hand it the test package uploaded as
 *                                               <artifact>, follow the results, and write them as JUnit
 *   node client.mjs summary <dir>               Markdown totals of the JUnit files under <dir>
 *   node client.mjs release <status>            give the phone back; <status> is the job's status
 *
 * Needs FARM_URL (the farm's public CI path) and, except for `oidc` and `summary`, FARM_TOKEN
 * (the run's one-time token). `oidc` needs the job's `permissions: id-token: write`.
 * No dependencies; Node 18 or newer.
 */
import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import http from 'node:http';
import https from 'node:https';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const URL_BASE = (process.env.FARM_URL || '').replace(/\/+$/, '');
const TOKEN = process.env.FARM_TOKEN || '';
const AUDIENCE = process.env.FARM_OIDC_AUDIENCE || 'countly-device-farm';
const AGENT_QUIET_MS = 3 * 60e3;
const OUTAGE_MS = Number(process.env.FARM_OUTAGE_MINUTES || 10) * 60e3;
const RETRY_MS = Number(process.env.FARM_RETRY_MS || 5000);

/** Whether an HTTP status means the farm is briefly unavailable (a restart or a proxy blip). */
function transient(status) {
  return status === 502 || status === 503 || status === 504;
}
const ADB_PORT = Number(process.env.FARM_ADB_PORT || 5037);
const WAIT_MINUTES = Number(process.env.FARM_WAIT_MINUTES || 120);
const POLL_MS = 15000;
const PROTOCOL = 'cc-farm-adb';
const PID_FILE = path.join(process.env.RUNNER_TEMP || '.', 'farm-tunnel.pid');

/** Prints a line for the workflow log. */
function log(line) {
  process.stdout.write(`${line}\n`);
}

/** Stops with a message the workflow log shows as an error. */
function fail(message) {
  process.stdout.write(`::error::${message}\n`);
  process.exit(1);
}

/** Resolves after `ms` milliseconds. */
function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

/** POSTs JSON to a farm CI endpoint with the run's token; resolves `{ status, body }`. */
async function post(name, body) {
  const r = await fetch(`${URL_BASE}/${name}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body || {}),
    signal: AbortSignal.timeout(30000),
  });
  let data = null;
  try { data = await r.json(); } catch { data = null; }
  return { status: r.status, body: data };
}

/** Appends `NAME=value` lines to the job's environment for the steps that follow. */
function exportEnv(vars) {
  if (!process.env.GITHUB_ENV) return;
  fs.appendFileSync(process.env.GITHUB_ENV, Object.entries(vars).map(([k, v]) => `${k}=${v}\n`).join(''));
}

/** Resolves once something accepts connections on the local adb port, or rejects after `ms`. */
async function listening(ms) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    const ok = await new Promise((resolve) => {
      const s = net.connect(ADB_PORT, '127.0.0.1');
      s.once('connect', () => { s.destroy(); resolve(true); });
      s.once('error', () => resolve(false));
    });
    if (ok) return;
    await sleep(200);
  }
  throw new Error(`nothing is listening on 127.0.0.1:${ADB_PORT}`);
}

/** GETs a farm CI endpoint with the run's token; resolves `{ status, body }`. */
async function get(name) {
  const r = await fetch(`${URL_BASE}/${name}`, { headers: { Authorization: `Bearer ${TOKEN}` }, signal: AbortSignal.timeout(60000) });
  let data = null;
  try { data = await r.json(); } catch { data = null; }
  return { status: r.status, body: data };
}

/** Asks GitHub for this job's OIDC token for the farm's audience. */
async function githubOidcToken() {
  const url = process.env.ACTIONS_ID_TOKEN_REQUEST_URL;
  const bearer = process.env.ACTIONS_ID_TOKEN_REQUEST_TOKEN;
  if (!url || !bearer) fail('this job has no OIDC token; give it `permissions: id-token: write`');
  const r = await fetch(`${url}&audience=${encodeURIComponent(AUDIENCE)}`, { headers: { Authorization: `Bearer ${bearer}` }, signal: AbortSignal.timeout(30000) });
  const data = await r.json().catch(() => null);
  if (!r.ok || !data || !data.value) fail(`GitHub gave no OIDC token (${r.status})`);
  return data.value;
}

/** Joins the farm's queue as this workflow run and passes the run token on to later steps, masked. */
async function oidc(sdk, phone) {
  const jwt = await githubOidcToken();
  const r = await fetch(`${URL_BASE}/oidc`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${jwt}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ sdk: sdk || '', phone: phone || '' }),
    signal: AbortSignal.timeout(30000),
  });
  const data = await r.json().catch(() => null);
  if (r.status !== 201 || !data || !data.token) fail(`the farm refused this run: ${(data && data.error) || r.status}`);
  process.stdout.write(`::add-mask::${data.token}\n`);
  exportEnv({ FARM_TOKEN: data.token, FARM_ID: data.farm_id });
  log(`queued on the farm as ${data.farm_id} for ${data.phone}`);
}

/** Waits until the farm gives this run its phone; resolves the claim's answer. */
async function waitForPhone() {
  const runUrl = process.env.GITHUB_RUN_ID ? `${process.env.GITHUB_SERVER_URL}/${process.env.GITHUB_REPOSITORY}/actions/runs/${process.env.GITHUB_RUN_ID}` : '';
  const end = Date.now() + WAIT_MINUTES * 60e3;
  let last = '';
  while (Date.now() < end) {
    let r;
    try {
      r = await post('claim', { run_id: Number(process.env.GITHUB_RUN_ID) || null, run_url: runUrl });
    } catch (e) {
      log(`the farm did not answer (${e.message}); asking again`);
      await sleep(POLL_MS);
      continue;
    }
    if (transient(r.status)) {
      log(`the farm answered ${r.status}; asking again`);
      await sleep(RETRY_MS);
      continue;
    }
    if (r.status !== 200) fail(`the farm refused the run: ${(r.body && r.body.error) || r.status}`);
    if (r.body.ready) {
      log(`took ${r.body.phone} (${r.body.serial}) until ${r.body.expires_at}`);
      return r.body;
    }
    if (r.body.waiting_for !== last) log(`waiting: ${r.body.waiting_for}`);
    last = r.body.waiting_for;
    await sleep(POLL_MS);
  }
  return fail(`no phone within ${WAIT_MINUTES} minutes (last: ${last})`);
}

/** Waits for the farm to give this run its phone, then starts the tunnel and exports ANDROID_SERIAL. */
async function open() {
  const claim = await waitForPhone();
  exportEnv({ ANDROID_SERIAL: claim.serial });
  const out = fs.openSync(path.join(process.env.RUNNER_TEMP || '.', 'farm-tunnel.log'), 'a');
  const child = spawn(process.execPath, [fileURLToPath(import.meta.url), 'tunnel'], { detached: true, stdio: ['ignore', out, out], env: process.env });
  child.unref();
  fs.writeFileSync(PID_FILE, String(child.pid));
  await listening(15000);
  log(`adb on 127.0.0.1:${ADB_PORT} now reaches the farm`);
}

/** One line of the workflow log for a result event from the phone. */
function eventLine(e) {
  const name = `${e.class}/${e.method}`;
  if (e.type === 'case-finished') return `${e.status === 'failed' ? 'FAIL' : e.status === 'skipped' ? 'SKIP' : 'PASS'} ${name} (${Number(e.duration || 0).toFixed(3)}s)`;
  if (e.type === 'case-failed') return `::error file=${e.file || ''},line=${e.line || 0}::${name}: ${e.message || 'failed'}`;
  if (e.type === 'case-crashed') return `::error::${name} crashed the test runner; the rest of the suite continues without it`;
  if (e.type === 'run-failed') return `::error::the tests did not run to the end: ${e.error || ''}`;
  return null;
}

/** Escapes text for an XML attribute or element. */
function xml(s) {
  return String(s === undefined || s === null ? '' : s).replace(/[<>&"']/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;', "'": '&apos;' }[c]));
}

/** The result events as one JUnit test suite, which `summary` and test reporters read. */
function junit(events, suite) {
  const cases = new Map();
  const at = (e) => {
    const key = `${e.class}/${e.method}`;
    if (!cases.has(key)) cases.set(key, { class: e.class, method: e.method, status: 'passed', duration: 0, failures: [] });
    return cases.get(key);
  };
  for (const e of events) {
    if (e.type === 'case-finished') Object.assign(at(e), { status: e.status, duration: Number(e.duration) || 0 });
    else if (e.type === 'case-failed') at(e).failures.push(`${e.message || 'failed'} (${e.file || '?'}:${e.line || 0})`);
    else if (e.type === 'case-crashed') Object.assign(at(e), { status: 'crashed' });
  }
  const list = [...cases.values()];
  const failures = list.filter((c) => c.status === 'failed').length;
  const errors = list.filter((c) => c.status === 'crashed').length;
  const skipped = list.filter((c) => c.status === 'skipped').length;
  const time = list.reduce((t, c) => t + c.duration, 0);
  const body = list.map((c) => {
    const open = `    <testcase name="${xml(c.method)}" classname="${xml(c.class)}" time="${c.duration.toFixed(3)}"`;
    if (c.status === 'failed') return `${open}>\n      <failure message="${xml(c.failures[0] || 'failed')}">${xml(c.failures.join('\n'))}</failure>\n    </testcase>`;
    if (c.status === 'crashed') return `${open}>\n      <error message="the test crashed the test runner">${xml(c.failures.join('\n'))}</error>\n    </testcase>`;
    if (c.status === 'skipped') return `${open}>\n      <skipped/>\n    </testcase>`;
    return `${open}/>`;
  });
  return `<?xml version="1.0" encoding="UTF-8"?>\n<testsuites>\n  <testsuite name="${xml(suite)}" tests="${list.length}" failures="${failures}" errors="${errors}" skipped="${skipped}" time="${time.toFixed(3)}">\n${body.join('\n')}\n  </testsuite>\n</testsuites>\n`;
}

/**
 * Runs the tests on the iPhone: waits for the phone, hands the farm the test package uploaded as
 * this run's artifact `artifact`, follows the results the phone streams, and writes them to
 * `junitFile` as JUnit. Fails the step when a test failed or crashed, or the suite did not finish.
 */
async function ios(artifact, runner, junitFile) {
  if (!artifact || !runner) fail('usage: client.mjs ios <artifact> <runner-bundle-id> [junit.xml]');
  await waitForPhone();
  const startEnd = Date.now() + OUTAGE_MS;
  let start;
  for (;;) {
    try {
      start = await post('ios/start', { artifact, runner });
    } catch (e) {
      start = { status: 0, body: { error: e.message } };
    }
    if ((start.status === 0 || transient(start.status)) && Date.now() < startEnd) {
      log(`the farm answered ${start.status || start.body.error}; handing the tests over again`);
      await sleep(RETRY_MS);
      continue;
    }
    break;
  }
  const already = start.status === 409 && /already handed over/.test((start.body && start.body.error) || '');
  if (start.status !== 200 && !already) fail(`the farm did not take the tests: ${(start.body && start.body.error) || start.status}`);
  log(already ? `the farm already has ${artifact}; following the results` : `handed the phone ${artifact} (${Math.round(start.body.size / 1024)} KB); following the results`);
  const events = [];
  const end = Date.now() + WAIT_MINUTES * 60e3;
  let after = 0;
  let warned = 0;
  let result = null;
  let outageSince = 0;
  while (Date.now() < end) {
    let r;
    try {
      r = await get(`ios/events?after=${after}&wait=25000`);
    } catch (e) {
      r = { status: 0, body: { error: e.message } };
    }
    if (r.status === 0 || transient(r.status)) {
      outageSince = outageSince || Date.now();
      if (Date.now() - outageSince > OUTAGE_MS) fail(`the farm has not answered for ${Math.round(OUTAGE_MS / 60e3)} minutes (${r.status || r.body.error})`);
      log(`the farm answered ${r.status || r.body.error}; following again from event ${after}`);
      await sleep(RETRY_MS);
      continue;
    }
    outageSince = 0;
    if (r.status !== 200) fail(`the farm stopped answering about the run: ${(r.body && r.body.error) || r.status}`);
    for (const e of r.body.events) {
      events.push(e);
      const line = eventLine(e);
      if (line) log(line);
    }
    after = r.body.next;
    if (r.body.done || r.body.job === 'cancelled') { result = r.body; break; }
    const seen = r.body.agent_seen_at ? Date.parse(r.body.agent_seen_at) : 0;
    if (r.body.job === 'pending' && Date.now() - seen > AGENT_QUIET_MS && Date.now() - warned > AGENT_QUIET_MS) {
      log(`::warning::the phone's agent has not checked in since ${r.body.agent_seen_at || 'it was set up'}; is the Farm Runner VPN on?`);
      warned = Date.now();
    }
  }
  if (junitFile) {
    fs.mkdirSync(path.dirname(junitFile), { recursive: true });
    fs.writeFileSync(junitFile, junit(events, runner.replace(/\.xctrunner$/, '')));
  }
  if (!result) fail(`the tests did not finish within ${WAIT_MINUTES} minutes`);
  if (result.job === 'cancelled') fail('the run was stopped on the farm');
  if (result.error) fail(`the tests did not run to the end: ${result.error}`);
  const s = result.summary || {};
  log(`${s.passed || 0} passed, ${s.failed || 0} failed, ${s.skipped || 0} skipped${s.crashed ? `, ${s.crashed} crashed the runner` : ''}`);
  if (s.failed || s.crashed) process.exit(1);
}

/** Carries one local adb connection to the farm over an HTTP upgrade. */
function carry(local) {
  local.pause();
  const u = new URL(`${URL_BASE}/adb`);
  const secure = u.protocol === 'https:';
  const req = (secure ? https : http).request({
    hostname: u.hostname,
    port: u.port || (secure ? 443 : 80),
    path: u.pathname,
    method: 'GET',
    headers: { Connection: 'Upgrade', Upgrade: PROTOCOL, Authorization: `Bearer ${TOKEN}` },
  });
  req.on('upgrade', (res, remote, head) => {
    remote.setNoDelay(true);
    remote.setKeepAlive(true, 30000);
    if (head && head.length) local.write(head);
    remote.pipe(local);
    local.pipe(remote);
    local.resume();
    remote.on('error', () => local.destroy());
    local.on('error', () => remote.destroy());
    remote.on('close', () => local.destroy());
    local.on('close', () => remote.destroy());
  });
  req.on('response', (res) => {
    log(`the farm refused a tunnel connection (${res.statusCode})`);
    local.destroy();
  });
  req.on('error', (e) => {
    log(`tunnel connection failed: ${e.message}`);
    local.destroy();
  });
  req.end();
}

/** Serves 127.0.0.1:ADB_PORT, each connection carried to the farm's adb server. */
function tunnel() {
  net.createServer({ noDelay: true }, carry).listen(ADB_PORT, '127.0.0.1', () => log(`tunnel listening on 127.0.0.1:${ADB_PORT}`));
}

/** Every `.xml` file under `dir`, recursively; none when it does not exist. */
function xmlFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) return xmlFiles(p);
    return e.name.endsWith('.xml') ? [p] : [];
  });
}

/** Markdown totals of every JUnit XML file under `dir`, for the job summary. */
function summary(dir) {
  const files = xmlFiles(dir || '.');
  const t = { tests: 0, failures: 0, errors: 0, skipped: 0 };
  const failed = [];
  for (const f of files) {
    const xml = fs.readFileSync(f, 'utf8');
    for (const m of xml.matchAll(/<testsuite\b([^>]*)>/g)) {
      for (const k of Object.keys(t)) {
        const v = new RegExp(`\\b${k}="(\\d+)"`).exec(m[1]);
        if (v) t[k] += Number(v[1]);
      }
    }
    for (const m of xml.matchAll(/<testcase\b([^>]*?)(\/)?>/g)) {
      if (m[2]) continue;
      const close = xml.indexOf('</testcase>', m.index);
      if (!/<(failure|error)\b/.test(xml.slice(m.index + m[0].length, close < 0 ? undefined : close))) continue;
      const name = /\bname="([^"]*)"/.exec(m[1]);
      const cls = /\bclassname="([^"]*)"/.exec(m[1]);
      failed.push(`${cls ? cls[1] : ''}.${name ? name[1] : ''}`);
    }
  }
  const lines = ['### Device Farm', ''];
  if (!files.length) lines.push('No test results were written.');
  else {
    lines.push(`| Tests | Failed | Errors | Skipped |`, '| --- | --- | --- | --- |', `| ${t.tests} | ${t.failures} | ${t.errors} | ${t.skipped} |`);
    if (failed.length) lines.push('', '<details><summary>Failed tests</summary>', '', ...failed.slice(0, 200).map((n) => `- \`${n}\``), '', '</details>');
  }
  log(lines.join('\n'));
}

/** Stops the background tunnel that `open` started, if it is still running. */
function stopTunnel() {
  try {
    process.kill(Number(fs.readFileSync(PID_FILE, 'utf8')));
  } catch { /* never started, or already gone */ }
  fs.rmSync(PID_FILE, { force: true });
}

/** Gives the phone back and stops the tunnel; never fails the job, since the farm also frees phones on its own. */
async function release(status) {
  stopTunnel();
  try {
    const r = await post('release', { status: status || '' });
    log(r.status === 200 ? 'gave the phone back' : `the farm answered ${r.status}: ${(r.body && r.body.error) || ''}`);
  } catch (e) {
    log(`could not reach the farm (${e.message}); the phone goes back when the run ends`);
  }
}

const [cmd, ...args] = process.argv.slice(2);
if (cmd === 'summary') summary(args[0]);
else if (cmd === 'oidc') {
  if (!URL_BASE) fail('FARM_URL is required');
  await oidc(args[0], args[1]);
} else {
  if (!URL_BASE || !TOKEN) fail('FARM_URL and FARM_TOKEN are required');
  if (cmd === 'open') await open();
  else if (cmd === 'tunnel') tunnel();
  else if (cmd === 'ios') await ios(args[0], args[1], args[2]);
  else if (cmd === 'release') await release(args[0]);
  else fail('usage: client.mjs oidc [sdk] [phone] | open | tunnel | ios <artifact> <runner> [junit] | summary <dir> | release <status>');
}
