# Store pages / Страницы на площадках

**English** · [Русский](#русский)

## English

The projects on Modrinth and CurseForge are created by hand; this file has everything their forms ask for. Once a project exists, put its id into `.github/publish.json` (see CONTRIBUTING, *Modrinth and CurseForge*), and the release workflow uploads the files there.

The store pages themselves come with the first release: `docs/modrinth.md` (uploaded to Modrinth by the *Modrinth page* workflow together with the summary below and the gallery from `docs/gallery.json`), `docs/curseforge.html` and `docs/curseforge-plugin.html` (pasted by hand into CurseForge's description editor through its source view, `<>`). Until then a short description is enough: the first paragraph below.

### Name

**Name:** Player Statuses: Typing, AFK & Open Screen Indicators

**Plugin (CurseForge Bukkit Plugins, Hangar, Spigot):** Player Statuses: Typing & AFK Indicators for Paper

Why: both stores search the name and the summary first, and Modrinth has no free tags, so the words people type have to be there. "Typing indicator", "AFK" and "open screen / inventory" are what players search for when they look for this kind of mod (the most downloaded one, *What Are They Up To*, is not in the title: names of other mods do not belong in ours; the description says plainly that this is an alternative to it). Loaders and Minecraft versions are not allowed in a store title.

**Slug:** `player-statuses` on both sites (the README badges point there).

### Summary

**Summary:** See what other players are up to: typing indicator, inventory or chest open, AFK with a timer, tabbed out. Small icons above heads and in the Tab list, a privacy switch for each status. Fabric, Forge, NeoForge and a Paper plugin.

229 characters: CurseForge allows 250, Modrinth 256. It starts with the plain promise ("what other players are up to", the phrase people know from WATUT), then the search words (typing indicator, inventory, chest, AFK, tabbed out), then what sets it apart (privacy, Tab list) and where it runs.

**First paragraph of the description** (used until the full pages exist):

> See what other players are up to. A small icon above a player's head shows when they are typing, have their inventory or a chest open, are AFK (with a timer) or have tabbed out, and the same shows in the player list. The text you type is never sent, and every kind of status has its own privacy switch. An alternative to What Are They Up To (WATUT) for 1.20.1 – 26.3 on Fabric, Forge and NeoForge, and also for vanilla clients via the server.

### Categories

| | Modrinth | CurseForge (mod) | CurseForge (plugin) |
|---|---|---|---|
| Main | Social | Utility & QoL | Chat Related |
| Additional | Utility, Management | Cosmetic, Server Utility, Map and Information | Informational, Fun |

Modrinth has no "Multiplayer" category; *Social* is where WATUT and similar mods are. *Management* is for the plugin file (server tools).

### Environment and dependencies

- **Modrinth environment:** Client: *required*, Server: *required*. The icons need the mod on the client and the mod or the plugin on the server; either side alone loads fine and does nothing harmful.
- **Dependencies:** Fabric API (required, Fabric file only). Nothing else; Simple Voice Chat is optional and not listed as a dependency.
- **License:** MIT. **Source:** https://github.com/Shamanalle/player-statuses · **Issues:** https://github.com/Shamanalle/player-statuses/issues

### Icon

`icon.png` in the repository root (512×512, pixel art, made by `python3 docs/make-icon.py icon.png common/src/main/resources/assets/playerstatuses/icon.png`). The GitHub social preview is `docs/images/social-preview.png` (1280×640): *Settings → General → Social preview*. Both are rendered again by `NODE_PATH=$(npm root -g) node docs/gallery/make-banners.js` (the README banner too).

### Checklist for creating the projects

1. Modrinth → *Create a project*: type *Mod*, the name and slug above, the summary, the icon, the categories, environment *required / required*, license MIT, links to the source and issues. Copy the *Project ID* into `.github/publish.json` → `"modrinth"`.
2. CurseForge → *Minecraft → Mods → Create project*: the same name, summary, icon and categories; description: the first paragraph above. Copy the *Project ID* number into `"curseforge"`.
3. CurseForge → *Minecraft → Bukkit Plugins → Create project* for the plugin: the plugin name above. Its id goes into `"curseforge_plugin"`.
4. Secrets `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN` and `REPO_ADMIN_TOKEN` with the rights listed in CONTRIBUTING.
5. CurseForge has no API for pictures: the gallery pictures (`docs/images/gallery/`, with the first release) go into each project's *Images* tab by hand, the cover first.

## Русский

Проекты на Modrinth и CurseForge создаются вручную; в этом файле всё, что спрашивают их формы. Когда проект создан, впишите его ID в `.github/publish.json` (см. CONTRIBUTING, *Modrinth и CurseForge*), и workflow релиза будет загружать туда файлы.

Сами страницы площадок появятся с первой версией: `docs/modrinth.md` (его выкладывает на Modrinth workflow *Modrinth page* вместе с кратким описанием ниже и галереей из `docs/gallery.json`), `docs/curseforge.html` и `docs/curseforge-plugin.html` (вставляются вручную в редактор описания CurseForge через исходный код, `<>`). До тех пор хватит короткого описания — первого абзаца ниже.

### Название

**Название:** Player Statuses: Typing, AFK & Open Screen Indicators

**Плагин (CurseForge Bukkit Plugins, Hangar, Spigot):** Player Statuses: Typing & AFK Indicators for Paper

Почему так: обе площадки ищут прежде всего по названию и краткому описанию, а у Modrinth нет свободных тегов, поэтому слова, которые люди набирают, должны быть там. «Typing indicator», «AFK» и «open screen / inventory» — то, что ищут игроки, когда ищут такой мод (самого скачиваемого, *What Are They Up To*, в названии нет: имена чужих модов в наше название не ставим, а в описании честно написано, что это замена ему). Загрузчики и версии Minecraft в названии на площадках запрещены.

**Slug:** `player-statuses` на обеих площадках (на него ведут бейджи README).

### Краткое описание

На площадках только на английском (выше). Перевод: Видно, чем заняты другие игроки: индикатор набора текста, открытый инвентарь или сундук, АФК с таймером, свёрнутая игра. Маленькие иконки над головами и в списке Tab, переключатель приватности для каждого статуса. Fabric, Forge, NeoForge и плагин для Paper.

229 символов: CurseForge допускает 250, Modrinth 256. Сначала простое обещание («what other players are up to» — фраза, которую знают по WATUT), затем слова для поиска (typing indicator, inventory, chest, AFK, tabbed out), затем отличия (приватность, список Tab) и где работает.

**Первый абзац описания** (пока нет полных страниц) — на английском, см. выше.

### Категории

| | Modrinth | CurseForge (мод) | CurseForge (плагин) |
|---|---|---|---|
| Основная | Social | Utility & QoL | Chat Related |
| Дополнительные | Utility, Management | Cosmetic, Server Utility, Map and Information | Informational, Fun |

Категории «Multiplayer» на Modrinth нет; *Social* — там, где WATUT и похожие моды. *Management* — для файла плагина (инструменты сервера).

### Окружение и зависимости

- **Окружение на Modrinth:** Client: *required*, Server: *required*. Для иконок нужен мод на клиенте и мод или плагин на сервере; каждая сторона и в одиночку нормально загружается и ничего не ломает.
- **Зависимости:** Fabric API (обязательно, только для файла Fabric). Больше ничего; Simple Voice Chat не обязателен и в зависимостях не указывается.
- **Лицензия:** MIT. **Исходники:** https://github.com/Shamanalle/player-statuses · **Issues:** https://github.com/Shamanalle/player-statuses/issues

### Иконка

`icon.png` в корне репозитория (512×512, пиксель-арт, делается командой `python3 docs/make-icon.py icon.png common/src/main/resources/assets/playerstatuses/icon.png`). Превью репозитория — `docs/images/social-preview.png` (1280×640): *Settings → General → Social preview*. Обе картинки (и баннер README) заново рендерит `NODE_PATH=$(npm root -g) node docs/gallery/make-banners.js`.

### Чек-лист создания проектов

1. Modrinth → *Create a project*: тип *Mod*, название и slug выше, краткое описание, иконка, категории, окружение *required / required*, лицензия MIT, ссылки на исходники и issues. *Project ID* — в `.github/publish.json` → `"modrinth"`.
2. CurseForge → *Minecraft → Mods → Create project*: то же название, краткое описание, иконка и категории; описание — первый абзац выше. Число *Project ID* — в `"curseforge"`.
3. CurseForge → *Minecraft → Bukkit Plugins → Create project* для плагина: название плагина выше. Его ID — в `"curseforge_plugin"`.
4. Секреты `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN` и `REPO_ADMIN_TOKEN` с правами из CONTRIBUTING.
5. У CurseForge нет API для картинок: картинки галереи (`docs/images/gallery/`, с первой версией) загружаются вручную во вкладку *Images* каждого проекта, обложка первой.
