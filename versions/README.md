# Minecraft versions

One folder per loader and band of Minecraft versions: `versions/<loader>/<band>`. Its Gradle project is `:<loader>-<band>`, so `versions/fabric/1.21.11` is `:fabric-1.21.11` (`./gradlew :fabric-1.21.11:runClient`). Which Minecraft versions a band covers is in `gradle.properties` and in the band's `build.gradle`.

The bands are not released one by one: the root `build.gradle` merges every Fabric band into the Fabric / Quilt jar and every Forge and NeoForge band into the Forge / NeoForge jar. Most of the code is in `common/` and `shared/`; a folder here holds only what differs on its versions. [CONTRIBUTING.md](../CONTRIBUTING.md#where-code-lives) says what each one has.

---

*Одна папка на загрузчик и диапазон версий Minecraft: `versions/<загрузчик>/<диапазон>`. Её проект Gradle — `:<загрузчик>-<диапазон>`, так что `versions/fabric/1.21.11` — это `:fabric-1.21.11` (`./gradlew :fabric-1.21.11:runClient`). Какие версии Minecraft входят в диапазон, написано в `gradle.properties` и в `build.gradle` диапазона.*

*По отдельности диапазоны не выпускаются: корневой `build.gradle` собирает все диапазоны Fabric в JAR для Fabric / Quilt, а все диапазоны Forge и NeoForge — в JAR для Forge / NeoForge. Основной код — в `common/` и `shared/`; в папке здесь только то, что отличается на её версиях. Что в какой — в [CONTRIBUTING.md](../CONTRIBUTING.md#где-какой-код).*
