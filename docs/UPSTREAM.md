# Syncing with upstream RikkaHub

PocketTavern-Lite tracks [RikkaHub](https://github.com/rikkahub/rikkahub) and starts from tag **3.0.0** (`55a108a4477fa308d6eacbe22ebf2fb0ceea1420`). The full upstream history is in this repository so later tags can be merged.

The `upstream` remote is not stored in git config that gets cloned. Add it once:

```bash
git remote add upstream https://github.com/rikkahub/rikkahub.git
git fetch upstream --tags
```

To bring in a newer RikkaHub tag (example: `3.1.0`):

```bash
git fetch upstream tag 3.1.0
git checkout main
git merge 3.1.0
```

Resolve conflicts in favor of PocketTavern-Lite behavior where the two trees intentionally diverge (application id, optional Firebase, SillyTavern prompt assembly). Keep Room schema changes additive: new tables and columns go in a new migration on top of upstream's latest version, and do not renumber or rewrite migrations that already shipped.

SillyTavern-specific work was ported from [rikkahub-sillytavern-android](https://github.com/MiaoWuNYA/rikkahub-sillytavern-android) and adapted to the 3.0.0 generation pipeline (`ChatRequestFactory`). Do not merge that fork wholesale; it carries unrelated features this project does not want.
