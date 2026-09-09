package com.suapapa.esprs

/**
 * esp-rs chip name → Rust target triple mapping.
 * Used by vars/espRsBuild and vars/espRsTargets.
 */
class ChipTargets implements Serializable {
    private static final long serialVersionUID = 1L

    private static final Map<String, String> TRIPLES = [
        'esp32'  : 'xtensa-esp32-none-elf',
        'esp32s3': 'xtensa-esp32s3-none-elf',
        'esp32c3': 'riscv32imc-unknown-none-elf',
    ].asImmutable()

    static String triple(String chip) {
        def key = chip?.trim()?.toLowerCase()
        def triple = TRIPLES[key]
        if (!triple) {
            throw new IllegalArgumentException(
                "Unsupported chip '${chip}'. Known: ${TRIPLES.keySet().sort().join(', ')}"
            )
        }
        return triple
    }

    static List<String> defaultChips() {
        return TRIPLES.keySet().sort() as List
    }

    static boolean isSupported(String chip) {
        return TRIPLES.containsKey(chip?.trim()?.toLowerCase())
    }

    static Map<String, String> all() {
        return TRIPLES
    }
}
