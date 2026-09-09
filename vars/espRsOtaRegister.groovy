/**
 * Register OTA binaries with the update API (POST metadata for each HW revision).
 * Injects HOMIN_DEV_TOKEN from a Jenkins Secret text credential (default id:
 * HOMIN_DEV_TOKEN). Override with args.credentialsId.
 *
 * Prefer calling the project's release/upload_update.sh when present; otherwise
 * POST JSON directly.
 *
 *   espRsOtaRegister(
 *     version: env.SW_VERSION,
 *     hwRevisions: ['3', '4'],
 *     sourceDir: 'release',
 *     binPrefix: 'rusty-hangulclock'
 *   )
 */
def call(Map args) {
    def version = (args.version ?: env.SW_VERSION)?.toString()?.trim()
    if (!version) {
        error('espRsOtaRegister: version or env.SW_VERSION is required')
    }

    def sourceDir = args.sourceDir ?: 'release'
    def binPrefix = args.binPrefix ?: env.OTA_BIN_PREFIX ?: 'rusty-hangulclock'
    def hwRevisions = args.hwRevisions ?: ['3', '4']
    if (hwRevisions instanceof String) {
        hwRevisions = hwRevisions.split(/[,\s]+/).findAll { it }
    }

    def apiUrl = args.apiUrl ?: env.OTA_API_URL ?: 'https://hangulclock.homin.dev/v1/update'
    def downloadUrlBase = args.downloadUrlBase ?: env.DOWNLOAD_URL_BASE ?: 'https://asset.homin.dev/rusty-hangulclock_fw/'
    def uploadScript = args.uploadScript ?: 'release/upload_update.sh'
    def credentialsId = (args.credentialsId ?: 'HOMIN_DEV_TOKEN').toString()

    def revs = hwRevisions.collect { it.toString() }.join(' ')

    echo "OTA register: version=${version} revisions=[${revs}] api=${apiUrl}"

    // Shebang required: Jenkins defaults to /bin/sh (dash), not bash.
    withCredentials([string(credentialsId: credentialsId, variable: 'HOMIN_DEV_TOKEN')]) {
        sh """#!/bin/bash
            set -eu

            if [ -z "\${HOMIN_DEV_TOKEN:-}" ]; then
                echo "ERROR: HOMIN_DEV_TOKEN credential binding is empty"
                exit 1
            fi

            export OTA_API_URL="${apiUrl}"
            export DOWNLOAD_URL_BASE="${downloadUrlBase}"

            for rev in ${revs}; do
                shopt -s nullglob
                files=("${sourceDir}/${binPrefix}_rev\${rev}_${version}_"*.bin)
                if [ "\${#files[@]}" -eq 0 ]; then
                    echo "ERROR: no binary for HW revision \${rev} under ${sourceDir}/"
                    exit 1
                fi
                file="\${files[0]}"
                echo "Registering \${file} (rev=\${rev})..."

                if [ -f "${uploadScript}" ]; then
                    bash "${uploadScript}" -v "${version}" -r "\${rev}" -f "\${file}"
                else
                    name="\$(basename "\${file}")"
                    base="\${DOWNLOAD_URL_BASE}"
                    case "\${base}" in */) ;; *) base="\${base}/" ;; esac
                    curl -fsS -X POST "\${OTA_API_URL}" \\
                      -H "Content-Type: application/json" \\
                      -H "Authorization: Bearer \${HOMIN_DEV_TOKEN}" \\
                      -d "{
                        \\"version\\": ${version},
                        \\"download_url\\": \\"\${base}\${name}\\",
                        \\"release_notes\\": \\"SW version ${version} for HW revision \${rev}\\",
                        \\"hw_revision\\": \${rev}
                      }"
                    echo
                fi
            done
        """
    }
}
