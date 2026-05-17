/**
 * CMS API 响应解析与标准化
 */

export function parseApiResponse(response, sourceId) {
  if (!response || response.code !== 1 || !Array.isArray(response.list)) {
    return [];
  }

  return response.list.map(item => {
    const episodes = parseEpisodes(item.vod_play_from, item.vod_play_url);
    return {
      id: `${sourceId}_${item.vod_id}`,
      sourceId,
      name: item.vod_name || '',
      pic: item.vod_pic || '',
      year: item.vod_year || '',
      area: item.vod_area || '',
      type: item.type_name || '',
      remarks: item.vod_remarks || '',
      episodes
    };
  });
}

function parseEpisodes(playFrom, playUrl) {
  if (!playFrom || !playUrl) return [];

  const sources = playFrom.split('$$$');
  const urlGroups = playUrl.split('$$$');
  const allEpisodes = [];

  for (let i = 0; i < sources.length; i++) {
    const sourceName = sources[i] || `线路${i + 1}`;
    const urlGroup = urlGroups[i] || '';
    const episodes = urlGroup.split('#').map(ep => {
      const dollarIdx = ep.lastIndexOf('$');
      if (dollarIdx <= 0) return null;
      return {
        name: ep.substring(0, dollarIdx),
        url: ep.substring(dollarIdx + 1),
        source: sourceName
      };
    }).filter(Boolean);

    allEpisodes.push(...episodes.map(ep => ({ ...ep, sourceIndex: i })));
  }

  return allEpisodes;
}

export function mergeResults(allResults) {
  const merged = new Map();

  for (const results of allResults) {
    for (const item of results) {
      const key = item.name.trim();
      if (merged.has(key)) {
        const existing = merged.get(key);
        existing.episodes.push(...item.episodes);
        existing.sources.push(item.sourceId);
        if (!existing.pic && item.pic) existing.pic = item.pic;
        if (!existing.remarks && item.remarks) existing.remarks = item.remarks;
      } else {
        merged.set(key, {
          ...item,
          sources: [item.sourceId],
          episodes: [...item.episodes]
        });
      }
    }
  }

  return Array.from(merged.values());
}

export function deduplicateEpisodes(episodes) {
  const episodeMap = new Map();

  for (const ep of episodes) {
    const key = ep.name.trim();
    if (!episodeMap.has(key)) {
      episodeMap.set(key, ep);
    }
  }

  return Array.from(episodeMap.values());
}
