> [!IMPORTANT]
> Looking to report an issue/bug or make a feature request? Please refer to the [README file](https://github.com/thiago8rocha/hobbies-vault#contributing).

---

Thanks for your interest in contributing to HobbiesVault!

# Code contributions

Pull requests are welcome!

If you're interested in taking on [an open issue](https://github.com/thiago8rocha/hobbies-vault/issues),
please comment on it so others are aware. You do not need to ask for permission nor an assignment.

## Prerequisites

Before you start, please note that the ability to use the following technologies is **required**
and that existing contributors will not actively teach them to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/) and [Jetpack Compose](https://developer.android.com/jetpack/compose)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled to test changes.

## Branch strategy

- **`main`** is the stable branch. It only receives reviewed, working code (via pull request) and
  is what release/beta builds are cut from.
- **`nightly`** is the active development/testing branch. This is where day-to-day work lands
  first; every push to it produces an automatic nightly prerelease (see
  [Releases](#releases) below) so changes can be tried on a real device before they reach `main`.

If you're opening a PR with work in progress or something you'd like tested before it's
considered stable, target `nightly`. Bug fixes and polished features are welcome directly against
`main`.

## Commit messages

This project follows [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) —
it's the most widely adopted convention in the industry and keeps history, diffs and (eventually)
changelog generation easy to scan.

```
<type>(<scope>): <short summary>

[optional body]

[optional footer(s)]
```

- **type** — one of:
  | Type | Use for |
  |---|---|
  | `feat` | a new user-facing feature |
  | `fix` | a bug fix |
  | `docs` | documentation only (README, CONTRIBUTING, comments, CLAUDE.md...) |
  | `style` | formatting only, no code behavior change (whitespace, imports order...) |
  | `refactor` | code change that neither fixes a bug nor adds a feature |
  | `perf` | a change that improves performance |
  | `test` | adding or fixing tests |
  | `build` | build system or dependency changes (Gradle, version catalog...) |
  | `ci` | CI/workflow changes (`.github/workflows`) |
  | `chore` | anything else that doesn't fit above (repo maintenance, tooling...) |
  | `revert` | reverts a previous commit |
- **scope** *(optional)* — the affected area in parentheses, e.g. `feat(games)`,
  `fix(stats)`, `chore(deps)`.
- **short summary** — imperative mood ("add", not "added"/"adds"), no trailing period, ideally
  under ~72 characters.
- **body** *(optional)* — the *why*, not the *what* (the diff already shows what changed).
- **footer** *(optional)* — issue references (`Closes #12`) or a `BREAKING CHANGE:` note when a
  change to the local database schema/migration path requires special attention.

Examples:

```
feat(manga): sync reading progress from AniList
fix(stats): correct rating distribution for items with no rating
docs: document secrets.json setup in README
chore(deps): bump Room to 2.7.0
```

## Pull requests

See the PR template for the checklist. Keep PRs focused — one logical change per PR makes review
(and revert, if needed) much easier.

## Releases

Releases (stable, beta and nightly) are cut by the `Build app` GitHub Actions workflow. Version
numbers follow [SemVer](https://semver.org/) (`MAJOR.MINOR.PATCH`); nightly builds are tagged
`rN`, where `N` is the total commit count at build time. See
[`CHANGELOG.md`](../CHANGELOG.md) for what belongs in the `Unreleased`/versioned sections that
feed each release's notes.
