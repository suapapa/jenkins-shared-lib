/**
 * Clone a CDN-backed asset git repo into the workspace, copy versioned binaries,
 * commit, and push. Uses the agent's pre-authenticated gh CLI (no Jenkins credentials).
 *
 *   espRsCdnPush(
 *     assetRepo: 'suapapa/homin-dev_asset',
 *     assetSubdir: 'asset/rusty-hangulclock_fw',
 *     version: env.SW_VERSION,
 *     sourceDir: 'release'
 *   )
 */
def call(Map args) {
    def assetRepo = args.assetRepo ?: env.ASSET_REPO
    if (!assetRepo) {
        error('espRsCdnPush: assetRepo or env.ASSET_REPO is required (owner/name)')
    }

    def version = (args.version ?: env.SW_VERSION)?.toString()?.trim()
    if (!version) {
        error('espRsCdnPush: version or env.SW_VERSION is required')
    }

    def assetSubdir = args.assetSubdir ?: env.ASSET_SUBDIR ?: 'asset/rusty-hangulclock_fw'
    def sourceDir = args.sourceDir ?: 'release'
    def workDir = args.workDir ?: 'cdn-asset'

    def commitMessage = args.commitMessage
    if (!commitMessage) {
        def leaf = assetSubdir.replaceAll(/\/$/, '').split('/').last()
        commitMessage = "feat(${leaf}): add sw ver. ${version}"
    }
    def safeMsg = commitMessage.replace('"', '\\"')

    echo "CDN push: ${sourceDir}/*_${version}_*.bin → ${assetRepo}:${assetSubdir}"

    // Shebang required: Jenkins defaults to /bin/sh (dash), not bash.
    sh """#!/bin/bash
        set -eu

        if [ ! -d "${sourceDir}" ]; then
            echo "ERROR: sourceDir not found: ${sourceDir}"
            exit 1
        fi

        shopt -s nullglob
        bins=("${sourceDir}"/*_${version}_*.bin)
        if [ "\${#bins[@]}" -eq 0 ]; then
            echo "ERROR: no binaries matching *_${version}_*.bin under ${sourceDir}"
            exit 1
        fi

        rm -rf "${workDir}"
        gh repo clone "${assetRepo}" "${workDir}" -- --depth 1

        dest="${workDir}/${assetSubdir}"
        mkdir -p "\${dest}"
        cp "\${bins[@]}" "\${dest}/"

        cd "${workDir}"

        # Local-only: route git HTTPS auth through gh (do not touch global gitconfig)
        git config --local credential.helper ""
        git config --local --add credential.helper "!gh auth git-credential"

        git add -- "${assetSubdir}"/*_${version}_*.bin

        if git diff --cached --quiet; then
            echo "Nothing new to commit for version ${version}"
            exit 0
        fi

        # Local identity only (do not touch global gitconfig)
        git config user.email "\${GIT_AUTHOR_EMAIL:-jenkins@localhost}"
        git config user.name "\${GIT_AUTHOR_NAME:-Jenkins}"

        git commit -m "${safeMsg}"
        git push origin HEAD
        echo "Pushed CDN assets for version ${version}"
    """
}
