/**
 * Print sccache hit/miss statistics.
 *
 *   espRsSccacheStats()
 */
def call(Map args = [:]) {
    def label = args.label ?: 'sccache Statistics'
    sh """
        set -euo pipefail
        echo "=== ${label} ==="
        sccache --show-stats || true
    """
}
