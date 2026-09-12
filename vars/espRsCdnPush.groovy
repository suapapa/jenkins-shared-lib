/**
 * Clone a CDN-backed asset git repo into the workspace, copy versioned binaries,
 * prune older artifacts (default: keep latest 6 per target prefix), commit, and push.
 * Uses the agent's pre-authenticated gh CLI (no Jenkins credentials).
 *
 *   espRsCdnPush(
 *     assetRepo: 'suapapa/homin-dev_asset',
 *     assetSubdir: 'asset/rusty-hangulclock_fw',
 *     version: env.SW_VERSION,
 *     sourceDir: 'release',
 *     keepCount: 6
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
    def keepCount = args.containsKey('keepCount') ? (args.keepCount as int) : (args.containsKey('keepArtifacts') ? (args.keepArtifacts as int) : 6)

    def commitMessage = args.commitMessage
    if (!commitMessage) {
        def leaf = assetSubdir.replaceAll(/\/$/, '').split('/').last()
        commitMessage = "feat(${leaf}): add sw ver. ${version}"
    }
    def safeMsg = commitMessage.replace('"', '\\"')

    echo "CDN push: ${sourceDir}/*_${version}_*.bin → ${assetRepo}:${assetSubdir} (keepCount: ${keepCount})"

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

        # Prune older artifacts per target prefix to retain only the latest keepCount artifacts
        keep_count="${keepCount}"
        if [ "\${keep_count}" -gt 0 ]; then
            prefixes=()
            for b in "\${bins[@]}"; do
                bname="\$(basename "\$b")"
                prefix="\${bname%%_${version}_*}"
                matched=0
                if [ "\${#prefixes[@]}" -gt 0 ]; then
                    for p in "\${prefixes[@]}"; do
                        if [ "\$p" = "\$prefix" ]; then
                            matched=1
                            break
                        fi
                    done
                fi
                if [ "\$matched" -eq 0 ]; then
                    prefixes+=("\${prefix}")
                fi
            done

            for p in "\${prefixes[@]}"; do
                shopt -s nullglob
                if [ -n "\$p" ]; then
                    p_bins=("\${dest}/\${p}_"*.bin)
                    if [ "\${#p_bins[@]}" -eq 0 ]; then
                        p_bins=("\${dest}/\${p}"*.bin)
                    fi
                else
                    p_bins=("\${dest}"/*.bin)
                fi

                if [ "\${#p_bins[@]}" -eq 0 ]; then
                    continue
                fi

                files=()
                while IFS= read -r f; do
                    [ -n "\$f" ] && files+=("\$f")
                done < <(printf "%s\n" "\${p_bins[@]}" | sort -V)

                total="\${#files[@]}"
                if [ "\$total" -gt "\${keep_count}" ]; then
                    delete_count=\$((total - keep_count))
                    echo "Retention (keep \${keep_count}): removing \$delete_count older artifact(s) for '\${p}'..."
                    for ((i=0; i<delete_count; i++)); do
                        echo "Removing \${files[i]}"
                        rm -f "\${files[i]}"
                    done
                fi
            done
        fi

        cd "${workDir}"

        # Local-only: route git HTTPS auth through gh (do not touch global gitconfig)
        git config --local credential.helper ""
        git config --local --add credential.helper "!gh auth git-credential"

        git add -A -- "${assetSubdir}"

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
