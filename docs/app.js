/**
 * Pulse Music - Proprietary Share & Deep Link Engine
 * Connects shared links to Pulse for playback, or provides one-tap APK download
 * and YouTube Music web fallback when Pulse is not installed.
 */
(function () {
  const APK_DOWNLOAD_URL = "https://winter-soljer.github.io/pulse/Pulse.apk";
  const YTM_BASE = "https://music.youtube.com";

  // DOM Elements
  const elArtwork = document.getElementById("artwork");
  const elSongKind = document.getElementById("song-kind");
  const elSongTitle = document.getElementById("song-title");
  const elSongArtist = document.getElementById("song-artist");
  const elStatusBanner = document.getElementById("status-banner");
  const elStatusIcon = document.getElementById("status-icon");
  const elStatusText = document.getElementById("status-text");
  const elBtnDownload = document.getElementById("btn-download");
  const elBtnOpen = document.getElementById("btn-open");
  const elBtnWeb = document.getElementById("btn-web");

  // Parse URL & query parameters
  const url = new URL(window.location.href);
  const params = url.searchParams;

  // Path segments (handles /pulse/watch, /pulse/browse/id, etc.)
  const rawPath = url.pathname.split("/").filter(Boolean);
  const segments = rawPath[0] === "pulse" ? rawPath.slice(1) : rawPath;

  // Extract content ID
  let videoId = params.get("v") || params.get("id");
  let playlistId = params.get("list");
  let browseId = null;
  let channelId = null;

  if (!videoId && (segments[0] === "watch" || segments[0] === "song" || segments[0] === "play") && segments[1]) {
    videoId = segments[1];
  } else if (segments[0] === "browse" && segments[1]) {
    browseId = segments[1];
  } else if (segments[0] === "channel" && segments[1]) {
    channelId = segments[1];
  } else if (segments[0] === "playlist" && params.get("list")) {
    playlistId = params.get("list");
  }

  // Pre-fill metadata from query if available
  const queryTitle = params.get("title");
  const queryArtist = params.get("artist");
  if (queryTitle) elSongTitle.textContent = queryTitle;
  if (queryArtist) elSongArtist.textContent = queryArtist;

  // Set up destination URLs
  let ytmFallbackUrl = YTM_BASE;
  let pulseSchemeUrl = "pulse://player";
  let androidIntentUrl = `intent://music.youtube.com#Intent;scheme=https;package=com.techfisher.pulsestream;action=android.intent.action.VIEW;category=android.intent.category.BROWSABLE;S.browser_fallback_url=${encodeURIComponent(APK_DOWNLOAD_URL)};end`;

  if (videoId) {
    elSongKind.textContent = "Song";
    ytmFallbackUrl = `${YTM_BASE}/watch?v=${encodeURIComponent(videoId)}`;
    pulseSchemeUrl = `pulse://watch?v=${encodeURIComponent(videoId)}`;
    androidIntentUrl = `intent://music.youtube.com/watch?v=${encodeURIComponent(videoId)}#Intent;scheme=https;package=com.techfisher.pulsestream;action=android.intent.action.VIEW;category=android.intent.category.BROWSABLE;S.browser_fallback_url=${encodeURIComponent(APK_DOWNLOAD_URL)};end`;

    // High quality thumbnail
    elArtwork.src = `https://i.ytimg.com/vi/${encodeURIComponent(videoId)}/hqdefault.jpg`;
    elArtwork.onerror = function () {
      elArtwork.src = "https://winter-soljer.github.io/pulse/screenshots/now-playing.png";
    };

    // If title wasn't in query, fetch live via oEmbed
    if (!queryTitle) {
      fetch(`https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=${encodeURIComponent(videoId)}&format=json`)
        .then((res) => (res.ok ? res.json() : null))
        .then((data) => {
          if (data) {
            if (data.title && !queryTitle) elSongTitle.textContent = data.title;
            if (data.author_name && !queryArtist) elSongArtist.textContent = data.author_name;
          }
        })
        .catch(() => {});
    }
  } else if (browseId) {
    elSongKind.textContent = browseId.startsWith("MPRE") ? "Album" : "Collection";
    elSongTitle.textContent = queryTitle || (browseId.startsWith("MPRE") ? "Shared Album" : "Shared Collection");
    ytmFallbackUrl = `${YTM_BASE}/browse/${encodeURIComponent(browseId)}`;
    pulseSchemeUrl = `pulse://browse/${encodeURIComponent(browseId)}`;
    androidIntentUrl = `intent://music.youtube.com/browse/${encodeURIComponent(browseId)}#Intent;scheme=https;package=com.techfisher.pulsestream;action=android.intent.action.VIEW;category=android.intent.category.BROWSABLE;S.browser_fallback_url=${encodeURIComponent(APK_DOWNLOAD_URL)};end`;
  } else if (playlistId) {
    elSongKind.textContent = "Playlist";
    elSongTitle.textContent = queryTitle || "Shared Playlist";
    ytmFallbackUrl = `${YTM_BASE}/playlist?list=${encodeURIComponent(playlistId)}`;
    pulseSchemeUrl = `pulse://playlist?list=${encodeURIComponent(playlistId)}`;
    androidIntentUrl = `intent://music.youtube.com/playlist?list=${encodeURIComponent(playlistId)}#Intent;scheme=https;package=com.techfisher.pulsestream;action=android.intent.action.VIEW;category=android.intent.category.BROWSABLE;S.browser_fallback_url=${encodeURIComponent(APK_DOWNLOAD_URL)};end`;
  } else if (channelId) {
    elSongKind.textContent = "Artist";
    elSongTitle.textContent = queryTitle || "Shared Artist";
    ytmFallbackUrl = `${YTM_BASE}/channel/${encodeURIComponent(channelId)}`;
    pulseSchemeUrl = `pulse://channel/${encodeURIComponent(channelId)}`;
    androidIntentUrl = `intent://music.youtube.com/channel/${encodeURIComponent(channelId)}#Intent;scheme=https;package=com.techfisher.pulsestream;action=android.intent.action.VIEW;category=android.intent.category.BROWSABLE;S.browser_fallback_url=${encodeURIComponent(APK_DOWNLOAD_URL)};end`;
  } else {
    elSongKind.textContent = "Pulse Music";
    elSongTitle.textContent = "Play on Pulse";
    elSongArtist.textContent = "Ad-free, open source music streamer";
  }

  // Detect Android
  const isAndroid = /Android/i.test(navigator.userAgent);

  // Configure action buttons directly with native links
  if (isAndroid) {
    elBtnOpen.href = androidIntentUrl;
  } else {
    elBtnOpen.href = pulseSchemeUrl;
  }
  elBtnDownload.href = APK_DOWNLOAD_URL;
  elBtnDownload.setAttribute("download", "Pulse.apk");
  elBtnWeb.href = ytmFallbackUrl;

  // Status message
  const elStatusSpinner = document.querySelector(".status-spinner");
  if (elStatusSpinner) elStatusSpinner.style.display = "none";
  if (elStatusIcon) {
    elStatusIcon.textContent = isAndroid ? "🎵" : "💻";
    elStatusIcon.style.display = "inline-block";
  }
  elStatusText.innerHTML = isAndroid
    ? "<strong>Listen on Pulse:</strong> Tap <b>Play in Pulse</b> to play immediately, or download the app below."
    : "<strong>Pulse is an Android app.</strong> Download the APK for your phone or listen on YouTube Music.";

  // Clicking "Play in Pulse"
  elBtnOpen.addEventListener("click", function () {
    if (elStatusIcon) elStatusIcon.textContent = "▶️";
    elStatusText.innerHTML = "<strong>Launching Pulse…</strong> If the app doesn't open, tap Download Pulse (.apk) below.";
  });

  // Clicking "Download Pulse"
  elBtnDownload.addEventListener("click", function () {
    if (elStatusIcon) elStatusIcon.textContent = "⬇️";
    elStatusText.innerHTML = "<strong>Downloading Pulse.apk…</strong> When finished, tap the notification to install.";
  });

  // Safe background trigger for Android without hijacking the page or forcing download
  if (isAndroid && (videoId || playlistId || browseId || channelId)) {
    try {
      const iframe = document.createElement("iframe");
      iframe.style.display = "none";
      iframe.src = pulseSchemeUrl;
      document.body.appendChild(iframe);
      setTimeout(function () {
        iframe.remove();
      }, 2000);
    } catch (_) {}
  }
})();
