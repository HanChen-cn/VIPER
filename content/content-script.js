let currentPanel = null;

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'ping') {
    sendResponse({ pong: true });
    return false;
  }
  if (message.type === 'pinPlay') {
    try {
      handlePinPlay(message);
      sendResponse({ success: true });
    } catch (err) {
      console.error('[VIPER] pinPlay error:', err);
      sendResponse({ success: false, error: err.message });
    }
  }
  return false;
});

function handlePinPlay({ url, name, episode }) {
  if (currentPanel) {
    currentPanel.update(url, name, episode);
  } else {
    currentPanel = createFloatingPanel(url, name, episode);
  }
}

function createFloatingPanel(url, name, episode) {
  const root = document.createElement('div');
  root.id = 'viper-pin-root';
  document.body.appendChild(root);

  const shadow = root.attachShadow({ mode: 'closed' });
  const panel = new FloatingPanel(shadow, url, name, episode, () => {
    root.remove();
    currentPanel = null;
  });

  return panel;
}
