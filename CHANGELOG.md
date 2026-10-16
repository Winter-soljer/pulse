# Changelog

What changed in each release. Older releases are described on the [Releases page](https://github.com/Yigder/Prism/releases).

## Unreleased

### Fixed
- **Discord sign-in stayed on Discord's page after signing in.** Prism now picks up the session from Discord's own sign-in answer (password, two-factor or QR code) and its requests, checks on every page rather than only once the address changes, and finds Discord's session wherever its current code keeps it.

## 1.6.9.1

### Fixed
- **Discord sign-in showed a blank page** when Discord's page couldn't load (no connection, or a network that blocks Discord, as many work and school ones do). It now says it couldn't reach Discord, with Try again.
- **Discord sign-in** looks like the phone's browser to Discord (Android's in-app browser marked itself), and reads the sign-in a second way if the first doesn't find it.

## 1.6.9

### New
- **More than one account**: Settings → "Switch account" lists every YouTube channel you can use, including several channels (brand accounts) under one email, and "Add a Google account" signs another one in beside it. Switching brings that channel's library, likes and recommendations; Replay and this phone's history stay.
- **Discord** (Settings → Discord): "Listening to Prism" on your Discord profile, with the song, artist and how far in, cleared when you pause. Off until you sign in to Discord on its own page; the sign-in stays on this phone and never goes in a backup. Discord has no official way for Android apps to do this, so it works the way other Android music players do, which Discord's terms don't allow for third-party apps: there's a small risk to your Discord account.
- **Cupertino icons** (Settings → Appearance → Icons → Pack): the most-seen icons redrawn in the style of Apple's apps (filled tabs, a chevron for Back, two-triangle skips, circled menus and downloads).
- **Remove from library** in an album's long-press menu.

### Fixed
- **Lyrics stuck on the last line** from the start of a song ("4 Steps Back, 2 Steps Forward" by Motion Sick): one source sent plain lyrics marked as synced, every line at 0:00. Those now count as plain, so a source with real timings wins.
- **Quick picks popping in and out**, and Home showing the same songs twice: Quick picks and Speed dial no longer reload each time they scroll off screen, a refresh without Quick picks keeps the last ones, and a section already shown isn't repeated.
- **Slow starts and buffering since 1.6.8**: saving playlists, albums and covers for offline, and retrying failed downloads, now wait while music is loading, and do less at a time.
- **A crash** when Prism started in the background with failed downloads waiting (Android 12 and newer).
- **Albums that wouldn't leave the library** when YouTube opened them as another edition (a remaster or deluxe).

## 1.6.8

### New
- **Speed dial** on Home (Customize Home → Add a section), as on YouTube Music's app: your go-to songs, albums, playlists and artists as big tiles, nine to a page. YouTube's own Speed dial (or Listen again) leads, filled out with what you play most. It goes in at the top (#53).
- **Quick picks start a radio**: tapping a pick plays it and then songs like it, as on YouTube Music. Customize Home → Options turns it back to playing Quick picks in order (#38).
- **Search your own music**: Search has YouTube Music and **On this phone**, which finds your music files and downloads by title, artist or album. Offline, Search starts there (#46).
- **Offline library**: your playlists, albums and artists are kept as last synced, and playlists and albums are saved with their songs when opened (and on Wi-Fi after a sync), so they open with no connection. Offline, a playlist plays its downloaded songs and shows the rest greyed out (#55).
- **Covers offline**: downloaded songs' covers (a lossless file's own embedded cover first) and your playlists' and albums' are kept on the phone, for the player and its blurred background, the notification, widgets and lists. Local files whose album has no artwork in Android show the cover inside the file (#54, #55).
- **Data saver** (Settings → Playback & sound → Mobile data): on mobile data, pictures come at the size they're shown, artist photos and animated covers wait for Wi-Fi, songs stream at your mobile data quality (at most Normal) and music videos at 480p at most (#40).
- **Songs, not videos** (Settings → Playback, on by default): autoplay and radios play music videos as their songs and leave out videos with no song version (#39).
- **Stop when swiped away** (Settings → Playback): swiping Prism out of your recent apps stops the music (#45).
- **Choose your lyrics sources**: each source in Settings → Lyrics → Sources has a switch and a line on what it's good for; Prism only asks the ones switched on (#51).
- **Save lyrics into your music files** (Settings → Lyrics, or a song's ⋯ menu): synced lyrics go into your FLAC and MP3 files' tags as LRC, so other players show them too (#16).

  > **About #16:** Prism has always left your own music files alone, so this only happens when you ask for it: nothing is written until you tap it in Settings → Lyrics or a song's menu, and on Android 11 and newer Android asks you to allow the change first. Only the lyrics tag is written; the audio and every other tag stay as they were. Each file is copied first and checked before it goes back (if anything goes wrong, even Prism closing mid-way, the original is put back). Prism still never deletes or moves your files.
- **New widgets**: Cover (2×2), Disc (2×2, with a progress ring) and Strip (4×1) (#34).
- **Local files sorting**: a sort sheet with the direction in plain words and more orders (date added, year, length, file size), plus orders for Artists and Albums (#35).
- **Every artist of a song**: a song's menu has "Go to …" for each of its artists, and tapping the artist on the player asks which one (#42).

### Fixed
- **Downloads stuck at a percentage** (#55): failed downloads now try again by themselves at launch and when you're back online, with fresh links; one that stalls restarts from where it got to; and Downloads says when they're waiting for Wi-Fi or failed, with "Use mobile data" and "Try again". Offline, songs that aren't fully on the phone are passed over instead of cutting off mid-song.
- **Music videos with the Song / Video switch off** (#48): a music video picked from a list plays as its song, and nothing switches to video.
- **Headphone volume lag** (#52): the player's volume slider only sends real changes, so Bluetooth headphones no longer fall seconds behind it.
- **Grid** for Quick picks and other song sections shows covers in two rows; it looked the same as List (#44).
- **Artist pages** (#47): the photo's colours carry on down the page and settle into its own tint instead of fading to black (album and playlist pages too). The artist's name moves with the photo as you scroll (#37).
- **The player's title** is a size larger than the artist under it; they were both 20 sp (#41).

## 1.6.7

### New
- **Your own fonts** (Settings → Appearance → Text): pick a .ttf or .otf file from your phone (Inter, SF Pro…) for the whole app, and choose a separate **lyrics font**: the app's, any built-in font, or a file of its own. Variable fonts get all their weights (#12).
- **Icon style** (Settings → Appearance → Icons): Thin, Regular or Bold icons, rounded or square. Applies after a quick restart, from the button that appears (#19).
- **App icons** (Settings → Appearance → App icon): Classic, Spectrum, Solid, Light, Wave and Midnight (#20).
- **Local files, reworked** (#29): Play, Shuffle and **Recently played** at the top, the song and album count under the title, songs sorted by title by default (the order you pick is kept), and new sections: **Favourites** (liked songs from your phone), **Recently added**, **Added albums** and **Downloads**. Tap **Edit** at the end of the bar to choose and order the sections.
- **Hide the Podcasts button** on Home (Settings → Home screen → Podcasts button); Podcasts then opens from Library (#28).

### Fixed
- **Lossless FLAC at double speed with harsh noise** on Samsung phones (#25): Samsung's FLAC decoder mishandled 16-bit (CD-quality) files in lossless mode. They're now decoded at 16-bit, which loses nothing; 24-bit files stay hi-res.
- **Searches showing up in your YouTube search history** (#27): Prism's searches, every keystroke and the lookups it makes for songs, were saved to your YouTube account. Searches no longer go out with your account.
- **Podcasts "Requested entity was not found"** (#18): in countries where YouTube Music has no Podcasts page, Prism shows your followed shows and saved episodes, and points to Search, instead of an error.
- **Phones in other languages** (Korean, Spanish and more): YouTube answers in your phone's language, and Prism misread its words for "Song", "Single", "Album", years ("2026년") and listener counts, which mixed up artists, album types and search ranking. They're understood in 20 languages now. The date on Home follows your language's order. (Prism's own screens are still in English.)

## 1.6.6

### New
- **Hold previous / next to seek** (Settings → Player → Hold to seek, on by default): holding jumps back or forward, again and again while you hold. Pick 1–20 seconds per step, or turn it off. A tap still changes songs (#21).
- **Local files as a tab** (Settings → Layout & navigation → Tabs): put Local files in the bottom bar, and open Prism on it if you like (#17).
- **Song / Video switch** (Settings → Player → On the player): turn it off to keep the player audio only (#22).
- **"Hey Google, play … on Prism"**: Google Assistant and Gemini can now pick Prism as the app to play from. "Play music on Prism" carries on with your last queue (#23).

### Fixed
- **Right-to-left lyrics on the player:** the arrow after an Arabic line now sits on its left, at the end of the line, and points left (#14).
- **Podcasts** showed "Requested entity was not found" for some signed-in accounts. Prism now loads the page as a guest when YouTube refuses it for the account (#18).
- **Closing the player:** swiping it down and pressing Back now slide it away the same way (#26).

## 1.6.5

### New
- **Immersive mode** (the player's ⋯ menu, or Settings → Player): the cover fills the whole screen, its animated version first when it has one, and the lyrics play over its lower part a line at a time, like a lyric video. Everything else hides; tap the screen for a settings button at the bottom (back to the normal player, lyrics timing and source, the song's options), or **hold it** until its ring fills to go straight back to the normal player. Swipe left or right for the next or previous song, and drag down to close the player. The cover can fill the screen, be **zoomed out** (the default) or show **whole**, over a blurred copy of itself (the settings button, or Settings → Player). The lyrics can sit **high**, in the **middle** or **low** (the default) on the screen, or run **over the whole screen**: every line, the one being sung large and bright, the list gliding along by itself.
- **Crossfade** (Settings → Playback & sound, formerly Mix) now works like Spotify's: the ending song fades out while the next one fades in, both at their own speed. **Automatic length** (on by default) works out each crossfade from how the songs end and begin: over a song's own fade-out, over the next song's quiet intro after an abrupt ending, and only briefly between two hard edges. Or set 1–12 seconds yourself. Matching tempo and beats is now an option (DJ-style), off unless you turn it on. Songs already measured are measured once more for their fade-outs and intros.
- **Local files** has its own page (Library → Playlists → Local files), with **Songs**, **Artists** and **Albums**. Artists open to their songs album by album, and albums play in track order.
- **Shuffle and repeat on the player** (Settings → Player → Shuffle and repeat buttons): either side of play, always. Repeat goes all, one, off.
- **Load ahead** (Settings → Playback & sound): besides the next song's first seconds (as before), the next song or the next three can be loaded whole while one plays, so they play without buffering even if the signal drops. On Wi-Fi, or on mobile data too if you allow it (for the next song's start as well).
- **Swipe a song** in an album or playlist (Liked songs, Downloads and Local files too): right to **play it next**, left to **add it to the end of the queue**.
- **Android Auto lyrics: Keep the song title** (Settings → Lyrics → Android Auto, with the Karaoke or Line by line style): the lyrics show only on the cover, so the car's small media card reads as usual and its full player shows the lyric card big.
- **Settings tidied:** the Player page has separate Artwork, Immersive mode and Animated covers groups, and "On the player" lists things top to bottom as they sit on the player ("Hide volume bar" is now "Volume bar"). Lyrics sources and how they're picked share one group. The same bar is called the progress bar everywhere.

### Fixed
- **The player sits higher again,** as before 1.6.4: the cover and the play, back and forward buttons had moved down. The lyrics, Song/Video and queue buttons along the bottom stay where they are, and the title still sits close to the progress bar.
- **Quick picks** showed your recent plays again: YouTube's "Listen again" / "Speed dial" shelf was taken for them. When YouTube has no picks, songs like your latest play stand in, never the songs you just played.
- **Animated covers:** after saving one song's animated cover, the album's other songs showed their still cover. Looking up covers for saved albums and downloads no longer holds up the cover of the song that's playing.
- **Songs start faster with Crossfade or Normalize volume on.** Measuring a song (which can mean fetching and decoding all of it) ran just as a song started, slowing its loading. Now the next three songs in the queue are measured (and their loudness looked up) while the current one plays, once it's under way, at low priority.
- **Normalize volume no longer jumps a few seconds into a song.** A song's volume is set as it starts; a loudness figure that only arrives later is kept for next time instead of changing the volume mid-song. Queued songs have theirs ready before they play.
- **Animated covers keep playing** while the ⋯ menu or another sheet is open over the player, instead of switching to the still cover.
- **Shuffle:** Previous went to a song that hadn't played yet. It now goes back to the song played last, including after songs are added to the queue or a song is picked from it, and Play next plays next while shuffled too.

## 1.6.4

### New
- **Cover size** (Settings → Player → Artwork): Standard, **Large** (narrow side margins, like YouTube Music) or **Full width** (the square cover runs edge to edge). With Large or Full width, a hidden volume bar or output device gives its room to the cover (#10).
- **Stream quality** (Settings → Player, where "Stats for nerds" was) shows or hides the codec and bitrate (or "Downloaded") under the progress bar. The stats line over the cover is gone; tap the label, or use the ⋯ menu, for the full stats (#8).
- **Animated covers are saved with downloads**, so downloaded songs keep their moving cover offline. "Save with downloads on mobile data" decides whether that waits for Wi-Fi. An album's songs share one saved copy when they use the album's cover.
- **Quick picks snap** a page of rows at a time when swiped, like YouTube Music (#11).

### Fixed
- **Arabic and other right-to-left lyrics** sit on the right and read right to left, and word-by-word lyrics light up from the right (#7). The line on the player is right-aligned too.
- **Time-synced lyrics from YouTube Music** (LyricFind) are used when a song has them, so songs that only had plain lyrics there now scroll in time, Arabic songs among them (#6).
- **With "Current lyric" off,** the player no longer keeps a blank line for it, and the title sits just above the controls on tall screens (#9).
- **Quick picks** showed your recent plays instead of YouTube's picks when YouTube was in another language (#11).
- **Android Auto's queue** (and the watch's and notification's) follows the shuffled order you hear, and tapping a song in it plays that song.

## 1.6.3

### New
- **Your music files.** Library → **On this phone** lists the music on your phone (MP3, M4A, FLAC, OGG, WAV…) with its own tags and album art, sorted by date added, title, artist or album, with Play and Shuffle. Songs play from the file, with normalization, EQ, Mix, lyrics and Replay. Prism asks once for access and only reads the files.
- **Smart Downloads is one storage space.** It now covers both the songs you stream (the old song cache) and the songs it downloads ahead, under one size (your old two sizes added together). When it's full, the songs you're least likely to play again make room, and liked songs are kept longest. **Smart Downloads on mobile data** lets it download without Wi-Fi, and **Clear Smart Downloads** empties it (songs you downloaded yourself stay).
- **Prism's own icons** everywhere: minimal, rounded line icons in place of Google's.
- **Search:** better top results. Abbreviations work ("adtr" finds A Day to Remember, also "rhcp", "mcr"), YouTube's own top pick is used unless it's a cover or remix you didn't ask for, a song wins over its music video, and suggestions start with your artists and recent searches.
- **Replay** lists your top 100 songs.
- Settings → About → tapping the version opens Prism's GitHub page.

### Fixed
- **Normalize volume** really evens songs out now: each song's loudness is measured on your phone (LUFS, as Spotify and YouTube use) and brought to the same level. YouTube's own figure, used before, hardly differed between loud and quiet songs. The next song's level is ready before it starts. Stats for nerds shows the measured loudness.
- **Mix** blended into silence, or not at all, on songs whose music ends before the file does (a fade-out or a silent tail): blends are now timed from where the music ends, the next song comes straight in if the ending one goes quiet, silence at the start of the next song is skipped, and a slow-loading ending gets more time. "Measure songs on mobile data" (Settings → Playback & sound → Sound) covers Mix and normalization; songs already on your phone are measured without data.
- **Scrubbing** after changing songs could jump too far (it used the first song's length).
- **The player's ⋯ menu** stuck or stuttered when pulled down on songs with animated covers.
- **"Alright" (Kendrick Lamar)** and other long explicit songs froze the player for a moment as they opened, while starred-out words were filled in; songs with long music videos also opened sluggishly.
- **Music videos with a long intro** (like "Alright") have their lyrics lined up again.
- **After a podcast,** music plays as the song again, not its video.
- **Live streams** load again when signed in.
- **Home's frosted background** follows the song that's playing (#3).
- **Search:** the search box has its background back, and a top result's play button turns into pause (#5).
- **Replay:** genre names are readable with a white accent in light mode.

## 1.6.2

Everything since 1.6.0 (1.6.1 was tagged but never released).

### New
- **Podcasts.** The round podcasts button at the top of Home, beside Customize, opens them.
  - **Customize button at the top** (Settings → Home screen) can be turned off; Podcasts then takes its place.
  - **Continue listening:** episodes you've started pick up where you left off.
  - **Your shows** and **Episodes for later** from your YouTube Music account, when signed in.
  - **Popular shows and episodes**, by category.
  - **Show pages:** newest episodes first, more as you go, save to library and share.
  - **Search:** new Podcasts and Episodes filters.
  - **Speed:** 0.75× to 2× from the player's ⋯ menu, for episodes only.
  - Episodes don't look up lyrics, don't blend with Mix, aren't followed by music radio and don't count in Replay.
- **Home screen widgets**, in four looks: Prism (frosted cover), Spotify style, Apple Music style and YouTube Music style. Each shows the cover, title, progress, previous/play/next and like; tapping the cover opens the player.
- **Content country** (Settings → Home screen → Region). Where YouTube Music isn't offered, pick a country where it is to get Home, charts and search back. Songs a label blocks in your country still won't play.
- **Mix on mobile data.** "Measure tempo on mobile data" lets beat matching work away from Wi-Fi (about 1–4 MB per song).

### Fixed
- **Pixel Watch:** previous and next sit beside play again, instead of like and shuffle.
- **Lyric timing:**
  - lyric files that shift their own timing (an `[offset]` line) were read without the shift, so every line was early or late;
  - some sources used an extended or radio edit's lyrics, which ran early or late; versions more than 8 seconds off are now turned down, and lyrics saved from those sources are checked again once;
  - while paused, lyrics no longer creep ahead and jump back.
- **Karaoke lyrics first.** Lyrics that light up word by word are now called **Karaoke** (Lyrics source shows "Karaoke", "Time-synced lines" or "Plain text"). They win over time-synced lines even when a source earlier in your order only has lines, and songs whose time-synced lyrics were saved before look for karaoke again.
- **Uncensored lyrics:** words starred out whole (`****`, as Apple Music's lyrics do) are filled in now, as in Childish Gambino's "IV. Sweatpants". Songs with a guest in the title find their full words again, and YouTube Music's or LRCLIB's lyrics fill in when Genius doesn't have the song.
- **Menus:** swiping a menu down (like the player's ⋯) could stutter, or pop back up after being flung away.
- **Player:** an interrupted swipe down could leave the player stuck part of the way down.

## 1.6.0

### New
- **Mix.** Songs blend into each other instead of stopping and starting (Settings → Playback & sound → Mix, off by default).
  - **Blend length:** 2 to 12 seconds.
  - **Match tempo and beats:** Prism measures each song's tempo on the phone, on Wi-Fi. The next song comes in on the beat and plays at the outgoing song's tempo while they overlap (pitch kept), then eases back to its own.
  - **No blend for:** an album's own tracks, live streams, music videos and Listen Together. These still play as before.
- **Translated lyrics.** A translation can show under each lyric line when a song is in another language.
  - **Where:** Settings → Lyrics, or the player's ⋯ menu.
  - **Language:** your phone's language by default, or one of 20 others.
  - **Privacy:** this is a machine translation, and the lyrics' text is sent to Google Translate.
- **"Next in queue".** Songs you add with Play next or Add to queue get their own heading in the queue. Add to queue now puts songs before autoplay's suggestions instead of after them.

### Faster
- **Songs start sooner.** Prism gets the likeliest next song ready ahead of time and, on Wi-Fi, saves its first few seconds. This covers:
  - the top search result;
  - the first song of an open album or playlist;
  - the next song in the queue, including after the queue changes or shuffle is turned on;
  - the song in the mini player when the app opens, and the one after it.
- **Results:** a song that was got ready starts in about half a second instead of over two seconds.

### Fixed
- **Back gesture animation:** the phone's back button or gesture now fades between pages like the back arrow does, instead of shrinking the page like a card.
- **Plain error messages:**
  - "You're offline", instead of technical text;
  - searching with no connection says so, instead of "Search failed";
  - a clear message when YouTube is limiting playback on the network.
- **Refused requests after signing in:** YouTube refused some requests ("Request contains an invalid argument") for some accounts and regions, such as brand accounts and es-419. Prism now retries them in a plainer form.
- **Blocked networks:** when YouTube blocks anonymous playback on a network, signed-in playback goes straight to the account's own clients for a while, instead of failing on each song first.
- **Bitrate:** the player shows each song's real average bitrate. It used to show YouTube's nominal figure (Opus always said 160 kbps).
- **Album tracks:** signed out, some albums' tracks opened as music videos. They now play as songs.
- **Switching between song and video** no longer counts as a new play in history and Replay.
- **Lyrics and queue scrolling:** the player controls no longer flicker in and out while you scroll lyrics or the queue (seen on 120 Hz phones).
- **Home:** a stale "Quick picks" row no longer flashes up while Home loads.
- **"See all" pages:** mixes and playlists now show as a two-column grid of covers, not one sideways row.
- **Fonts:** Google Sans and Rounded are greyed out, with a note, on phones that don't have Google Sans Flex. Picking them there used to do nothing.
- **Counts:** they read "1 song" and "1 artist", not "1 songs".
