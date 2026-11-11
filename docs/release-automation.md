# Release automation

LikeBeacon treats the published GitHub Release as the source of truth for release notes and binaries. Publishing a GitHub draft release starts `.github/workflows/publish-marketplaces.yml`, which publishes the same release to Hangar and Modrinth.

## Publication flow

1. Update `version` and, when necessary, the marketplace metadata in `gradle.properties`.
2. Merge the changes into `main` and confirm that CI succeeds.
3. Run the `Release` workflow with the version number without the `v` prefix.
4. Review and edit the generated GitHub draft release.
5. Publish the GitHub release.
6. The marketplace workflow validates the tag and release assets.
7. Hangar and Modrinth publish in independent jobs.

The marketplace workflow does not rebuild the plugin. It downloads the GitHub Release JAR and its `.sha256` file, verifies them, and uploads that exact JAR to Modrinth. Hangar receives the direct GitHub Release Asset URL because the shaded JAR exceeds Hangar's direct upload size limit.

## Required secrets

Add both secrets under **Settings > Secrets and variables > Actions > Repository secrets**.

### `HANGAR_API_TOKEN`

Create a Hangar API key that is restricted to the LikeBeacon project and has only the `create_version` permission. The workflow uses it to create a version whose Paper download points directly to the GitHub Release Asset.

### `MODRINTH_TOKEN`

Create a Modrinth personal access token with the `CREATE_VERSION` scope. `PROJECT_WRITE` is not required because the workflow does not synchronize the project description.

Never put either token in `gradle.properties`, a workflow file, a local Gradle property committed to Git, or a release note.

## Publication metadata

The following values in `gradle.properties` are shared by the marketplace jobs:

```properties
modrinthProjectId=likebeacon
hangarProjectSlug=LikeBeacon
supportedMinecraftVersions=1.21.4,1.21.5,1.21.6,1.21.7,1.21.8,1.21.9,1.21.10,1.21.11,26.3
```

Update `supportedMinecraftVersions` only after the corresponding Paper versions have been tested and the public compatibility documentation has been updated.

The GitHub tag includes the `v` prefix, such as `v1.1.0`. Hangar and Modrinth receive the version without the prefix, such as `1.1.0`. The version must match `version` in the tagged `gradle.properties` file.

## Stable releases and prereleases

Only stable GitHub releases are automatically published. If a GitHub release is marked as a prerelease, validation succeeds but the Hangar and Modrinth jobs are skipped. Before enabling prerelease publication, create and document the corresponding Hangar channels and add an explicit mapping between GitHub prereleases, Hangar channels, and Modrinth version types.

This prevents an alpha or beta build from accidentally entering Hangar's `Release` channel or being labeled as a Modrinth release.

## Recovery runs

If one marketplace fails, first use GitHub's **Re-run failed jobs** command. The Hangar and Modrinth jobs are independent, so a successful marketplace does not need to be published again.

For a later recovery:

1. Open **Actions > Publish release to marketplaces > Run workflow**.
2. Enter an already-published GitHub tag, such as `v1.1.0`.
3. Select `hangar` or `modrinth` as the target.
4. Run the workflow.

Use `all` only when neither marketplace has created that version. Both services reject duplicate version numbers.

## Security and maintenance

- Marketplace publication runs only for a published GitHub release or an explicit manual recovery run.
- The workflow has read-only GitHub repository permissions.
- The Hangar Action is pinned to the commit used by its `v2.2.0` release rather than a mutable major-version tag.
- The Modrinth Minotaur Gradle plugin is pinned to an exact version for reproducible builds.
- Review and update the pinned integrations deliberately when upstream releases security or compatibility fixes.
- Do not add marketplace secrets to pull-request workflows.

To inspect the Modrinth request without publishing it, place the release JAR at `build/release/LikeBeacon-<version>.jar` and run the `modrinth` task with `--no-configuration-cache -PmodrinthDebug=true`. A token value is still required by the plugin configuration, but debug mode does not send the version to Modrinth. Minotaur 2.10.0 is not compatible with Gradle's configuration cache, so the marketplace workflow disables it only for this task.
