/**
 * Resolve release version from git and set env.RELEASE_TAG.
 *
 *   espRsResolveVersion()
 *   espRsResolveVersion(command: 'git describe --tags --exact-match')
 *
 * @return trimmed version string
 */
def call(Map args = [:]) {
    def command = args.command ?: 'git describe --tags --always'
    def tag = sh(script: command, returnStdout: true).trim()
    env.RELEASE_TAG = tag
    echo "=== Target Release Version: ${tag} ==="
    return tag
}
