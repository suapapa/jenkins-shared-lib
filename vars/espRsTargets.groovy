import com.suapapa.esprs.ChipTargets

/**
 * Chip ↔ Rust target triple helpers for esp-rs pipelines.
 *
 *   espRsTargets.triple('esp32s3')
 *   espRsTargets.defaultChips()
 */
def triple(String chip) {
    return ChipTargets.triple(chip)
}

def defaultChips() {
    return ChipTargets.defaultChips()
}

def isSupported(String chip) {
    return ChipTargets.isSupported(chip)
}

def all() {
    return ChipTargets.all()
}
