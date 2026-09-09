// Example pipeline reconstructed from _refs/rust-cross-build-jenkins-guide.pdf
// using this repository's shared library steps.
//
// Jenkins setup:
//   Manage Jenkins → System → Global Pipeline Libraries
//     Name:    jenkins-shared-lib   (must match @Library below)
//     Default version: main (or your branch/tag)
//     Retrieval: Modern SCM → this git repo
//     Load implicitly: optional
//
// Agent prerequisites (preinstalled):
//   rust + esp-rs (espup), ~/export-esp.sh, sccache, gh (auth login done)
//
// Configure before first run:
//   TARGET_REPO  → owner/name of the binary release repository
//   FIRMWARE_BIN → [[bin]] name from the firmware Cargo.toml

@Library('jenkins-shared-lib') _

pipeline {
    agent any

    options {
        timeout(time: 1, unit: 'HOURS')
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    environment {
        // 1) Deployment target GitHub repository (owner/repository)
        TARGET_REPO = 'your-org/target-binary-repo'

        // Cargo package binary name (must match Cargo.toml [[bin]] / default package name)
        FIRMWARE_BIN = 'my-firmware'

        // 2) rustup bin dir (Jenkins sh is non-login; ~/.bashrc is not sourced)
        PATH = "${HOME}/.cargo/bin:${env.PATH}"

        // 3) sccache compile cache
        RUSTC_WRAPPER = 'sccache'
        SCCACHE_DIR = "${WORKSPACE}/.sccache"

        // 4) Keep cargo terminal colors in Jenkins console
        CARGO_TERM_COLOR = 'always'
    }

    stages {
        stage('Checkout & Versioning') {
            steps {
                checkout scm
                script {
                    espRsResolveVersion()
                }
            }
        }

        stage('Pre-flight Check') {
            steps {
                script {
                    espRsPreflight()
                }
            }
        }

        stage('Parallel esp-rs Build') {
            matrix {
                axes {
                    axis {
                        name 'CHIP'
                        values 'esp32', 'esp32s3', 'esp32c3'
                    }
                }
                stages {
                    stage('Build & Package') {
                        steps {
                            script {
                                espRsBuild(
                                    chip: env.CHIP,
                                    binName: env.FIRMWARE_BIN,
                                    releaseTag: env.RELEASE_TAG
                                )
                            }
                        }
                    }
                }
            }
        }

        stage('Publish to Target GitHub Repo') {
            steps {
                script {
                    espRsPublish(
                        targetRepo: env.TARGET_REPO,
                        releaseTag: env.RELEASE_TAG,
                        title: "Firmware Release ${env.RELEASE_TAG}",
                        notes: 'Automated esp-rs build from Jenkins Node (sccache accelerated).'
                    )
                }
            }
        }
    }

    post {
        always {
            // Drop bulky build trees; keep .sccache for the next run
            cleanWs(
                deleteDirs: true,
                notFailBuild: true,
                patterns: [
                    [pattern: 'target/**', type: 'INCLUDE'],
                    [pattern: 'pkg_*/**', type: 'INCLUDE']
                ]
            )
        }
        failure {
            echo 'Pipeline build failed. Please check console logs.'
        }
    }
}
