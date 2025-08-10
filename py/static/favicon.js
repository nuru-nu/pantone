/**
 * Sets the page's favicon to an emoji.
 * @param {string} emoji The emoji to use as the favicon.
 */
export function setEmojiFavicon(emoji) {
  let favicon = document.querySelector("link[rel*='icon']");

  if (!favicon) {
    favicon = document.createElement('link');
    favicon.rel = 'icon';
    document.head.appendChild(favicon);
  }

  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
      <text y=".9em" font-size="90">${emoji}</text>
    </svg>
  `.trim();

  const dataUri = 'data:image/svg+xml,' + encodeURIComponent(svg);
  favicon.href = dataUri;
}
