# Releasing

Releases are handled by [`sbt-ci-release`](https://github.com/olafurpg/sbt-ci-release) and [Github Actions](../.github/workflows/release.yml). To release a new version do the following:

1. Go [here](https://github.com/Iterable/sbt-codeartifact/releases/new)
2. In the `Choose a tag` dropdown, create a new tag with the following format `v[0-9]+.[0-9]+.[0-9]`. For example `v0.0.1` is a valid release number but `0.0.1` and `0.1` are not.
3. Make sure the `Target` branch is set correctly.
4. Select the correct `Previous tag`.
5. Click the `Generate release notes` button.
6. Click the `Publish release` button.

Once this is done it will trigger a github action that will publish this version to maven central.
