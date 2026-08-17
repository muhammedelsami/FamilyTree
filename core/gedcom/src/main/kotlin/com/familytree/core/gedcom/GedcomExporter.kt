package com.familytree.core.gedcom

import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.folg.gedcom.visitors.GedcomWriter
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

/**
 * Writes a tree back out as a `.ged` file.
 *
 * All the work of turning rows back into records happens in [GedcomProjector]; this
 * only serialises the result, so export and the diagram layout engine are guaranteed to
 * see exactly the same model.
 */
class GedcomExporter @Inject constructor(
    private val projector: GedcomProjector,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun export(treeId: Long, output: OutputStream) = withContext(ioDispatcher) {
        val gedcom = projector.project(treeId)
        GedcomWriter().write(gedcom, output)
    }

    suspend fun export(treeId: Long, file: File) = withContext(ioDispatcher) {
        val gedcom = projector.project(treeId)
        GedcomWriter().write(gedcom, file)
    }
}
