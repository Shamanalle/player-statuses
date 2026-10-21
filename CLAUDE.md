# Notes for Claude
- Commits and pull requests: no `Co-Authored-By` trailer, no `Claude-Session` line, no "Generated with Claude Code" line.
  The author of the project asked not to be listed with Claude as a co-author or contributor. When squash-merging,
  pass an explicit commit message so GitHub does not copy trailers from the branch commits.
- CHANGELOG (and so the release notes on GitHub, Modrinth and CurseForge): only what players and server owners notice,
  a few plain sentences per item: what changed, where the switch is, the command for players without the addon.
  No tests, CI or build internals, no implementation details (algorithms, decibels, thresholds, config keys, sources);
  those belong in the README.
  Small things players barely notice (a hint that fits better, a typo, a layout nudge) get no item of their own:
  one line "Small fixes." / "Мелкие исправления." is enough.
