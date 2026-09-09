# jenkins-shared-lib

개인/팀 Jenkins 환경용 **Shared Library** 저장소입니다.  
기설치 노드(rust, esp-rs/espup, sccache, `gh` 로그인)를 전제로 한 **esp-rs 펌웨어 크로스 빌드·배포** 단계를 재사용 가능하게 모아 둡니다.

## 디렉터리 구조

```text
.
├── src/com/suapapa/esprs/   # Groovy 헬퍼 클래스 (칩셋 → target triple)
├── vars/                    # Pipeline에서 호출하는 global steps
└── examples/                # 이 라이브러리를 쓰는 예시 Jenkinsfile
```

## Jenkins에 등록

**Manage Jenkins → System → Global Pipeline Libraries**

| 항목 | 값 |
|------|-----|
| Name | `jenkins-shared-lib` (`@Library` 이름과 일치) |
| Default version | `main` (또는 사용할 브랜치/태그) |
| Retrieval method | Modern SCM → 이 Git 저장소 |
| Load implicitly | 선택 |

파이프라인 상단:

```groovy
@Library('jenkins-shared-lib') _
```

## 에이전트 전제 조건

노드에 다음이 이미 설치·설정된 환경을 가정합니다.

- Rust + **esp-rs** (espup), 보통 `~/export-esp.sh`
- **sccache**
- **gh** CLI (`gh auth login` 완료, 타깃 릴리스 저장소 쓰기 권한) — GitHub Release 플로우용
- (OTA CDN) asset 저장소에 대한 **git SSH** 쓰기 권한
- (OTA API) 에이전트 환경 변수 `HOMIN_DEV_TOKEN`
- (선택) `cargo-espflash` — 있으면 `.bin` 플래시 이미지 생성

Docker/`cross`나 Jenkins Credential으로 `GH_TOKEN`을 주입하는 방식은 쓰지 않습니다.

## Steps (`vars/`)

| Step | 설명 |
|------|------|
| `espRsResolveVersion` | `git describe --tags --always` → `env.RELEASE_TAG` |
| `espRsResolveFwVer` | `params.SW_VERSION` / `FWVER` → `env.SW_VERSION` (OTA 수동 배포용) |
| `espRsPreflight` | cargo / rustc / sccache / gh 확인, sccache stats 초기화, `dist/` 생성 |
| `espRsBuild` | 칩셋별 release 빌드 → ELF(+bin) → `dist/*.tar.gz` + `.sha256` |
| `espRsPublish` | 에이전트 `gh` 세션으로 타깃 저장소 GitHub Release create/upload |
| `espRsCdnPush` | CDN용 asset git 저장소를 workspace에 clone → `.bin` 복사 → commit/push |
| `espRsOtaRegister` | HW revision별 OTA API에 바이너리 메타데이터 등록 (`HOMIN_DEV_TOKEN`) |
| `espRsSccacheStats` | `sccache --show-stats` |
| `espRsTargets` | 칩셋 ↔ Rust target triple 헬퍼 |

### 지원 칩셋

| Chip | Rust target |
|------|-------------|
| `esp32` | `xtensa-esp32-none-elf` |
| `esp32s3` | `xtensa-esp32s3-none-elf` |
| `esp32c3` | `riscv32imc-unknown-none-elf` |

맵핑 구현: `src/com/suapapa/esprs/ChipTargets.groovy`

### 호출 예

```groovy
script {
    espRsResolveVersion()
    espRsPreflight()
    espRsBuild(chip: 'esp32s3', binName: 'my-firmware')
    espRsPublish(targetRepo: 'your-org/target-binary-repo')
}
```

`espRsBuild` / `espRsPublish`는 `releaseTag`를 넘기지 않으면 `env.RELEASE_TAG`를 사용합니다.

### OTA CDN 배포 예

```groovy
script {
    espRsResolveFwVer()
    // project-specific: ./make_ota_bins.sh -v "${SW_VERSION}"
    espRsCdnPush(
        assetRepo: 'suapapa/homin-dev_asset',
        assetSubdir: 'asset/rusty-hangulclock_fw',
        version: env.SW_VERSION,
        sourceDir: 'release'
    )
    espRsOtaRegister(
        version: env.SW_VERSION,
        hwRevisions: ['3', '4'],
        sourceDir: 'release',
        binPrefix: 'rusty-hangulclock'
    )
}
```

에이전트에 `HOMIN_DEV_TOKEN`과 asset 저장소 SSH 쓰기 권한이 있어야 합니다.

## Examples

| 파일 | 용도 |
|------|------|
| [`examples/esp-rs-firmware.Jenkinsfile`](examples/esp-rs-firmware.Jenkinsfile) | PDF와 동일한 전체 플로우 (병렬 빌드 + GitHub Release) |
| [`examples/esp-rs-build-only.Jenkinsfile`](examples/esp-rs-build-only.Jenkinsfile) | 빌드·아카이브만 (PR/브랜치용, `gh` 불필요) |
| [`examples/esp-rs-ota-cdn.Jenkinsfile`](examples/esp-rs-ota-cdn.Jenkinsfile) | 수동 OTA: `make_ota_bins` → CDN git push → OTA API 등록 |

펌웨어 저장소에서 쓸 때:

1. example을 복사해 `Jenkinsfile`로 둔다.
2. `TARGET_REPO` / `FIRMWARE_BIN`, 또는 OTA의 `ASSET_REPO` / `OTA_*` 값을 실제 값으로 바꾼다.
3. `@Library('jenkins-shared-lib') _`를 유지한다.

## 환경 변수 (예시 파이프라인)

| 변수 | 의미 |
|------|------|
| `TARGET_REPO` | 바이너리 릴리스용 GitHub `owner/repo` |
| `FIRMWARE_BIN` | `Cargo.toml`의 바이너리 이름 |
| `ASSET_REPO` | CDN asset GitHub `owner/repo` (OTA) |
| `ASSET_SUBDIR` | asset 저장소 내 펌웨어 경로 |
| `OTA_API_URL` | OTA 메타데이터 POST URL |
| `DOWNLOAD_URL_BASE` | CDN 공개 다운로드 base URL |
| `HOMIN_DEV_TOKEN` | OTA API / 펌웨어 빌드 토큰 (에이전트 env) |
| `RUSTC_WRAPPER` | 보통 `sccache` |
| `SCCACHE_DIR` | 예: `${WORKSPACE}/.sccache` |
| `CARGO_TERM_COLOR` | `always` (콘솔 컬러) |
| `RELEASE_TAG` | `espRsResolveVersion`이 설정 |
| `SW_VERSION` | `espRsResolveFwVer`이 설정 |

## 산출물

칩셋별로 `dist/`에 다음이 생성됩니다.

- `{bin}-{tag}-{chip}.tar.gz` — ELF (+ 가능하면 `.bin`)
- `{bin}-{tag}-{chip}.tar.gz.sha256`
