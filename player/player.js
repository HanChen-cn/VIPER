const playerFrame = document.getElementById('playerFrame');
const toggleBtn = document.getElementById('toggleBtn');
const sourceDropdown = document.getElementById('sourceDropdown');
const closeDropdown = document.getElementById('closeDropdown');
const showInfo = document.getElementById('showInfo');
const sourceList = document.getElementById('sourceList');
const topTitle = document.getElementById('topTitle');
const nextEpisodeBtn = document.getElementById('nextEpisodeBtn');

let currentData = null;

chrome.storage.session.get('playerData', (result) => {
  if (!result.playerData) {
    document.body.innerHTML = '<div style="color:#fff;display:flex;align-items:center;justify-content:center;height:100%;font-size:16px;">无播放数据，请从扩展搜索页打开</div>';
    return;
  }

  currentData = result.playerData;
  const { url, name, episode } = currentData;

  topTitle.textContent = `${name} - ${episode}`;
  showInfo.textContent = `${name} · ${episode}`;
  document.title = `${name} - ${episode}`;

  buildSourceList(url);

  playerFrame.src = url;

  playerFrame.addEventListener('load', () => {
    loadAltSources();
    loadNextEpisode();
  }, { once: true });
});

function loadAltSources() {
  if (!currentData || !currentData.name) return;

  const loadingEl = sourceList.querySelector('.source-loading');
  if (loadingEl) loadingEl.classList.remove('hidden');

  chrome.runtime.sendMessage({
    type: 'getAltSources',
    showName: currentData.name,
    episode: currentData.episode,
    currentUrl: currentData.url
  }, (res) => {
    if (loadingEl) loadingEl.remove();

    if (res && res.success && res.data.length > 0) {
      appendAltSources(currentData.url, res.data);
    } else {
      const emptyEl = document.createElement('div');
      emptyEl.className = 'source-empty';
      emptyEl.textContent = '暂无其他源';
      sourceList.appendChild(emptyEl);
    }
  });
}

function loadNextEpisode() {
  if (!currentData || !currentData.name) return;

  setTimeout(() => {
    chrome.runtime.sendMessage({
      type: 'getEpisodeList',
      showName: currentData.name
    }, (res) => {
      if (!res || !res.success || !res.data || res.data.length === 0) return;

      const episodes = res.data;
      const currentIndex = episodes.findIndex(ep => ep.name === currentData.episode);
      if (currentIndex < 0 || currentIndex >= episodes.length - 1) return;

      const nextEp = episodes[currentIndex + 1];
      currentData.nextPlayUrl = nextEp.playUrl;
      currentData.nextEpisode = nextEp.name;

      nextEpisodeBtn.classList.remove('hidden');
    });
  }, 500);
}

function buildSourceList(currentUrl) {
  sourceList.innerHTML = '';

  const mainItem = createSourceItem(currentUrl, '当前源', true);
  sourceList.appendChild(mainItem);

  const loadingEl = document.createElement('div');
  loadingEl.className = 'source-loading';
  loadingEl.innerHTML = '<span class="loading-spinner"></span> 搜索其他源中...';
  sourceList.appendChild(loadingEl);
}

function appendAltSources(currentUrl, altSources) {
  for (const alt of altSources) {
    const item = createSourceItem(alt.url, alt.name, false);
    sourceList.appendChild(item);
  }
}

function createSourceItem(url, name, isCurrent) {
  const div = document.createElement('div');
  div.className = 'source-item' + (isCurrent ? ' active' : '');

  const nameSpan = document.createElement('span');
  nameSpan.className = 'source-name';
  nameSpan.textContent = name;
  div.appendChild(nameSpan);

  if (isCurrent) {
    const tag = document.createElement('span');
    tag.className = 'source-tag current';
    tag.textContent = '当前';
    div.appendChild(tag);
  }

  div.addEventListener('click', () => {
    playerFrame.src = url;

    currentData.url = url;
    chrome.storage.session.set({ playerData: currentData });

    sourceList.querySelectorAll('.source-item').forEach(item => {
      item.classList.remove('active');
      item.querySelectorAll('.source-tag.current').forEach(t => t.remove());
    });
    div.classList.add('active');
    const tag = document.createElement('span');
    tag.className = 'source-tag current';
    tag.textContent = '当前';
    div.appendChild(tag);

    sourceDropdown.classList.add('hidden');
  });

  return div;
}

toggleBtn.addEventListener('click', (e) => {
  e.stopPropagation();
  sourceDropdown.classList.toggle('hidden');
});

closeDropdown.addEventListener('click', () => {
  sourceDropdown.classList.add('hidden');
});

document.addEventListener('click', (e) => {
  if (!e.target.closest('.source-panel')) {
    sourceDropdown.classList.add('hidden');
  }
});

nextEpisodeBtn.addEventListener('click', () => {
  if (!currentData || !currentData.nextPlayUrl) return;

  currentData.url = currentData.nextPlayUrl;
  currentData.episode = currentData.nextEpisode;
  delete currentData.nextPlayUrl;
  delete currentData.nextEpisode;

  chrome.storage.session.set({ playerData: currentData });

  topTitle.textContent = `${currentData.name} - ${currentData.episode}`;
  showInfo.textContent = `${currentData.name} · ${currentData.episode}`;
  document.title = `${currentData.name} - ${currentData.episode}`;

  nextEpisodeBtn.classList.add('hidden');
  playerFrame.src = currentData.url;

  playerFrame.addEventListener('load', () => {
    loadAltSources();
    loadNextEpisode();
  }, { once: true });
});
