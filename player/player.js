const playerFrame = document.getElementById('playerFrame');
const toggleBtn = document.getElementById('toggleBtn');
const sourceDropdown = document.getElementById('sourceDropdown');
const closeDropdown = document.getElementById('closeDropdown');
const showInfo = document.getElementById('showInfo');
const sourceList = document.getElementById('sourceList');
const topTitle = document.getElementById('topTitle');

let currentData = null;

chrome.storage.session.get('playerData', (result) => {
  if (!result.playerData) {
    document.body.innerHTML = '<div style="color:#fff;display:flex;align-items:center;justify-content:center;height:100%;font-size:16px;">无播放数据，请从扩展搜索页打开</div>';
    return;
  }

  currentData = result.playerData;
  const { url, name, episode, altUrls } = currentData;

  topTitle.textContent = `${name} - ${episode}`;
  showInfo.textContent = `${name} · ${episode}`;
  document.title = `${name} - ${episode}`;

  playerFrame.src = url;

  buildSourceList(url, altUrls);
});

function buildSourceList(currentUrl, altUrls) {
  sourceList.innerHTML = '';

  const mainItem = createSourceItem(currentUrl, '当前源', true);
  sourceList.appendChild(mainItem);

  if (altUrls && altUrls.length > 0) {
    for (const alt of altUrls) {
      const item = createSourceItem(alt.url, alt.name, false);
      sourceList.appendChild(item);
    }
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
