# Contributing / Участие в разработке

**[English](#english)** · **[Русский](#русский)**

---

## English

### Setup

- **JDK 25**: it runs Gradle and builds every module (the older bands are compiled with `--release 17` / `21`).
- **JDK 17** for the Java 8 build of the common code (`common-j8`, see `gradle/jabel.gradle`): Gradle finds it through `JAVA_HOME_17_X64` (GitHub's runners set it) or downloads it.
- Git.

```bash
git clone https://github.com/Shamanalle/player-statuses.git
cd player-statuses
./gradlew :common:test                 # fast: statuses, network messages, relay, settings, commands, translations
./gradlew build                        # every target; the release jars land in build/libs/
./gradlew :fabric-1.21.11:runClient    # or :fabric-26, :neoforge-1.21.1, ...; runServer for a server
./gradlew :bukkit:build                # the Paper / Purpur / Folia / Spigot plugin only
```

### Where code lives

| Directory | Compiled into | Contains |
|---|---|---|
| `common/` | every jar (no Minecraft classes; Java 17 sources, built a second time as Java 8 bytecode by `common-j8`) | the status model (`Activity`, `StatusFlag`, `PlayerStatus`), the network messages (`StatusMessages`), the server relay with its rate limit (`StatusRelay`), idle detection (`IdleTracker`), the client's send limiter (`SendLimiter`), settings files (`SettingsFile`, `ClientSettings`, `ServerSettings`), other mods' screens (`ScreenRules`), the commands (`StatusCommands`), server texts (`ServerText`), **translations and the icon** |
| `shared/mc-all/` | every Fabric, Forge and NeoForge band | the server half on top of Minecraft: `/playerstatuses` and `/afk` (`StatusCommand`), the server hooks (`ServerSide`), `AdminPermission`, `PlayerLanguage` |
| `shared/mc-1.20.1/`, `shared/mc-1.21.11-up/` | the bands of those versions | the few classes that differ there (a band's own copy of a file replaces the shared one with the same path) |
| `shared/fabric/`, `shared/neoforge/`, `shared/forge-bus7/` | the bands of that loader | the loader's entrypoint and hooks; `shared/fabric` also has the `fabric.mod.json` template |
| `shared/gametest/` | `fabric-1.21.11`, `fabric-26` (client gametest) | opens a world in a real client, runs the commands in English and Russian, takes screenshots |
| `versions/<loader>/<band>/` | one band of the universal jar each; the Gradle project is `:<loader>-<band>` | only what differs on those versions, and the band's `build.gradle` (see `gradle/fabric-band.gradle`, `neoforge-band.gradle`, `forge-band.gradle`). Forge 1.20.1 runs with Forge's SRG names, so that band is remapped by `reobfJar` |
| `universal/`, `packaging/` | the two universal jars | the dispatchers that pick the band of the running Minecraft at startup (Java 8, loader API only), and `MergeJars`, which builds one jar out of the band jars and checks that nothing in it is newer than its loaders can read |
| `bukkit/` | own jar (Java 8 bytecode, 1.16.5 API) | the Paper / Purpur / Folia / Spigot / Bukkit plugin: the server side only. Folia's schedulers are only in `FoliaScheduling` (source set `folia`), loaded only on Folia |

Rules of thumb:
- Logic that does not need Minecraft goes into `common` and gets a unit test. `common` must also compile as Java 8 (no `List.of`, `String.isBlank` and the like; records need `@Desugar`).
- One band is compiled against one Minecraft version and runs on several. Before using a Minecraft method in a band, check that it has the same signature on every version of the band (for example with the Yarn or Mojang mappings of each). If not, use one that does, a band file of its own, or reflection. Methods that already broke in other mods of this author: `GuiGraphics.drawString` (return type, 1.21.6), the `KeyMapping` constructor (1.21.9, 1.21.11), `Camera.getPosition()` (1.21.11), `Entity.level()` and `Entity.position()` (1.21.9), `ServerLevel.getEntity(UUID)`, `CommandSourceStack.hasPermission(int)` (gone in 1.21.11, hence `shared/mc-1.21.11-up`). In 1.21.11 Mojang's names renamed `ResourceLocation` to `Identifier`.
- Everything drawn goes through the mod's own drawing layer, not straight to Minecraft's: 1.21.2 moved entity rendering to render states, 1.21.9 to submit nodes, and 26.x draws screens through a render-state API.
- The plugin uses only the Bukkit API (no Paper-only or NMS classes). Its channel and bytes are the mod's (`StatusMessages`), so clients with the mod work with it as with a modded server.
- Either side works without the other: a client never sends anything to a server without the mod or the plugin, and a server never sends the mod's messages to a client without it.

### Translations

Language files are in `common/src/main/resources/assets/playerstatuses/lang/`. Add every new key to **all** files. `LangConsistencyTest` fails if a key used in code is missing, if the languages have different keys, or if their placeholders differ. The server answers each player in their game language from the same files (`ServerText`).

### Documentation

The README is two files: `README.md` in English and `README.ru.md` in Russian. They have the same sections and are changed together. The rest of the documentation (CHANGELOG, this file, issue and pull request templates) is written in English first, followed by a complete Russian translation. The README is a reference for players and server owners: what the mod does, how to install it, commands and every setting, without internals.

GitHub release notes are generated from `CHANGELOG.md` by `.github/scripts/release-notes.sh`. When the changelog changes on `main`, the *Sync release notes* workflow updates the notes of already published releases (and of their Modrinth versions).

The repository description and topics live in `.github/about.json`. The *Repository about* workflow validates the file in pull requests and applies it when it changes on `main`. Applying needs the `REPO_ADMIN_TOKEN` secret, because the built-in workflow token cannot change repository settings. It is set up once:
1. GitHub → *Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token*.
2. *Repository access*: *Only select repositories* → this repository. *Permissions → Repository permissions → Administration*: *Read and write*. Nothing else is needed.
3. In this repository: *Settings → Secrets and variables → Actions → New repository secret*, name `REPO_ADMIN_TOKEN`, value: the token.
4. Run *Actions → Repository about → Run workflow* once, or change `.github/about.json`. When the token expires, generate a new one and update the secret.

### In-game tests

*In-game tests* (`.github/workflows/ingame.yml`) runs on every pull request and before every release:
- **Servers:** `.github/scripts/server-smoke.sh` starts Fabric, Forge, NeoForge, Paper and Folia servers with the mod or the plugin (the oldest loader of each band too), runs `/playerstatuses`, a misspelled subcommand, `reload` and `/afk` from the console, and fails on bare text keys, a missing answer, a missing settings file, or an exception from the mod. Locally: `bash .github/scripts/server-smoke.sh fabric 1.21.11 build/libs/player-statuses-fabric-*.jar`.
- **Client:** `./gradlew :fabric-1.21.11:runClientGameTest` (or `:fabric-26`) opens a world in a real client, runs the commands in English and Russian and saves screenshots in `build/run/clientGameTest/screenshots/` of the module. CI runs it under `xvfb-run` with Mesa's software OpenGL and Vulkan; the 1.21.11 client runs on Java 21.
- Logs and screenshots are attached to each run as artifacts.
- **Network failures:** a download that fails on the way (a timeout, a reset connection, HTTP 429 / 5xx) is tried once more and leaves a warning on the run: the build and the client before the game starts (`.github/scripts/net-retry.sh`, the second time with Maven Central through Google's mirror), the server's installer and first start. A failing test, a compile error and a missing file (404) fail at once.
- *Minecraft 26.x compatibility* (`.github/workflows/compat-26.yml`) checks the 26.x jar against every 26.x release of Minecraft, not only the one it is built against.

### Releases

1. Set `mod_version` in `gradle.properties` and add its section to `CHANGELOG.md` (English, then Russian).
2. Merge into `main` and wait for the green build. The release runs the in-game tests again and stops when they fail.
3. *Actions → Publish Release → Run workflow* on `main`, or push the tag `vX.Y.Z`. The workflow builds every jar, creates the tag and the release, and takes the notes from `CHANGELOG.md`.
4. The same run then uploads the files to Modrinth and CurseForge (see below). To upload an existing release again: *Actions → Publish to Modrinth & CurseForge → Run workflow* with its tag.

### Modrinth and CurseForge

The workflow *Publish to Modrinth & CurseForge* (`.github/workflows/publish.yml`) takes the files of a GitHub release and uploads them:
- **Modrinth:** the Fabric / Quilt jar, the Forge / NeoForge jar and the Paper / Purpur / Folia / Spigot plugin.
- **CurseForge:** the two mod jars; the plugin goes to a separate project in CurseForge's Bukkit Plugins section (`curseforge_plugin`).

A store is skipped with a warning (the run stays green) while its project id or token is missing. Setup, once:
1. Create the projects by hand: Modrinth → *Create a project* (type *Mod*); CurseForge → *Minecraft → Mods → Create project*, and for the plugin *Bukkit Plugins → Create project*. Name, summary, categories and icon are in `docs/store-description.md`. Both sites review new projects; the first upload can go into the project while it waits.
2. Put the project ids into `.github/publish.json`: on Modrinth the *Project ID* from the project's menu, on CurseForge the *Project ID* number on the project page.
3. Tokens, as repository secrets (*Settings → Secrets and variables → Actions*). Tokens never go into files, commits or chats.
   - `MODRINTH_TOKEN`: Modrinth → *Settings → Personal access tokens*, scopes *Create versions* and *Read projects* (uploads), *Write versions* (*Sync release notes* updates the changelog of uploaded versions) and *Write projects* (*Modrinth page* uploads the page, the summary and the gallery).
   - `CURSEFORGE_TOKEN`: CurseForge → *Account → API tokens*. One token covers the mod and the plugin project.
   - `REPO_ADMIN_TOKEN`: see *Documentation* above (*Administration: Read and write* on this repository only).

The Modrinth page is `docs/modrinth.md`, uploaded by the *Modrinth page* workflow together with the summary and the gallery (`docs/gallery.json`). The CurseForge page is pasted by hand; `docs/store-description.md` says how.

### Pull requests

1. Branch off `main` (`feature/…`, `fix/…`).
2. Run `./gradlew :common:test` and `./gradlew build`.
3. Describe what changed and how you tested it in game (Minecraft and loader versions; client, server or both).
4. Add an entry to `CHANGELOG.md` under an *Unreleased* section, in English and in Russian.
   The changelog becomes the release notes on Modrinth and CurseForge. Keep it like other mods' changelogs: short plain bullets under Added / Changed / Fixed, about what players and server owners notice. No internals and no advertising tone.

---

## Русский

### Подготовка

- **JDK 25**: на нём работает Gradle и собираются все модули (старые диапазоны компилируются с `--release 17` / `21`).
- **JDK 17** для сборки общего кода в байткод Java 8 (`common-j8`, см. `gradle/jabel.gradle`): Gradle находит его через `JAVA_HOME_17_X64` (на раннерах GitHub она задана) или скачивает сам.
- Git.

```bash
git clone https://github.com/Shamanalle/player-statuses.git
cd player-statuses
./gradlew :common:test                 # быстро: статусы, сетевые сообщения, ретрансляция, настройки, команды, переводы
./gradlew build                        # все версии; JAR для релиза в build/libs/
./gradlew :fabric-1.21.11:runClient    # или :fabric-26, :neoforge-1.21.1, ...; runServer для сервера
./gradlew :bukkit:build                # только плагин для Paper / Purpur / Folia / Spigot
```

### Где какой код

| Папка | Куда попадает | Что внутри |
|---|---|---|
| `common/` | во все JAR (без классов Minecraft; исходники на Java 17, `common-j8` собирает их ещё раз в байткод Java 8) | модель статусов (`Activity`, `StatusFlag`, `PlayerStatus`), сетевые сообщения (`StatusMessages`), ретрансляция на сервере с ограничением частоты (`StatusRelay`), учёт простоя (`IdleTracker`), ограничитель отправки на клиенте (`SendLimiter`), файлы настроек (`SettingsFile`, `ClientSettings`, `ServerSettings`), экраны других модов (`ScreenRules`), команды (`StatusCommands`), тексты сервера (`ServerText`), **переводы и иконка** |
| `shared/mc-all/` | во все диапазоны Fabric, Forge и NeoForge | серверная часть поверх Minecraft: `/playerstatuses` и `/afk` (`StatusCommand`), хуки сервера (`ServerSide`), `AdminPermission`, `PlayerLanguage` |
| `shared/mc-1.20.1/`, `shared/mc-1.21.11-up/` | в диапазоны этих версий | немногие классы, которые там отличаются (своя копия файла в диапазоне заменяет общий файл с тем же путём) |
| `shared/fabric/`, `shared/neoforge/`, `shared/forge-bus7/` | в диапазоны этого загрузчика | точка входа и хуки загрузчика; в `shared/fabric` ещё шаблон `fabric.mod.json` |
| `shared/gametest/` | `fabric-1.21.11`, `fabric-26` (client gametest) | открывает мир в настоящем клиенте, выполняет команды на английском и русском, делает скриншоты |
| `versions/<загрузчик>/<диапазон>/` | по одному диапазону универсального JAR; проект Gradle — `:<загрузчик>-<диапазон>` | только то, что отличается на этих версиях, и `build.gradle` диапазона (см. `gradle/fabric-band.gradle`, `neoforge-band.gradle`, `forge-band.gradle`). Forge 1.20.1 работает с именами SRG, поэтому этот диапазон переименовывает `reobfJar` |
| `universal/`, `packaging/` | в два универсальных JAR | диспетчеры, которые при запуске выбирают диапазон запущенного Minecraft (Java 8, только API загрузчика), и `MergeJars`, который собирает один JAR из JAR диапазонов и проверяет, что в нём нет ничего новее, чем могут прочитать загрузчики |
| `bukkit/` | свой JAR (байткод Java 8, API 1.16.5) | плагин для Paper / Purpur / Folia / Spigot / Bukkit: только серверная часть. Планировщики Folia только в `FoliaScheduling` (набор исходников `folia`), он загружается только на Folia |

Правила:
- Логика, которой не нужен Minecraft, живёт в `common` и покрывается юнит-тестом. `common` должен собираться и как Java 8 (без `List.of`, `String.isBlank` и подобного; record нужна пометка `@Desugar`).
- Диапазон собирается против одной версии Minecraft и работает на нескольких. Прежде чем вызвать в диапазоне метод Minecraft, проверьте, что его сигнатура одинакова во всех версиях диапазона (например, по маппингам Yarn или Mojang каждой). Если нет — возьмите другой метод, свой файл диапазона или рефлексию. Методы, которые уже ломались в других модах автора: `GuiGraphics.drawString` (тип возврата, 1.21.6), конструктор `KeyMapping` (1.21.9, 1.21.11), `Camera.getPosition()` (1.21.11), `Entity.level()` и `Entity.position()` (1.21.9), `ServerLevel.getEntity(UUID)`, `CommandSourceStack.hasPermission(int)` (пропал в 1.21.11, отсюда `shared/mc-1.21.11-up`). В 1.21.11 в именах Mojang `ResourceLocation` переименован в `Identifier`.
- Всё нарисованное идёт через собственную прослойку отрисовки мода, а не напрямую в Minecraft: в 1.21.2 отрисовка сущностей перешла на render state, в 1.21.9 — на submit nodes, а в 26.x экраны рисуются через API render state.
- Плагин использует только API Bukkit (без классов, которые есть только в Paper, и без NMS). Канал и байты у него те же, что у мода (`StatusMessages`), поэтому клиенты с модом работают с ним как с сервером с модом.
- Каждая сторона работает без другой: клиент ничего не отправляет серверу без мода или плагина, а сервер не шлёт сообщения мода клиенту без мода.

### Переводы

Файлы языков лежат в `common/src/main/resources/assets/playerstatuses/lang/`. Каждый новый ключ добавляйте во **все** файлы. `LangConsistencyTest` упадёт, если в коде есть ключ без перевода, если наборы ключей в языках различаются или если не совпадают плейсхолдеры. Сервер отвечает каждому игроку на языке его игры из тех же файлов (`ServerText`).

### Документация

README — это два файла: `README.md` на английском и `README.ru.md` на русском. В них одинаковые разделы, и меняются они вместе. Остальная документация (CHANGELOG, этот файл, шаблоны issue и pull request) пишется сначала на английском, затем идёт полный перевод на русский. README — справочник для игроков и владельцев серверов: что умеет мод, как его поставить, команды и все настройки, без внутреннего устройства.

Описания релизов на GitHub собираются из `CHANGELOG.md` скриптом `.github/scripts/release-notes.sh`. Когда журнал изменений меняется в `main`, workflow *Sync release notes* обновляет описания уже опубликованных релизов (и их версий на Modrinth).

Описание и темы (topics) репозитория лежат в `.github/about.json`. Workflow *Repository about* проверяет этот файл в pull request и применяет его, когда файл меняется в `main`. Для применения нужен секрет `REPO_ADMIN_TOKEN`, потому что встроенный токен workflow не может менять настройки репозитория. Настраивается один раз:
1. GitHub → *Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token*.
2. *Repository access*: *Only select repositories* → этот репозиторий. *Permissions → Repository permissions → Administration*: *Read and write*. Больше ничего не нужно.
3. В этом репозитории: *Settings → Secrets and variables → Actions → New repository secret*, имя `REPO_ADMIN_TOKEN`, значение — токен.
4. Один раз запустите *Actions → Repository about → Run workflow* или измените `.github/about.json`. Когда срок токена истечёт, создайте новый и обновите секрет.

### Проверки в игре

*In-game tests* (`.github/workflows/ingame.yml`) запускается на каждый pull request и перед каждым релизом:
- **Серверы:** `.github/scripts/server-smoke.sh` запускает серверы Fabric, Forge, NeoForge, Paper и Folia с модом или плагином (и самый старый загрузчик каждого диапазона), выполняет из консоли `/playerstatuses`, подкоманду с опечаткой, `reload` и `/afk` и падает, если ответ — голые ключи текстов, нужного ответа нет, файл настроек не появился или мод бросил исключение. Локально: `bash .github/scripts/server-smoke.sh fabric 1.21.11 build/libs/player-statuses-fabric-*.jar`.
- **Клиент:** `./gradlew :fabric-1.21.11:runClientGameTest` (или `:fabric-26`) открывает мир в настоящем клиенте, выполняет команды на английском и русском и сохраняет скриншоты в `build/run/clientGameTest/screenshots/` модуля. В CI тест идёт под `xvfb-run` с программными OpenGL и Vulkan из Mesa; клиент 1.21.11 запускается на Java 21.
- Логи и скриншоты прикладываются к каждому запуску как артефакты.
- **Сбои сети:** загрузка, оборвавшаяся по дороге (тайм-аут, сброс соединения, HTTP 429 / 5xx), повторяется один раз, и в запуске остаётся предупреждение: сборка и клиент до старта игры (`.github/scripts/net-retry.sh`, во второй раз Maven Central берётся с зеркала Google), установщик сервера и его первый запуск. Упавший тест, ошибка компиляции и отсутствующий файл (404) валят задание сразу.
- *Minecraft 26.x compatibility* (`.github/workflows/compat-26.yml`) проверяет JAR для 26.x на каждом релизе 26.x, а не только на том, против которого он собран.

### Релизы

1. Укажите `mod_version` в `gradle.properties` и добавьте раздел этой версии в `CHANGELOG.md` (сначала английский, затем русский).
2. Слейте изменения в `main` и дождитесь зелёной сборки. Релиз ещё раз запускает проверки в игре и останавливается, если они не прошли.
3. *Actions → Publish Release → Run workflow* на `main` или запушьте тег `vX.Y.Z`. Workflow соберёт все JAR, создаст тег и релиз, а описание возьмёт из `CHANGELOG.md`.
4. Затем тот же запуск загрузит файлы на Modrinth и CurseForge (см. ниже). Чтобы загрузить уже вышедший релиз заново: *Actions → Publish to Modrinth & CurseForge → Run workflow* с его тегом.

### Modrinth и CurseForge

Workflow *Publish to Modrinth & CurseForge* (`.github/workflows/publish.yml`) берёт файлы релиза на GitHub и загружает их:
- **Modrinth:** JAR для Fabric / Quilt, JAR для Forge / NeoForge и плагин для Paper / Purpur / Folia / Spigot.
- **CurseForge:** два JAR мода; плагин идёт в отдельный проект в разделе Bukkit Plugins (`curseforge_plugin`).

Площадка пропускается с предупреждением (запуск остаётся зелёным), пока для неё нет ID проекта или токена. Настраивается один раз:
1. Создайте проекты вручную: Modrinth → *Create a project* (тип *Mod*); CurseForge → *Minecraft → Mods → Create project*, а для плагина *Bukkit Plugins → Create project*. Название, краткое описание, категории и иконка — в `docs/store-description.md`. Оба сайта проверяют новые проекты; первую загрузку можно сделать, пока проект ждёт проверки.
2. Впишите ID проектов в `.github/publish.json`: на Modrinth — *Project ID* из меню проекта, на CurseForge — число *Project ID* на странице проекта.
3. Токены — как секреты репозитория (*Settings → Secrets and variables → Actions*). Токены никогда не пишутся в файлы, коммиты и чаты.
   - `MODRINTH_TOKEN`: Modrinth → *Settings → Personal access tokens*, права *Create versions* и *Read projects* (загрузка), *Write versions* (*Sync release notes* обновляет описание загруженных версий) и *Write projects* (*Modrinth page* выкладывает страницу, краткое описание и галерею).
   - `CURSEFORGE_TOKEN`: CurseForge → *Account → API tokens*. Один токен годится и для мода, и для проекта плагина.
   - `REPO_ADMIN_TOKEN`: см. *Документация* выше (*Administration: Read and write* только на этот репозиторий).

Страница Modrinth — `docs/modrinth.md`, её вместе с кратким описанием и галереей (`docs/gallery.json`) выкладывает workflow *Modrinth page*. Страницу CurseForge вставляют вручную; как — написано в `docs/store-description.md`.

### Pull request

1. Создайте ветку от `main` (`feature/…`, `fix/…`).
2. Запустите `./gradlew :common:test` и `./gradlew build`.
3. Опишите, что изменилось и как вы проверяли в игре (версии Minecraft и загрузчика; клиент, сервер или оба).
4. Добавьте запись в `CHANGELOG.md` в раздел *Unreleased* на английском и на русском.
   Журнал изменений становится описанием версии на Modrinth и CurseForge. Пишите его как у других модов: короткие простые пункты в разделах «Добавлено / Изменено / Исправлено», о том, что заметят игроки и владельцы серверов. Без внутреннего устройства и без рекламного тона.
