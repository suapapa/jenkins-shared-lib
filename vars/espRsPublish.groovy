/**
 * Publish dist artifacts to a GitHub Release using the agent's pre-authenticated gh CLI.
 * No Jenkins credentials / GH_TOKEN injection required when gh auth login is already done.
 *
 *   espRsPublish(targetRepo: 'your-org/target-binary-repo')
 *   espRsPublish(
 *     targetRepo: env.TARGET_REPO,
 *     releaseTag: env.RELEASE_TAG,
 *     distDir: 'dist',
 *     title: "Firmware Release ${env.RELEASE_TAG}",
 *     notes: 'Automated esp-rs build from Jenkins.'
 *   )
 */
def call(Map args) {
    def targetRepo = args.targetRepo ?: env.TARGET_REPO
    if (!targetRepo) {
        error('espRsPublish: targetRepo or env.TARGET_REPO is required')
    }

    def releaseTag = args.releaseTag ?: env.RELEASE_TAG
    if (!releaseTag) {
        error('espRsPublish: releaseTag or env.RELEASE_TAG is required')
    }

    def distDir = args.distDir ?: 'dist'
    def title = args.title ?: "Firmware Release ${releaseTag}"
    def notes = args.notes ?: 'Automated esp-rs build from Jenkins Node (sccache accelerated).'
    def clobber = args.clobber != false
    def showStats = args.showSccacheStats != false

    // Avoid breaking the shell when title/notes contain quotes
    def safeTitle = title.replace('"', '\\"')
    def safeNotes = notes.replace('"', '\\"')

    if (showStats) {
        espRsSccacheStats()
    }

    sh """
        set -eu

        if [ ! -d "${distDir}" ] || [ -z "\$(ls -A '${distDir}' 2>/dev/null || true)" ]; then
            echo "ERROR: no artifacts under ${distDir}"
            exit 1
        fi

        echo "Deploying release assets to https://github.com/${targetRepo}..."

        if gh release view "${releaseTag}" --repo "${targetRepo}" >/dev/null 2>&1; then
            echo "Release ${releaseTag} exists. Uploading new assets..."
            gh release upload "${releaseTag}" "${distDir}"/* \\
                --repo "${targetRepo}" \\
                ${clobber ? '--clobber' : ''}
        else
            echo "Creating new release ${releaseTag} on ${targetRepo}..."
            gh release create "${releaseTag}" "${distDir}"/* \\
                --repo "${targetRepo}" \\
                --title "${safeTitle}" \\
                --notes "${safeNotes}"
        fi
    """
}
