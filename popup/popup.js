const searchInput = document.getElementById('searchInput');
const searchBtn = document.getElementById('searchBtn');
const loadingEl = document.getElementById('loading');
const resultsEl = document.getElementById('results');
const errorEl = document.getElementById('error');
const warmupStatusEl = document.getElementById('warmupStatus');
const historyContentEl = document.getElementById('historyContent');
const favoritesContentEl = document.getElementById('favoritesContent');

const RECENT_EPISODES_COUNT = 10;
let currentResults = [];

searchBtn.addEventListener('click', handleSearch);
searchInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') handleSearch();
});

searchInput.focus();

// Tab 切换
document.querySelectorAll('.tab').forEach(tab => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(c => c.classList.add('hidden'));
    tab.classList.add('active');
    const target = tab.dataset.tab;
    if (target === 'search') document.getElementById('tabSearch').classList.remove('hidden');
    if (target === 'history') { document.getElementById('tabHistory').classList.remove('hidden'); loadHistory(); }
    if (target === 'favorites') { document.getElementById('tabFavorites').classList.remove('hidden'); loadFavorites(); }
  });
});

// popup 打开时立即预热解析接口
warmupStatusEl.textContent = '⏳ 检测解析接口中...';
chrome.runtime.sendMessage({ type: 'warmup' }).then(res => {
  if (res && res.success) {
    warmupStatusEl.textContent = `✓ ${res.available} 个接口可用，最快: ${res.fastest}`;
    warmupStatusEl.classList.add('ready');
  } else {
    warmupStatusEl.textContent = '⚠ 接口检测失败，将使用默认接口';
  }
});

async function handleSearch() {
  const keyword = searchInput.value.trim();
  if (!keyword) return;

  showLoading();
  hideError();
  clearResults();
  searchBtn.disabled = true;

  try {
    const response = await chrome.runtime.sendMessage({ type: 'search', keyword });

    if (!response.success) {
      showError(response.error || '搜索失败，请稍后重试');
      return;
    }

    if (response.data.length === 0) {
      showError('未找到相关资源，请尝试其他关键词');
      return;
    }

    currentResults = response.data;
    renderResults(response.data);
  } catch (err) {
    showError('搜索出错: ' + err.message);
  } finally {
    hideLoading();
    searchBtn.disabled = false;
  }
}

function renderResults(results) {
  resultsEl.innerHTML = '';

  for (const item of results) {
    const el = document.createElement('div');
    el.className = 'result-item';

    const episodes = item.episodes || [];
    const recentEpisodes = episodes.slice(-RECENT_EPISODES_COUNT);
    const hasMore = episodes.length > RECENT_EPISODES_COUNT;

    el.innerHTML = `
      <div class="result-title">
        ${escapeHtml(item.name)}
        <button class="fav-btn" data-name="${escapeAttr(item.name)}" data-pic="${escapeAttr(item.pic)}" data-type="${escapeAttr(item.type)}" data-year="${escapeAttr(item.year)}" data-remarks="${escapeAttr(item.remarks)}">☆ 收藏</button>
      </div>
      <div class="result-meta">
        ${item.type ? item.type + ' · ' : ''}${item.year || ''}${item.remarks ? ' · ' + item.remarks : ''}
      </div>
      <div class="episodes" data-show-name="${escapeAttr(item.name)}">
        ${recentEpisodes.map((ep, idx) => buildEpisodeHtml(ep, idx)).join('')}
        ${hasMore ? `<button class="show-all-btn">查看全部 (${episodes.length}集)</button>` : ''}
      </div>
    `;

    bindEpisodeEvents(el);

    const showAllBtn = el.querySelector('.show-all-btn');
    if (showAllBtn) {
      showAllBtn.addEventListener('click', () => {
        renderAllEpisodes(el, episodes);
      });
    }

    // 收藏按钮
    const favBtn = el.querySelector('.fav-btn');
    if (favBtn) {
      favBtn.addEventListener('click', () => {
        const favItem = {
          name: favBtn.dataset.name,
          pic: favBtn.dataset.pic,
          type: favBtn.dataset.type,
          year: favBtn.dataset.year,
          remarks: favBtn.dataset.remarks
        };
        chrome.runtime.sendMessage({ type: 'addFavorite', item: favItem }).then(res => {
          if (res.success) {
            favBtn.textContent = '★ 已收藏';
            favBtn.classList.add('is-fav');
          } else {
            favBtn.textContent = '★ 已收藏';
            favBtn.classList.add('is-fav');
          }
        });
      });
    }

    resultsEl.appendChild(el);
  }
}

function buildEpisodeHtml(ep, idx) {
  return `<a class="episode-btn" href="#" data-url="${escapeAttr(ep.playUrl)}" title="${escapeAttr(ep.source)}">${escapeHtml(ep.name)}</a>`;
}

function bindEpisodeEvents(container) {
  container.querySelectorAll('.episode-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      const url = btn.dataset.url;
      const showName = btn.closest('.episodes')?.dataset.showName || '';
      const episode = btn.textContent.trim();
      if (url) chrome.runtime.sendMessage({ type: 'play', url, name: showName, episode });
    });
  });
}

function renderAllEpisodes(containerEl, episodes) {
  const episodesEl = containerEl.querySelector('.episodes');
  episodesEl.innerHTML = episodes.map((ep, idx) => buildEpisodeHtml(ep, idx)).join('');
  bindEpisodeEvents(episodesEl);
}

function showLoading() { loadingEl.classList.remove('hidden'); }
function hideLoading() { loadingEl.classList.add('hidden'); }
function showError(msg) {
  errorEl.textContent = msg;
  errorEl.classList.remove('hidden');
}
function hideError() { errorEl.classList.add('hidden'); }
function clearResults() { resultsEl.innerHTML = ''; }

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escapeAttr(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

// === 历史记录 ===

async function loadHistory() {
  const [searchHistory, playHistory] = await Promise.all([
    chrome.runtime.sendMessage({ type: 'getSearchHistory' }),
    chrome.runtime.sendMessage({ type: 'getPlayHistory' })
  ]);

  let html = '';

  if (searchHistory && searchHistory.length > 0) {
    html += `<div class="history-section">
      <div class="history-section-title">
        <span>搜索历史</span>
        <button class="clear-btn" id="clearSearchHistory">清空</button>
      </div>`;
    for (const item of searchHistory) {
      html += `<div class="history-item" data-keyword="${escapeAttr(item.keyword)}">
        <span class="history-keyword">🔍 ${escapeHtml(item.keyword)}</span>
        <span class="history-time">${formatTime(item.timestamp)}</span>
      </div>`;
    }
    html += '</div>';
  }

  if (playHistory && playHistory.length > 0) {
    html += `<div class="history-section">
      <div class="history-section-title"><span>播放历史</span></div>`;
    for (const item of playHistory) {
      html += `<div class="history-item" data-url="${escapeAttr(item.url)}">
        <span class="history-keyword">▶ ${escapeHtml(item.name)} - ${escapeHtml(item.episode)}</span>
        <span class="history-time">${formatTime(item.timestamp)}</span>
      </div>`;
    }
    html += '</div>';
  }

  if (!html) {
    html = '<div class="empty-state">暂无历史记录</div>';
  }

  historyContentEl.innerHTML = html;

  // 点击搜索历史重新搜索
  historyContentEl.querySelectorAll('.history-item[data-keyword]').forEach(item => {
    item.addEventListener('click', () => {
      searchInput.value = item.dataset.keyword;
      document.querySelector('.tab[data-tab="search"]').click();
      handleSearch();
    });
  });

  // 点击播放历史直接播放
  historyContentEl.querySelectorAll('.history-item[data-url]').forEach(item => {
    item.addEventListener('click', () => {
      chrome.runtime.sendMessage({ type: 'play', url: item.dataset.url });
    });
  });

  // 清空搜索历史
  const clearBtn = document.getElementById('clearSearchHistory');
  if (clearBtn) {
    clearBtn.addEventListener('click', async () => {
      await chrome.runtime.sendMessage({ type: 'clearSearchHistory' });
      loadHistory();
    });
  }
}

// === 收藏夹 ===

async function loadFavorites() {
  const favorites = await chrome.runtime.sendMessage({ type: 'getFavorites' });

  if (!favorites || favorites.length === 0) {
    favoritesContentEl.innerHTML = '<div class="empty-state">暂无收藏，搜索后点击 ☆ 收藏</div>';
    return;
  }

  let html = '';
  for (const item of favorites) {
    html += `<div class="fav-item">
      <div class="fav-info" data-name="${escapeAttr(item.name)}">
        <div class="fav-name">${escapeHtml(item.name)}</div>
        <div class="fav-meta">${item.type ? item.type + ' · ' : ''}${item.year || ''}${item.remarks ? ' · ' + item.remarks : ''}</div>
      </div>
      <button class="fav-remove" data-name="${escapeAttr(item.name)}">取消收藏</button>
    </div>`;
  }

  favoritesContentEl.innerHTML = html;

  // 点击收藏项重新搜索
  favoritesContentEl.querySelectorAll('.fav-info').forEach(info => {
    info.addEventListener('click', () => {
      searchInput.value = info.dataset.name;
      document.querySelector('.tab[data-tab="search"]').click();
      handleSearch();
    });
  });

  // 取消收藏
  favoritesContentEl.querySelectorAll('.fav-remove').forEach(btn => {
    btn.addEventListener('click', async () => {
      await chrome.runtime.sendMessage({ type: 'removeFavorite', name: btn.dataset.name });
      loadFavorites();
    });
  });
}

function formatTime(timestamp) {
  const date = new Date(timestamp);
  const now = new Date();
  const diff = now - date;

  if (diff < 60000) return '刚刚';
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前';
  return date.toLocaleDateString('zh-CN');
}
