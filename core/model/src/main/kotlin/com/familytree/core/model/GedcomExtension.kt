package com.familytree.core.model

/**
 * A GEDCOM tag this app does not model natively.
 *
 * This is what makes lossless round-tripping possible. Real-world GEDCOM files are
 * full of vendor extensions (`_UID`, `_MILT`, `_ROOT`, `_HOME`, `_APID`…) and even
 * standard tags this app has no editor for. Rather than dropping them on import,
 * every unmapped tag is preserved here with its position in the record and its
 * children, and written back out unchanged on export.
 *
 * [parentExtensionId] makes the table self-referencing, so arbitrarily deep tag
 * hierarchies survive intact.
 */
data class GedcomExtension(
    val id: Long = 0L,
    val treeId: Long,
    val ownerType: OwnerType,
    val ownerId: Long,
    val parentExtensionId: Long? = null,
    val tag: String,
    /** A cross-reference id, when the tag points at another record. */
    val ref: String? = null,
    val value: String? = null,
    val position: Int = 0,
)

/** An [GedcomExtension] with its children resolved, for recursive rendering and export. */
data class GedcomExtensionNode(
    val extension: GedcomExtension,
    val children: List<GedcomExtensionNode> = emptyList(),
)
