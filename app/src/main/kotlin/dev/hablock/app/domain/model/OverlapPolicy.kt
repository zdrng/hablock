package dev.hablock.app.domain.model

/** Determines when an app mentioned by more than one active block is released. */
enum class OverlapPolicy {
    ALL_BLOCKS,
    ANY_BLOCK,
}
