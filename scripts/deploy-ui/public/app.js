const configForm = document.getElementById('config-form');
const configStatus = document.getElementById('config-status');

async function loadConfig() {
  const res = await fetch('/api/config');
  const config = await res.json();
  for (const field of configForm.elements) {
    if (field.name && Object.prototype.hasOwnProperty.call(config, field.name)) {
      field.value = config[field.name];
    }
  }
}

configForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const data = {};
  for (const field of configForm.elements) {
    if (field.name) data[field.name] = field.value;
  }
  const res = await fetch('/api/config', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  configStatus.textContent = res.ok ? 'Configuration enregistrée.' : 'Erreur lors de l’enregistrement.';
});

loadConfig();

const deployForm = document.getElementById('deploy-form');
const deployButton = document.getElementById('deploy-button');
const releaseToggle = document.getElementById('release-toggle');
const releaseFields = document.getElementById('release-fields');
const logPanel = document.getElementById('log-panel');
const awaitingInputBox = document.getElementById('awaiting-input-box');
const awaitingInputLabel = document.getElementById('awaiting-input-label');
const awaitingInputValue = document.getElementById('awaiting-input-value');
const awaitingInputSubmit = document.getElementById('awaiting-input-submit');

let currentRunId = null;
let currentEventSource = null;

releaseToggle.addEventListener('change', () => {
  releaseFields.hidden = !releaseToggle.checked;
});

function appendLog(line) {
  logPanel.textContent += `${line}\n`;
  logPanel.scrollTop = logPanel.scrollHeight;
}

function showAwaitingInput(device) {
  const labels = { PHONE_IP: 'IP du phone :', WATCH_IP: 'IP de la watch :' };
  awaitingInputLabel.textContent = labels[device] || `Valeur attendue (${device}) :`;
  awaitingInputValue.value = '';
  awaitingInputBox.hidden = false;
}

awaitingInputSubmit.addEventListener('click', async () => {
  await fetch('/api/deploy/input', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ runId: currentRunId, value: awaitingInputValue.value }),
  });
  awaitingInputBox.hidden = true;
});

deployForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  deployButton.disabled = true;
  logPanel.textContent = '';

  const payload = {
    phone: deployForm.elements.phone.checked,
    watch: deployForm.elements.watch.checked,
    release: deployForm.elements.release.checked,
    keystorePassword: deployForm.elements.keystorePassword.value,
    keyPassword: deployForm.elements.keyPassword.value,
  };

  const res = await fetch('/api/deploy', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const error = await res.json();
    appendLog(`Erreur : ${error.error}`);
    deployButton.disabled = false;
    return;
  }

  const { runId } = await res.json();
  currentRunId = runId;
  currentEventSource = new EventSource(`/api/deploy/stream?runId=${encodeURIComponent(runId)}`);

  currentEventSource.addEventListener('log', (e) => {
    const data = JSON.parse(e.data);
    appendLog(data.line);
  });

  currentEventSource.addEventListener('awaiting-input', (e) => {
    const data = JSON.parse(e.data);
    showAwaitingInput(data.device);
  });

  currentEventSource.addEventListener('done', (e) => {
    const data = JSON.parse(e.data);
    appendLog(data.code === 0 ? '✅ Déploiement terminé avec succès.' : `❌ Déploiement terminé en échec (code ${data.code}).`);
    currentEventSource.close();
    deployButton.disabled = false;
  });
});
