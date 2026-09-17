# AstroFish release signing

`generate-release-key.sh` creates the private release key in the ignored
`release-signing/` directory. Keep both `AstroFish-release.keystore` and
`credentials.env` backed up together. Every future AstroFish update must be
signed with this same key or Android will reject it as an upgrade.

Do not publish either private file. The public certificate exported by
`build-release.sh` may be distributed with the APK.
