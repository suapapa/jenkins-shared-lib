# Build-only example (no GitHub release publish).
# Useful for PR / branch builds where you only need firmware artifacts.

@Library('jenkins-shared-lib') _

pipeline {
    agent any

    options {
        timeout(time: 1, unit: 'HOURS')
        ansiColor('xterm')
    }

    environment {
        FIRMWARE_BIN = 'my-firmware'
        RUSTC_WRAPPER = 'sccache'
        SCCACHE_DIR = "${WORKSPACE}/.sccache"
        CARGO_TERM_COLOR = 'always'
    }

    stages {
        stage('Checkout & Versioning') {
            steps {
                checkout scm
                script { espRsResolveVersion() }
            }
        }

        stage('Pre-flight Check') {
            steps {
                script { espRsPreflight(requireGh: false) }
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
                                    binName: env.FIRMWARE_BIN
                                )
                            }
                        }
                    }
                }
            }
        }

        stage('sccache Stats') {
            steps {
                script { espRsSccacheStats() }
            }
        }

        stage('Archive Artifacts') {
            steps {
                archiveArtifacts artifacts: 'dist/*', fingerprint: true
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
                    [pattern: 'pkg_*/**', type: 'INCLUDE']
                ]
            )
        }
    }
}
