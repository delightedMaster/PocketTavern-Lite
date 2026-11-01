package me.rerere.rikkahub.data.model

import kotlin.uuid.Uuid

/** 只保留指定类型的世界书或预设，顺序与原列表一致。 */
internal fun lorebooksOfKind(lorebooks: List<Lorebook>, kind: LorebookKind): List<Lorebook> =
    lorebooks.filter { it.kind == kind }

/**
 * 绑定或解绑一条世界书/预设。
 * 两类共用 [Assistant.lorebookIds]，这里只改 [id]，另一类的 id 原样保留。
 */
internal fun toggleLorebookBinding(boundIds: Set<Uuid>, id: Uuid, checked: Boolean): Set<Uuid> =
    if (checked) boundIds + id else boundIds - id
