const searchInput = document.getElementById('searchInput');
const searchBtn = document.getElementById('searchBtn');
const loadingEl = document.getElementById('loading');
const resultsEl = document.getElementById('results');
const errorEl = document.getElementById('error');
const warmupStatusEl = document.getElementById('warmupStatus');

const RECENT_EPISODES_COUNT = 10;

searchBtn.addEventListener('click', handleSearch);
searchInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') handleSearch();
});

searchInput.focus();

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
      <div class="result-title">${escapeHtml(item.name)}</div>
      <div class="result-meta">
        ${item.type ? item.type + ' · ' : ''}${item.year || ''}${item.remarks ? ' · ' + item.remarks : ''}
      </div>
      <div class="episodes">
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

    resultsEl.appendChild(el);
  }
}

function buildEpisodeHtml(ep, idx) {
  const hasAlt = ep.altUrls && ep.altUrls.length > 0;
  if (!hasAlt) {
    return `<a class="episode-btn" href="#" data-url="${escapeAttr(ep.playUrl)}" title="${escapeAttr(ep.source)}">${escapeHtml(ep.name)}</a>`;
  }

  const altLinksHtml = ep.altUrls.map(alt =>
    `<a class="alt-link" href="#" data-url="${escapeAttr(alt.url)}">${escapeHtml(alt.name)}</a>`
  ).join('');

  return `
    <span class="episode-group">
      <a class="episode-btn has-alt" href="#" data-url="${escapeAttr(ep.playUrl)}" title="点击播放 | 来源: ${escapeAttr(ep.source)}">${escapeHtml(ep.name)}</a>
      <button class="alt-source-btn" title="换源（看不了点这里）">▼</button>
      <div class="alt-dropdown">
        <div class="alt-dropdown-title">换源播放</div>
        ${altLinksHtml}
      </div>
    </span>
  `;
}

function bindEpisodeEvents(container) {
  container.querySelectorAll('.episode-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      const url = btn.dataset.url;
      if (url) chrome.runtime.sendMessage({ type: 'play', url });
    });
  });

  container.querySelectorAll('.alt-source-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const dropdown = btn.nextElementSibling;
      // 关闭其他所有下拉
      document.querySelectorAll('.alt-dropdown.show').forEach(d => {
        if (d !== dropdown) d.classList.remove('show');
      });
      dropdown.classList.toggle('show');
    });
  });

  container.querySelectorAll('.alt-link').forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      const url = link.dataset.url;
      if (url) chrome.runtime.sendMessage({ type: 'play', url });
      link.closest('.alt-dropdown').classList.remove('show');
    });
  });
}

function renderAllEpisodes(containerEl, episodes) {
  const episodesEl = containerEl.querySelector('.episodes');
  episodesEl.innerHTML = episodes.map((ep, idx) => buildEpisodeHtml(ep, idx)).join('');
  bindEpisodeEvents(episodesEl);
}

// 点击其他区域关闭所有下拉
document.addEventListener('click', () => {
  document.querySelectorAll('.alt-dropdown.show').forEach(d => d.classList.remove('show'));
});

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
