/**
 * Clone a CDN-backed asset git repo into the workspace, copy versioned binaries,
 * optionally prune older artifacts with git rm (when keepCount > 0), commit, and push.
 * Uses the agent's pre-authenticated gh CLI (no Jenkins credentials).
 *
 *   espRsCdnPush(
 *     assetRepo: 'suapapa/homin-dev_asset',
 *     assetSubdir: 'asset/rusty-hangulclock_fw',
 *     version: env.SW_VERSION,
 *     sourceDir: 'release'
 *   )
 *
 * Optional:
 *   keepCount: 0 (default: 0 = unlimited; when > 0, keeps latest keepCount artifacts in assetSubdir via git rm)
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
    def keepCount = args.containsKey('keepCount') ? (args.keepCount as int) : (args.containsKey('keepArtifacts') ? (args.keepArtifacts as int) : 0)

    def commitMessage = args.commitMessage
    if (!commitMessage) {
        def leaf = assetSubdir.replaceAll(/\/$/, '').split('/').last()
        commitMessage = "feat(${leaf}): add sw ver. ${version}"
    }
    def safeMsg = commitMessage.replace('"', '\\"')

    if (keepCount > 0) {
        echo "CDN push: ${sourceDir}/*_${version}_*.bin → ${assetRepo}:${assetSubdir} (keepCount: ${keepCount})"
    } else {
        echo "CDN push: ${sourceDir}/*_${version}_*.bin → ${assetRepo}:${assetSubdir}"
    }

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

        keep_count="${keepCount}"
        if [ "\${keep_count}" -gt 0 ]; then
            shopt -s nullglob
            artifacts=("${assetSubdir}"/*.bin)
            if [ "\${#artifacts[@]}" -gt 0 ]; then
                files=()
                while IFS= read -r f; do
                    [ -n "\$f" ] && files+=("\$f")
                done < <(for a in "\${artifacts[@]}"; do
                    ts=\$(echo "\$a" | grep -oE "[0-9]{8}_[0-9]{6}" || true)
                    echo "\$ts \$a"
                done | sort -k1,1 -V | cut -d" " -f2-)

                total="\${#files[@]}"
                if [ "\$total" -gt "\${keep_count}" ]; then
                    delete_count=\$((total - keep_count))
                    echo "Retention (keep \${keep_count}): removing \$delete_count older artifact(s) in ${assetSubdir}..."
                    for ((i=0; i<delete_count; i++)); do
                        echo "git rm: \${files[i]}"
                        git rm -f -- "\${files[i]}"
                    done
                fi
            fi
        fi

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
