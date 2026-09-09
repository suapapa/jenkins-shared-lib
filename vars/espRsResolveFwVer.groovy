/**
 * Resolve SW version for OTA builds.
 *
 * Priority: args.version → params.SW_VERSION → env.SW_VERSION → FWVER file.
 * Sets env.SW_VERSION.
 *
 *   espRsResolveFwVer()
 *   espRsResolveFwVer(version: '43', fwverFile: 'FWVER')
 *
 * @return trimmed version string
 */
def call(Map args = [:]) {
    def fwverFile = args.fwverFile ?: 'FWVER'
    def version = args.version?.toString()?.trim()

    if (!version) {
        try {
            def fromParam = params?.SW_VERSION?.toString()?.trim()
            if (fromParam) {
                version = fromParam
            }
        } catch (MissingPropertyException ignored) {
            // non-parameterized pipeline
        }
    }
    if (!version && env.SW_VERSION) {
        version = env.SW_VERSION.toString().trim()
    }
    if (!version && fileExists(fwverFile)) {
        version = readFile(fwverFile).trim()
    }

    if (!version) {
        error("espRsResolveFwVer: SW_VERSION is empty (pass version, set params.SW_VERSION, or provide ${fwverFile})")
    }

    env.SW_VERSION = version
    echo "=== OTA SW_VERSION: ${version} ==="
    return version
}
