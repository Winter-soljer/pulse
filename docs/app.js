/**
 * Pulse Music - Proprietary Share & Deep Link Engine
 * Handles opening shared music directly in Pulse (via Android Intents / pulse:// URI scheme)
 * or prompting automatic download of Pulse.apk if Pulse is not installed.
 */

(function () {
  const APK_DOWNLOAD_URL = "https://github.com/Winter-soljer/pulse/releases/latest/download/Pulse.apk";
  const GITHUB_REPO_URL = "https://github.com/Winter-soljer/pulse";
  const YTM_BASE = "https://music.youtube.com";

  // Elements
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

  // Parse current URL path & search params
  const url = new URL(window.location.href);
  const params = url.searchParams;

  // Path segments (handles /pulse/watch, /pulse/browse/id, etc.)
  const rawPath = url.pathname.split("/").filter(Boolean);
  const segments = rawPath[0] === "pulse" ? rawPath.slice(1) : rawPath;

  // Extract ID / action
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

  // Pre-filled metadata from query if available
  const queryTitle = params.get("title");
  const queryArtist = params.get("artist");
  if (queryTitle) elSongTitle.textContent = queryTitle;
  if (queryArtist) elSongArtist.textContent = queryArtist;

  // Target item setup
  let ytmFallbackUrl = YTM_BASE;
  let pulseSchemeUrl = "pulse://player";
  let androidIntentUrl = "intent://player#Intent;scheme=pulse;package=com.techfisher.pulsestream;end";

  if (videoId) {
    elSongKind.textContent = "Song";
    ytmFallbackUrl = `${YTM_BASE}/watch?v=${encodeURIComponent(videoId)}`;
    pulseSchemeUrl = `pulse://watch?v=${encodeURIComponent(videoId)}`;
    androidIntentUrl = `intent://watch?v=${encodeURIComponent(videoId)}#Intent;scheme=pulse;package=com.techfisher.pulsestream;end`;
    
    // Set HQ thumbnail
    elArtwork.src = `https://i.ytimg.com/vi/${encodeURIComponent(videoId)}/hqdefault.jpg`;
    elArtwork.onerror = function () {
      elArtwork.src = "https://winter-soljer.github.io/pulse/screenshots/now-playing.png";
    };

    // If title wasn't passed in query, fetch title & artist via oEmbed
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
    androidIntentUrl = `intent://browse/${encodeURIComponent(browseId)}#Intent;scheme=pulse;package=com.techfisher.pulsestream;end`;
  } else if (playlistId) {
    elSongKind.textContent = "Playlist";
    elSongTitle.textContent = queryTitle || "Shared Playlist";
    ytmFallbackUrl = `${YTM_BASE}/playlist?list=${encodeURIComponent(playlistId)}`;
    pulseSchemeUrl = `pulse://playlist?list=${encodeURIComponent(playlistId)}`;
    androidIntentUrl = `intent://playlist?list=${encodeURIComponent(playlistId)}#Intent;scheme=pulse;package=com.techfisher.pulsestream;end`;
  } else if (channelId) {
    elSongKind.textContent = "Artist";
    elSongTitle.textContent = queryTitle || "Shared Artist";
    ytmFallbackUrl = `${YTM_BASE}/channel/${encodeURIComponent(channelId)}`;
    pulseSchemeUrl = `pulse://channel/${encodeURIComponent(channelId)}`;
    androidIntentUrl = `intent://channel/${encodeURIComponent(channelId)}#Intent;scheme=pulse;package=com.techfisher.pulsestream;end`;
  } else {
    elSongKind.textContent = "Pulse Music";
    elSongTitle.textContent = "Play on Pulse";
    elSongArtist.textContent = "Ad-free, open source music streamer";
  }

  // Configure action buttons
  elBtnDownload.href = APK_DOWNLOAD_URL;
  elBtnWeb.href = ytmFallbackUrl;

  elBtnOpen.addEventListener("click", function (e) {
    e.preventDefault();
    attemptOpenApp();
  });

  elBtnDownload.addEventListener("click", function () {
    showDownloadingState();
  });

  // Track if user switched away to the app
  let appOpened = false;
  function markAppOpened() {
    appOpened = true;
  }
  document.addEventListener("visibilitychange", function () {
    if (document.hidden) markAppOpened();
  });
  window.addEventListener("pagehide", markAppOpened);
  window.addEventListener("blur", markAppOpened);

  function attemptOpenApp() {
    const isAndroid = /Android/i.test(navigator.userAgent);
    if (isAndroid) {
      // Android Intent scheme
      window.location.href = androidIntentUrl;
      // Fallback custom scheme attempt
      setTimeout(function () {
        if (!appOpened) {
          window.location.href = pulseSchemeUrl;
        }
      }, 300);
    } else {
      // Non-Android: try custom scheme
      window.location.href = pulseSchemeUrl;
    }
  }

  function showDownloadingState() {
    if (elStatusSpinner) elStatusSpinner.style.display = "none";
    if (elStatusIcon) {
      elStatusIcon.textContent = "⬇️";
      elStatusIcon.style.display = "inline-block";
    }
    elStatusText.innerHTML = "<strong>Downloading Pulse.apk...</strong> When finished, tap the download in your notifications to install and play.";
  }

  function showNotInstalledState() {
    const elStatusSpinner = document.querySelector(".status-spinner");
    if (elStatusSpinner) elStatusSpinner.style.display = "none";
    if (elStatusIcon) {
      elStatusIcon.textContent = "📦";
      elStatusIcon.style.display = "inline-block";
    }
    elStatusText.innerHTML = "<strong>Pulse isn't installed yet.</strong> Downloading Pulse APK now, or install manually below to listen.";
  }

  // Automatic Flow:
  // 1. Try launching Pulse app
  // 2. If app not opened after 1.2s, automatically initiate APK download
  const isAndroid = /Android/i.test(navigator.userAgent);
  if (isAndroid && (videoId || playlistId || browseId || channelId)) {
    attemptOpenApp();

    setTimeout(function () {
      if (!appOpened) {
        showNotInstalledState();
        // Trigger APK download
        const downloadFrame = document.createElement("iframe");
        downloadFrame.style.display = "none";
        downloadFrame.src = APK_DOWNLOAD_URL;
        document.body.appendChild(downloadFrame);
      }
    }, 1400);
  } else {
    // Desktop or iOS or generic page
    const elStatusSpinner = document.querySelector(".status-spinner");
    if (elStatusSpinner) elStatusSpinner.style.display = "none";
    if (elStatusIcon) {
      elStatusIcon.textContent = isAndroid ? "📱" : "💻";
      elStatusIcon.style.display = "inline-block";
    }
    elStatusText.innerHTML = isAndroid
      ? "<strong>Play in Pulse</strong> · Open with the app or download the APK below."
      : "<strong>Pulse is an Android app.</strong> Download the APK for your phone or listen on YouTube Music.";
  }
})();
