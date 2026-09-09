/**
 * Verify preinstalled rust / esp-rs / sccache / gh toolchain on the agent.
 *
 *   espRsPreflight()
 *   espRsPreflight(distDir: 'dist', zeroSccacheStats: true)
 */
def call(Map args = [:]) {
    def distDir = args.distDir ?: 'dist'
    def zeroStats = args.zeroSccacheStats != false
    def requireGh = args.requireGh != false

    sh """
        set -eu
        echo "=== Toolchain Verification ==="
        cargo --version
        rustc --version
        sccache --version
        ${requireGh ? 'gh auth status' : 'echo "gh check skipped"'}
        ${zeroStats ? 'sccache --zero-stats || true' : 'true'}
        mkdir -p "${distDir}"
    """
}
