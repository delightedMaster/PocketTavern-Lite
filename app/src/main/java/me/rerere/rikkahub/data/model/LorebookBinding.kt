package me.rerere.rikkahub.data.model

import kotlin.uuid.Uuid

/** 只保留指定类型的世界书或预设，顺序与原列表一致。 */
internal fun lorebooksOfKind(lorebooks: List<Lorebook>, kind: LorebookKind): List<Lorebook> =
    lorebooks.filter { it.kind == kind }

/**
 * 绑定或解绑一条世界书/预设。
 * 世界书支持多选；预设（[LorebookKind.PRESET]）单选互斥，开启新预设时自动关闭其他预设，
 * 两类共用 [Assistant.lorebookIds]，另一类的绑定原样保留。
 */
internal fun toggleLorebookBinding(
    boundIds: Set<Uuid>,
    id: Uuid,
    checked: Boolean,
    kind: LorebookKind = LorebookKind.WORLD_BOOK,
    allLorebooks: List<Lorebook> = emptyList(),
): Set<Uuid> {
    if (!checked) return boundIds - id
    if (kind == LorebookKind.PRESET) {
        val otherPresetIds = allLorebooks
            .asSequence()
            .filter { it.kind == LorebookKind.PRESET && it.id != id }
            .map { it.id }
            .toSet()
        return (boundIds - otherPresetIds) + id
    }
    return boundIds + id
}
