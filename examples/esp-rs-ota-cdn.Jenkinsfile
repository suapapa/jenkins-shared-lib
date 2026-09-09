// Example: manual OTA bin build → CDN asset git push → OTA API register.
// Copy into a firmware repo as Jenkinsfile and adjust ASSET_* / OTA_* values.
//
// Agent prerequisites:
//   rust + esp-rs (espup), ~/export-esp.sh, cargo-espflash
//   git SSH write access to the asset repository
//   HOMIN_DEV_TOKEN in the agent environment
//
// Project scripts expected:
//   ./make_ota_bins.sh [-v SW_VERSION]
//   release/upload_update.sh -v ... -r ... -f ...

@Library('jenkins-shared-lib') _

pipeline {
    agent any

    options {
        timeout(time: 2, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    parameters {
        string(
            name: 'SW_VERSION',
            defaultValue: '',
            description: 'Leave empty to use FWVER from the repository.'
        )
        booleanParam(name: 'SKIP_CDN', defaultValue: false, description: 'Skip CDN git push')
        booleanParam(name: 'SKIP_OTA_API', defaultValue: false, description: 'Skip OTA API registration')
    }

    environment {
        ASSET_REPO = 'your-org/your-asset-repo'
        ASSET_SUBDIR = 'asset/your-firmware_fw'
        OTA_API_URL = 'https://example.com/v1/update'
        DOWNLOAD_URL_BASE = 'https://cdn.example.com/your-firmware_fw/'
        OTA_BIN_PREFIX = 'your-firmware'

        // rustup install is not on non-login shell PATH by default
        PATH = "${HOME}/.cargo/bin:${env.PATH}"
        RUSTC_WRAPPER = 'sccache'
        SCCACHE_DIR = "${WORKSPACE}/.sccache"
        CARGO_TERM_COLOR = 'always'
    }

    stages {
        stage('Checkout & Version') {
            steps {
                checkout scm
                script { espRsResolveFwVer() }
            }
        }

        stage('Pre-flight') {
            steps {
                sh '''
                    set -eu
                    cargo --version
                    rustc --version
                    command -v cargo-espflash
                    test -n "${HOMIN_DEV_TOKEN:-}"
                    mkdir -p release
                '''
            }
        }

        stage('Build OTA bins') {
            steps {
                sh '''
                    set -eu
                    if [ -f "${HOME}/export-esp.sh" ]; then
                        # shellcheck disable=SC1090
                        source "${HOME}/export-esp.sh"
                    fi
                    ./make_ota_bins.sh -v "${SW_VERSION}"
                '''
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: "release/*_${env.SW_VERSION}_*.bin", fingerprint: true
            }
        }

        stage('CDN push') {
            when { expression { return !params.SKIP_CDN } }
            steps {
                script {
                    espRsCdnPush(
                        assetRepo: env.ASSET_REPO,
                        assetSubdir: env.ASSET_SUBDIR,
                        version: env.SW_VERSION,
                        sourceDir: 'release'
                    )
                }
            }
        }

        stage('OTA API register') {
            when { expression { return !params.SKIP_OTA_API } }
            steps {
                script {
                    espRsOtaRegister(
                        version: env.SW_VERSION,
                        hwRevisions: ['3', '4'],
                        sourceDir: 'release',
                        binPrefix: env.OTA_BIN_PREFIX
                    )
                }
            }
        }
    }

    post {
        always {
            cleanWs(
                deleteDirs: true,
                notFailBuild: true,
                patterns: [
                    [pattern: 'target/**', type: 'INCLUDE'],
                    [pattern: 'cdn-asset/**', type: 'INCLUDE']
                ]
            )
        }
    }
}
