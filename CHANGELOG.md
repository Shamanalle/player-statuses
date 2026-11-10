# Changelog

All notable changes to this project are documented in this file. Each version is written in English
first, then in Russian. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project uses [Semantic Versioning](https://semver.org/).

Все заметные изменения проекта записываются в этот файл. Каждая версия описана сначала на английском,
затем на русском.

## [1.0.0] - 2026-11-10

### English

#### Added
- Universal release for Fabric and Quilt (Minecraft 1.16.5 – 26.3), Forge (1.16.5, 1.17.1 – 26.3) and NeoForge (1.20.1 – 26.3), plus a server plugin for Paper, Purpur, Folia and Spigot (1.16.5 – 26.3).
- Crisp dark-slate status badges above players' heads and non-destructive status tags in the Tab player list for typing in chat or entering a command, open inventory, crafting and work blocks, containers, reading books, signs and maps, pause and settings menus, other mods' screens, dimension loading, AFK with an elapsed timer, and an unfocused game window.
- Subtle 3D arm poses while typing or using an open screen, optional Simple Voice Chat indicators for muted microphone and disabled sound, and an unbound-by-default settings shortcut (`Controls -> Key Binds -> Multiplayer`).
- Redesigned in-game client settings screen with a live preview card (overhead badge + Tab list preview), `General`, `Privacy` and `Visibility` tabs, bulk `Enable all` / `Disable all` buttons, tooltips and one-click `Reset` to defaults.
- Server-side AFK and open-container detection for players without the mod (respecting vanished players, spectators, riding entities and team nametag visibility), plus localized status display for vanilla clients above heads on 1.19.4+ (`TextDisplay`) and in the Tab list on 1.16.5 – 26.3 across 13 languages.
- `/playerstatuses` to inspect version, modded player count, server settings and your own status, `/playerstatuses reload` to reload the server configuration, and `/afk` to toggle manual AFK.

### Русский

#### Добавлено
- Универсальный релиз для Fabric и Quilt (Minecraft 1.16.5 – 26.3), Forge (1.16.5, 1.17.1 – 26.3) и NeoForge (1.20.1 – 26.3), а также серверный плагин для Paper, Purpur, Folia и Spigot (1.16.5 – 26.3).
- Контрастные тёмно-графитовые значки статусов над головами игроков и аккуратные метки в списке игроков (Tab) с сохранением цветов и префиксов команд: набор сообщения или команды в чате, открытый инвентарь, верстак и рабочие блоки, сундуки и контейнеры, чтение книги, таблички или карты, меню паузы и настроек, экраны других модов, переход между измерениями, АФК с таймером и свёрнутое окно игры.
- Анимация поз рук при наборе текста и открытых экранах, интеграция с Simple Voice Chat (иконки выключенного микрофона и звука) и опциональная клавиша открытия настроек в разделе `Сетевая игра` (по умолчанию не назначена).
- Обновлённый внутриигровой экран настроек клиента с карточкой живого предпросмотра (плашка над головой и строка в Tab), вкладками `Общие`, `Приватность` и `Видимость`, кнопками `Включить все` / `Выключить все`, всплывающими подсказками и кнопкой `Сброс`.
- Серверное определение АФК и открытых контейнеров у игроков без мода (с учётом ваниша, режима наблюдателя, транспорта и видимости ников команд), а также локализованный показ статусов ванильным клиентам над головами на 1.19.4+ (`TextDisplay`) и в списке Tab на 1.16.5 – 26.3 на 13 языках.
- Команды `/playerstatuses` (версия, число игроков с модом, настройки сервера и ваш статус), `/playerstatuses reload` (перезагрузка конфигурации сервера) и `/afk` (ручной режим АФК до первого движения).
