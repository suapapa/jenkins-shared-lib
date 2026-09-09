/**
 * Print sccache hit/miss statistics.
 *
 *   espRsSccacheStats()
 */
def call(Map args = [:]) {
    def label = args.label ?: 'sccache Statistics'
    sh """
        set -eu
        echo "=== ${label} ==="
        sccache --show-stats || true
    """
}
