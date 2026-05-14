/**
 * 存储管理：搜索历史 + 播放历史 + 收藏夹
 * 使用 chrome.storage.local
 */

const KEYS = {
  SEARCH_HISTORY: 'searchHistory',
  PLAY_HISTORY: 'playHistory',
  FAVORITES: 'favorites'
};

const MAX_SEARCH_HISTORY = 20;
const MAX_PLAY_HISTORY = 50;

async function getStorage(key) {
  const result = await chrome.storage.local.get(key);
  return result[key] || [];
}

async function setStorage(key, value) {
  await chrome.storage.local.set({ [key]: value });
}

// === 搜索历史 ===

export async function getSearchHistory() {
  return getStorage(KEYS.SEARCH_HISTORY);
}

export async function addSearchHistory(keyword) {
  const history = await getSearchHistory();
  const filtered = history.filter(item => item.keyword !== keyword);
  filtered.unshift({ keyword, timestamp: Date.now() });
  await setStorage(KEYS.SEARCH_HISTORY, filtered.slice(0, MAX_SEARCH_HISTORY));
}

export async function clearSearchHistory() {
  await setStorage(KEYS.SEARCH_HISTORY, []);
}

// === 播放历史 ===

export async function getPlayHistory() {
  return getStorage(KEYS.PLAY_HISTORY);
}

export async function addPlayHistory(item) {
  const history = await getPlayHistory();
  const filtered = history.filter(h => !(h.name === item.name && h.episode === item.episode));
  filtered.unshift({
    name: item.name,
    episode: item.episode,
    url: item.url,
    timestamp: Date.now()
  });
  await setStorage(KEYS.PLAY_HISTORY, filtered.slice(0, MAX_PLAY_HISTORY));
}

// === 收藏夹 ===

export async function getFavorites() {
  return getStorage(KEYS.FAVORITES);
}

export async function addFavorite(item) {
  const favorites = await getFavorites();
  const exists = favorites.some(f => f.name === item.name);
  if (exists) return false;
  favorites.unshift({
    name: item.name,
    pic: item.pic || '',
    type: item.type || '',
    year: item.year || '',
    remarks: item.remarks || '',
    timestamp: Date.now()
  });
  await setStorage(KEYS.FAVORITES, favorites);
  return true;
}

export async function removeFavorite(name) {
  const favorites = await getFavorites();
  const filtered = favorites.filter(f => f.name !== name);
  await setStorage(KEYS.FAVORITES, filtered);
}

export async function isFavorite(name) {
  const favorites = await getFavorites();
  return favorites.some(f => f.name === name);
}
