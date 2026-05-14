class FloatingPanel {
  constructor(shadow, url, name, episode, onClose) {
    this.shadow = shadow;
    this.onClose = onClose;
    this.currentName = name;
    this.currentEpisode = episode;
    this.width = 400;
    this.height = 225;
    this.x = window.innerWidth - this.width - 20;
    this.y = window.innerHeight - this.height - 20;
    this.isMinimized = false;

    this.render(url, name, episode);
    this.bindDrag();
    this.bindResize();
    this.startLoadTimer();
  }

  render(url, name, episode) {
    this.shadow.innerHTML = `
      <style>${this.getStyles()}</style>
      <div class="viper-panel" style="left:${this.x}px;top:${this.y}px;width:${this.width}px;height:${this.height}px;">
        <div class="viper-header">
          <span class="viper-title">${this.escapeHtml(name)} - ${this.escapeHtml(episode)}</span>
          <div class="viper-controls">
            <button class="viper-min" title="最小化">─</button>
            <button class="viper-close" title="关闭">✕</button>
          </div>
        </div>
        <div class="viper-body">
          <iframe src="${this.escapeHtml(url)}" allowfullscreen></iframe>
        </div>
        <div class="viper-source-bar">
          <button class="viper-source-toggle">换源 ▼</button>
          <div class="viper-source-list hidden"></div>
        </div>
        <div class="viper-resize-handle"></div>
      </div>
      <div class="viper-minimized hidden" title="${this.escapeHtml(name)}">
        <span>▶</span>
      </div>
    `;

    this.panel = this.shadow.querySelector('.viper-panel');
    this.minBtn = this.shadow.querySelector('.viper-min');
    this.closeBtn = this.shadow.querySelector('.viper-close');
    this.minimizedEl = this.shadow.querySelector('.viper-minimized');
    this.resizeHandle = this.shadow.querySelector('.viper-resize-handle');
    this.iframe = this.shadow.querySelector('iframe');
    this.titleEl = this.shadow.querySelector('.viper-title');
    this.sourceToggle = this.shadow.querySelector('.viper-source-toggle');
    this.sourceList = this.shadow.querySelector('.viper-source-list');

    this.minBtn.addEventListener('click', () => this.toggleMinimize());
    this.closeBtn.addEventListener('click', () => this.close());
    this.minimizedEl.addEventListener('click', () => this.toggleMinimize());
    this.sourceToggle.addEventListener('click', () => this.toggleSourceList());
  }

  update(url, name, episode) {
    this.iframe.src = url;
    this.titleEl.textContent = `${name} - ${episode}`;
    this.currentName = name;
    this.currentEpisode = episode;
    this.sourceList.innerHTML = '';
    this.sourceList.classList.add('hidden');
    clearTimeout(this.loadTimer);
    this.startLoadTimer();
  }

  toggleMinimize() {
    this.isMinimized = !this.isMinimized;
    this.panel.classList.toggle('hidden', this.isMinimized);
    this.minimizedEl.classList.toggle('hidden', !this.isMinimized);
  }

  close() {
    clearTimeout(this.loadTimer);
    this.removeDragListeners();
    this.removeResizeListeners();
    this.onClose();
  }

  startLoadTimer() {
    try {
      if (this.iframe.contentDocument && this.iframe.contentDocument.readyState === 'complete') {
        return;
      }
    } catch (e) {
      // Cross-origin iframe, can't check — proceed with timer
    }

    this.loadTimer = setTimeout(() => {
      this.showLoadError();
    }, 10000);

    this.iframe.addEventListener('load', () => {
      clearTimeout(this.loadTimer);
    }, { once: true });
  }

  showLoadError() {
    this.shadow.querySelector('.viper-error')?.remove();
    const body = this.shadow.querySelector('.viper-body');
    const errorEl = document.createElement('div');
    errorEl.className = 'viper-error';
    errorEl.innerHTML = `
      <span>视频加载失败</span>
      <button class="viper-retry-btn">点击换源</button>
    `;
    body.appendChild(errorEl);
    errorEl.querySelector('.viper-retry-btn').addEventListener('click', () => {
      errorEl.remove();
      this.toggleSourceList();
    });
  }

  async toggleSourceList() {
    this.sourceList.classList.toggle('hidden');
    if (!this.sourceList.classList.contains('hidden') && this.sourceList.children.length === 0) {
      this.sourceList.innerHTML = '<div class="viper-source-loading">搜索其他源中...</div>';
      try {
        const res = await chrome.runtime.sendMessage({
          type: 'getAltSources',
          showName: this.currentName,
          episode: this.currentEpisode,
          currentUrl: this.iframe.src
        });
        this.sourceList.innerHTML = '';
        if (res && res.success && res.data.length > 0) {
          const current = document.createElement('div');
          current.className = 'viper-source-item active';
          current.textContent = '当前源';
          this.sourceList.appendChild(current);
          for (const alt of res.data) {
            const item = document.createElement('div');
            item.className = 'viper-source-item';
            item.textContent = alt.name;
            item.addEventListener('click', () => {
              this.iframe.src = alt.url;
              this.sourceList.classList.add('hidden');
              this.sourceList.innerHTML = '';
            });
            this.sourceList.appendChild(item);
          }
        } else {
          this.sourceList.innerHTML = '<div class="viper-source-empty">暂无其他源</div>';
        }
      } catch {
        this.sourceList.innerHTML = '<div class="viper-source-empty">换源失败</div>';
      }
    }
  }

  bindDrag() {
    const header = this.shadow.querySelector('.viper-header');
    let isDragging = false;
    let startX, startY, origX, origY;

    const onMouseDown = (e) => {
      if (e.target.closest('.viper-controls')) return;
      isDragging = true;
      startX = e.clientX;
      startY = e.clientY;
      origX = this.x;
      origY = this.y;
      e.preventDefault();
    };

    const onMouseMove = (e) => {
      if (!isDragging) return;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      this.x = Math.max(0, Math.min(origX + dx, window.innerWidth - this.width));
      this.y = Math.max(-(this.height - 30), Math.min(origY + dy, window.innerHeight - 40));
      this.panel.style.left = this.x + 'px';
      this.panel.style.top = this.y + 'px';
    };

    const onMouseUp = () => {
      isDragging = false;
    };

    header.addEventListener('mousedown', onMouseDown);
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);

    this._dragCleanup = () => {
      header.removeEventListener('mousedown', onMouseDown);
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };
  }

  removeDragListeners() {
    if (this._dragCleanup) this._dragCleanup();
  }

  bindResize() {
    const handle = this.shadow.querySelector('.viper-resize-handle');
    let isResizing = false;
    let startX, startY, origW, origH;

    const onMouseDown = (e) => {
      isResizing = true;
      startX = e.clientX;
      startY = e.clientY;
      origW = this.width;
      origH = this.height;
      e.preventDefault();
      e.stopPropagation();
    };

    const onMouseMove = (e) => {
      if (!isResizing) return;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      let newW = Math.max(320, origW + dx);
      let newH = newW * 9 / 16;
      const maxW = window.innerWidth * 0.8;
      const maxH = window.innerHeight * 0.8;
      if (newW > maxW) { newW = maxW; newH = newW * 9 / 16; }
      if (newH > maxH) { newH = maxH; newW = newH * 16 / 9; }
      if (newH < 180) { newH = 180; newW = newH * 16 / 9; }
      this.width = Math.round(newW);
      this.height = Math.round(newH);
      this.panel.style.width = this.width + 'px';
      this.panel.style.height = this.height + 'px';
    };

    const onMouseUp = () => {
      isResizing = false;
    };

    handle.addEventListener('mousedown', onMouseDown);
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);

    this._resizeCleanup = () => {
      handle.removeEventListener('mousedown', onMouseDown);
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };
  }

  removeResizeListeners() {
    if (this._resizeCleanup) this._resizeCleanup();
  }

  escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  getStyles() {
    return `
      :host { all: initial; }
      .hidden { display: none !important; }
      .viper-panel {
        position: fixed;
        z-index: 2147483647;
        background: #1a1a2e;
        border: 1px solid #667eea;
        border-radius: 10px;
        box-shadow: 0 8px 32px rgba(0,0,0,0.6);
        overflow: visible;
        display: flex;
        flex-direction: column;
      }
      .viper-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 6px 10px;
        background: linear-gradient(135deg, #667eea, #764ba2);
        cursor: move;
        user-select: none;
        flex-shrink: 0;
      }
      .viper-title {
        color: #fff;
        font-size: 12px;
        font-weight: 500;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .viper-controls {
        display: flex;
        gap: 4px;
      }
      .viper-controls button {
        background: rgba(255,255,255,0.2);
        border: none;
        color: #fff;
        width: 22px;
        height: 22px;
        border-radius: 4px;
        cursor: pointer;
        font-size: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .viper-controls button:hover {
        background: rgba(255,255,255,0.35);
      }
      .viper-body {
        flex: 1;
        min-height: 0;
      }
      .viper-body iframe {
        width: 100%;
        height: 100%;
        border: none;
      }
      .viper-source-bar {
        position: relative;
        flex-shrink: 0;
        background: rgba(26,26,46,0.9);
        padding: 4px 8px;
        display: flex;
        align-items: center;
        opacity: 0;
        transition: opacity 0.2s;
      }
      .viper-panel:hover .viper-source-bar {
        opacity: 1;
      }
      .viper-source-toggle {
        background: none;
        border: 1px solid #667eea;
        color: #667eea;
        font-size: 11px;
        padding: 3px 8px;
        border-radius: 4px;
        cursor: pointer;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      }
      .viper-source-toggle:hover {
        background: rgba(102,126,234,0.2);
      }
      .viper-source-list {
        position: absolute;
        top: 100%;
        left: 8px;
        background: #1a1a2e;
        border: 1px solid #667eea;
        border-radius: 6px;
        padding: 6px;
        margin-top: 4px;
        max-height: 150px;
        overflow-y: auto;
        min-width: 160px;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      }
      .viper-source-list.hidden { display: none; }
      .viper-source-item {
        padding: 6px 10px;
        color: #ccc;
        font-size: 12px;
        cursor: pointer;
        border-radius: 4px;
      }
      .viper-source-item:hover {
        background: rgba(102,126,234,0.2);
        color: #fff;
      }
      .viper-source-item.active {
        color: #667eea;
        font-weight: 500;
      }
      .viper-source-loading {
        padding: 6px 10px;
        color: #888;
        font-size: 11px;
      }
      .viper-source-empty {
        padding: 6px 10px;
        color: #666;
        font-size: 11px;
      }
      .viper-resize-handle {
        position: absolute;
        bottom: 0;
        right: 0;
        width: 16px;
        height: 16px;
        cursor: nwse-resize;
        z-index: 2;
      }
      .viper-resize-handle::after {
        content: '';
        position: absolute;
        bottom: 3px;
        right: 3px;
        width: 8px;
        height: 8px;
        border-right: 2px solid rgba(255,255,255,0.3);
        border-bottom: 2px solid rgba(255,255,255,0.3);
      }
      .viper-minimized {
        position: fixed;
        bottom: 20px;
        right: 20px;
        width: 40px;
        height: 40px;
        background: linear-gradient(135deg, #667eea, #764ba2);
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        z-index: 2147483647;
        box-shadow: 0 4px 12px rgba(0,0,0,0.4);
        color: #fff;
        font-size: 16px;
      }
      .viper-minimized:hover {
        transform: scale(1.1);
      }
      .viper-minimized.hidden { display: none; }
      .viper-error {
        position: absolute;
        inset: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        background: #1a1a2e;
        color: #ccc;
        font-size: 13px;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        gap: 8px;
      }
      .viper-error button {
        background: #667eea;
        border: none;
        color: #fff;
        padding: 6px 16px;
        border-radius: 6px;
        cursor: pointer;
        font-size: 12px;
      }
    `;
  }
}
