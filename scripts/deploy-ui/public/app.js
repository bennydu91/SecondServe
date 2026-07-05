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
