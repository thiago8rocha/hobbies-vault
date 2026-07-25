# Changelog

All notable changes to this project will be documented in this file.

The format is a simplified version of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/):
- `Additions` - New features
- `Changes` - Behaviour/visual changes
- `Fixes` - Bugfixes
- `Other` - Technical changes/updates

## [Unreleased]

### Additions
- Settings > Plataformas: choose which game platforms show up as filters in Jogos (all enabled by default).
- "Notas" (free-text notes) available from the "..." menu on every detail screen.
- Swipe left/right between the 5 hobby tabs.
- Books: star rating (0-5), a review section, and a Citações (quotes) section.
- Games: Preços section (current ITAD deals) with a real price-history chart (ITAD `games/history/v2`).
- Games: DLCs/Expansões and Recomendações sections, sourced from IGDB.
- Games: Jogatinas section — manual playthrough log (title, dates, hours, notes). Steam/PSN can't be
  auto-synced here since neither API exposes per-session data, only aggregate totals.
- Films: "Adicionar à lista" from the "..." menu (existing list or new).
- Series: toggle to show/hide "Séries relacionadas" from the "..." menu.

### Changes
- Bottom bar reordered to Jogos, Filmes, Séries, Mangás, Livros.
- Overflow ("...") menus now dim the background and open anchored under the top bar, matching Rokku's style.
- All hobby list screens open Settings through a "..." menu instead of a direct shortcut icon.
- Larger, tighter tabs on the hobby list screens.
- Standardized rounded corners on remaining flat-cornered boxes across detail screens.
- Games/Manga "Informações"/"Datas" now render as individual cards instead of one shared box.
- Manga: added "Início da leitura" date, relabeled publication dates, moved Sinônimos to the end of the page.
- Series/Manga/Books status menus now show a checkmark on the current status.
- Series/Manga/Books progress bars: flat ends instead of the rounded "dot" look.
- Books: "Progresso" renamed to "Histórico de Leitura"; publication date relabeled "Publicação desta edição".
- All search fields now have a clear (X) button.
- Films/Series: cast/crew now render as a horizontal avatar list (same pattern as Manga), section order
  changed (Sinopse → Gêneros → Onde Assistir → Elenco → Equipe), streaming options in two columns past 2.
- CI: `gradlew` executable bit and a machine-specific `gradle-daemon-jvm.properties` were breaking every
  build since the initial commit — fixed, unrelated to any app code.

### Fixes
- Steam platform badge text was unreadable (near-black on a dark badge); now white.
