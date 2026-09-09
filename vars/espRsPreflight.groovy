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

    // Jenkins sh is non-login; rustup's ~/.cargo/bin is often missing from PATH.
    // Setting env.PATH here also benefits later stages in the same build.
    def cargoBin = "${env.HOME}/.cargo/bin"
    if (!(env.PATH ?: '').tokenize(':').contains(cargoBin)) {
        env.PATH = "${cargoBin}:${env.PATH}"
    }

    sh """
        set -eu
        export PATH="\$HOME/.cargo/bin:\$PATH"
        echo "=== Toolchain Verification ==="
        cargo --version
        rustc --version
        sccache --version
        ${requireGh ? 'gh auth status' : 'echo "gh check skipped"'}
        ${zeroStats ? 'sccache --zero-stats || true' : 'true'}
        mkdir -p "${distDir}"
    """
}
