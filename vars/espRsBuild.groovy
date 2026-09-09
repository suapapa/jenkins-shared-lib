/**
 * Build, flash-image (optional), and package one esp-rs chip target.
 *
 * Expects a node with rust, espup export-esp.sh, and optionally cargo-espflash.
 * sccache is used when RUSTC_WRAPPER=sccache is set in the pipeline environment.
 *
 *   espRsBuild(chip: 'esp32', binName: 'my-firmware')
 *   espRsBuild(
 *     chip: env.CHIP,
 *     binName: 'my-firmware',
 *     releaseTag: env.RELEASE_TAG,
 *     distDir: 'dist',
 *     exportEspScript: '${HOME}/export-esp.sh',
 *     makeFlashImage: true
 *   )
 */
def call(Map args) {
    if (!args?.chip) {
        error("espRsBuild: 'chip' is required")
    }
    if (!args?.binName) {
        error("espRsBuild: 'binName' is required")
    }

    def chip = args.chip.toString()
    def binName = args.binName.toString()
    def releaseTag = args.releaseTag ?: env.RELEASE_TAG
    if (!releaseTag) {
        error('espRsBuild: releaseTag or env.RELEASE_TAG is required')
    }

    def distDir = args.distDir ?: 'dist'
    def profile = args.profile ?: 'release'
    def makeFlashImage = args.makeFlashImage != false
    def exportEspScript = args.exportEspScript ?: '${HOME}/export-esp.sh'
    def targetTriple = espRsTargets.triple(chip)
    def artifactName = "${binName}-${releaseTag}-${chip}.tar.gz"
    def pkgDir = "pkg_${chip}"

    echo "Building ${binName} for ${chip} (${targetTriple}), tag=${releaseTag}"

    // Ensure rustup cargo is visible even when callers skip espRsPreflight.
    def cargoBin = "${env.HOME}/.cargo/bin"
    if (!(env.PATH ?: '').tokenize(':').contains(cargoBin)) {
        env.PATH = "${cargoBin}:${env.PATH}"
    }

    // Shebang required: Jenkins defaults to /bin/sh (dash), not bash.
    // `source` is a bashism (POSIX equivalent is `.`).
    sh """#!/bin/bash
        set -eu
        export PATH="\$HOME/.cargo/bin:\$PATH"

        # 1) Load espup Xtensa/LLVM environment when present
        if [ -f "${exportEspScript}" ]; then
            # shellcheck disable=SC1090
            source "${exportEspScript}"
        elif [ -f "\$HOME/export-esp.sh" ]; then
            # shellcheck disable=SC1090
            source "\$HOME/export-esp.sh"
        else
            echo "WARN: export-esp.sh not found; relying on PATH toolchain"
        fi

        # 2) Release firmware build (sccache applies via RUSTC_WRAPPER)
        echo "Building for ${chip} (${targetTriple})..."
        cargo build --${profile} --target "${targetTriple}"

        # 3) Optional flash image via cargo-espflash
        mkdir -p "${pkgDir}"
        if [ "${makeFlashImage}" = "true" ] && command -v cargo-espflash >/dev/null 2>&1; then
            cargo espflash save-image --${profile} --chip "${chip}" "${pkgDir}/${binName}.bin"
        elif [ "${makeFlashImage}" = "true" ]; then
            echo "WARN: cargo-espflash not installed; skipping .bin image"
        fi

        # 4) Keep ELF for gdb / debugging
        cp "target/${targetTriple}/${profile}/${binName}" "${pkgDir}/${binName}.elf"

        # 5) Archive + SHA256
        mkdir -p "${distDir}"
        tar -czvf "${distDir}/${artifactName}" -C "${pkgDir}" .
        (
            cd "${distDir}"
            sha256sum "${artifactName}" > "${artifactName}.sha256"
        )

        echo "Artifact ready: ${distDir}/${artifactName}"
    """

    return [
        chip         : chip,
        targetTriple : targetTriple,
        artifactName : artifactName,
        artifactPath : "${distDir}/${artifactName}",
        checksumPath : "${distDir}/${artifactName}.sha256",
    ]
}
