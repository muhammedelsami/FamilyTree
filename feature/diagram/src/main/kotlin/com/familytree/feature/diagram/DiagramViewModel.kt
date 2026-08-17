package com.familytree.feature.diagram

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familytree.core.diagram.DiagramEngine
import com.familytree.core.diagram.DiagramLayout
import com.familytree.core.diagram.DiagramSession
import com.familytree.core.diagram.export.DiagramExporter
import com.familytree.core.domain.repository.FamilyRepository
import com.familytree.core.domain.repository.MediaRepository
import com.familytree.core.domain.repository.PersonRepository
import com.familytree.core.domain.repository.SettingsRepository
import com.familytree.core.domain.repository.TreeRepository
import com.familytree.core.gedcom.GedcomProjector
import com.familytree.core.domain.usecase.AddRelativeUseCase
import com.familytree.core.domain.usecase.DeletePersonUseCase
import com.familytree.core.model.MediaObject
import com.familytree.core.model.Relation
import com.familytree.core.model.RelativeTarget
import com.familytree.core.model.PersonSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ExportFormat(val mimeType: String, val extension: String) {
    PNG("image/png", "png"),
    PDF("application/pdf", "pdf"),

    /**
     * The one format with no ceiling: PNG needs the whole bitmap in memory and PDF caps a
     * page at 14400 points, so a tree of enough generations only exports as SVG.
     */
    SVG("image/svg+xml", "svg"),
}

data class DiagramUiState(
    val session: DiagramSession? = null,
    /** Card contents, keyed by GEDCOM id because that is what the engine works in. */
    val peopleById: Map<String, PersonSummary> = emptyMap(),
    /** Family row ids by GEDCOM id, so a tapped bond can be opened. */
    val familyIdsByGedcomId: Map<String, Long> = emptyMap(),
    /** Card portraits, keyed the same way as [peopleById]. */
    val portraits: Map<String, MediaObject> = emptyMap(),
    val loading: Boolean = true,
    /** The tree holds nobody yet, so there is nothing to draw. */
    val isEmpty: Boolean = false,
    val error: String? = null,
    /** The card whose long-press menu is open, if any. */
    val cardMenu: CardMenuTarget? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiagramViewModel @Inject constructor(
    private val treeRepository: TreeRepository,
    private val personRepository: PersonRepository,
    private val familyRepository: FamilyRepository,
    private val mediaRepository: MediaRepository,
    private val addRelative: AddRelativeUseCase,
    private val deletePerson: DeletePersonUseCase,
    private val settingsRepository: SettingsRepository,
    private val projector: GedcomProjector,
    private val engine: DiagramEngine,
    private val exporter: DiagramExporter,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagramUiState())
    val uiState: StateFlow<DiagramUiState> = _uiState.asStateFlow()

    private var treeId: Long = 0L

    /** The person the diagram is drawn around; null means "use the tree's root". */
    private var fulcrumGedcomId: String? = null

    private val openTree = MutableStateFlow(0L)

    /** One layout at a time: the engine mutates its own state as it places nodes. */
    private val redrawing = Mutex()

    init {
        // The diagram is a view of the tree, so it follows the tree. Without this it was
        // a snapshot taken when the tab was first opened: a child added from the person
        // editor, or a name corrected in the profile, simply did not appear until the
        // tree was closed and reopened.
        viewModelScope.launch {
            openTree
                .filter { it != 0L }
                .flatMapLatest(::contentSignal)
                .distinctUntilChanged()
                .collect {
                    // Only the first pass shows the spinner. A redraw after an edit keeps
                    // the current diagram on screen until the new one is ready, so a
                    // small correction does not blank the screen.
                    load(showLoading = _uiState.value.session == null)
                }
        }
    }

    /**
     * A cheap description of everything the diagram draws.
     *
     * Compared rather than acted on directly, so Room re-emitting identical rows — which
     * it does on any write to the same tables — does not send the layout engine through a
     * full pass over a tree of thousands.
     */
    private fun contentSignal(id: Long): Flow<String> = combine(
        personRepository.observePersonSummaries(id),
        familyRepository.observeAllMemberships(id),
        familyRepository.observeFamilyEvents(id),
        mediaRepository.observePersonPortraits(id),
        settingsRepository.settings,
    ) { people, memberships, events, portraits, settings ->
        buildString {
            people.forEach { person ->
                append(person.person.id).append(person.displayName)
                    .append(person.person.sex).append(person.isDeceased)
                    .append(person.birth?.year).append(person.death?.year).append('|')
            }
            memberships.forEach { append(it.familyId).append(it.personId).append(it.role).append('|') }
            events.forEach { append(it.id).append(it.tag).append(it.date.orEmpty()).append('|') }
            portraits.forEach { (personId, media) -> append(personId).append(media.file.orEmpty()).append('|') }
            append(settings.diagram)
        }
    }

    fun setTree(id: Long) {
        if (treeId == id && _uiState.value.session != null) return
        treeId = id
        openTree.value = id
    }

    /** Re-draws around a different person, as tapping a mini card does. */
    fun recenterOn(gedcomId: String) {
        fulcrumGedcomId = gedcomId
        load()
    }

    fun reload() = load()

    /**
     * Gathers what the long-press menu needs about one card.
     *
     * Done on demand: a large diagram shows hundreds of cards, and asking every one of
     * them for its families up front would be thousands of queries for a menu that opens
     * on one card at a time.
     */
    fun openCardMenu(gedcomId: String) = viewModelScope.launch {
        val summary = _uiState.value.peopleById[gedcomId] ?: return@launch
        val personId = summary.person.id

        val parents = familyRepository.getParentFamilies(personId).map { family ->
            CardFamily(family.id, familyLabel(family.id, personId))
        }
        val spouses = familyRepository.getSpouseFamilies(personId).map { family ->
            CardFamily(family.id, familyLabel(family.id, personId))
        }

        _uiState.value = _uiState.value.copy(
            cardMenu = CardMenuTarget(
                personId = personId,
                gedcomId = gedcomId,
                name = summary.displayName,
                isFulcrum = gedcomId == currentFulcrum(),
                parentFamilies = parents,
                spouseFamilies = spouses,
                // Nobody to link to in a tree of one.
                canLinkExisting = _uiState.value.peopleById.size > 1,
            ),
        )
    }

    fun dismissCardMenu() {
        _uiState.value = _uiState.value.copy(cardMenu = null)
    }

    /**
     * Names a family by the people in it other than the person the menu belongs to.
     *
     * "with Ayşe Yılmaz" tells the user which marriage they are about to open; "F3" does
     * not, and a person with two families gets two identical entries without it.
     */
    private suspend fun familyLabel(familyId: Long, exceptPersonId: Long): String {
        val names = familyRepository.getMembers(familyId)
            .filter { it.personId != exceptPersonId }
            .mapNotNull { member ->
                _uiState.value.peopleById.values
                    .firstOrNull { it.person.id == member.personId }
                    ?.displayName
                    ?.takeIf { name -> name.isNotBlank() }
            }
        return names.joinToString(", ")
    }

    private fun currentFulcrum(): String? =
        fulcrumGedcomId ?: _uiState.value.session?.cards?.firstOrNull { it.isFulcrum }?.personGedcomId

    /**
     * Removes a person from every family they belong to, leaving the person alone.
     *
     * Kept apart from deletion because they answer different mistakes: unlinking fixes a
     * relationship recorded wrongly, deleting removes somebody entered by accident. The
     * original offered both for the same reason.
     */
    fun unlinkFromFamilies(personId: Long) = viewModelScope.launch {
        val families = familyRepository.getParentFamilies(personId) +
            familyRepository.getSpouseFamilies(personId)
        families.forEach { familyRepository.removeMember(it.id, personId) }
        // A family that has lost its second member is no longer a family.
        familyRepository.pruneUnderpopulatedFamilies(treeId)
        dismissCardMenu()
        load()
    }

    fun deletePerson(personId: Long) = viewModelScope.launch {
        val wasFulcrum = _uiState.value.cardMenu?.gedcomId == currentFulcrum()
        deletePerson.invoke(personId)
        // The diagram is drawn around the fulcrum; deleting it leaves nothing to draw
        // around, so the next redraw has to pick a new one.
        if (wasFulcrum) fulcrumGedcomId = null
        dismissCardMenu()
        load()
    }

    /** Links somebody already in the tree, chosen on the picker screen. */
    fun linkExisting(pivotPersonId: Long, otherPersonId: Long, relation: Relation) =
        viewModelScope.launch {
            addRelative(treeId, pivotPersonId, otherPersonId, RelativeTarget(relation))
            load()
        }

    /**
     * The placed layout, kept so an export can reuse exactly what is on screen rather
     * than laying the tree out a second time.
     */
    private var layout: DiagramLayout? = null

    fun onLayoutReady(placed: DiagramLayout) {
        layout = placed
    }

    fun export(uri: String, format: ExportFormat, onResult: (Result<Unit>) -> Unit) =
        viewModelScope.launch {
            val placed = layout
            if (placed == null) {
                onResult(Result.failure(IllegalStateException("The diagram is not ready yet.")))
                return@launch
            }
            val density = context.resources.displayMetrics.density
            val people = _uiState.value.peopleById
            val result = runCatching {
                val stream = context.contentResolver.openOutputStream(Uri.parse(uri))
                    ?: error("This location could not be opened for writing.")
                stream.use { output ->
                    when (format) {
                        // Only the raster export cares about the screen's density; the two
                        // vector formats carry the layout's own dp figures.
                        ExportFormat.PNG -> exporter.exportPng(placed, people, density, output)
                        ExportFormat.PDF -> exporter.exportPdf(placed, people, output)
                        ExportFormat.SVG -> exporter.exportSvg(placed, people, output)
                    }
                }
            }
            onResult(result)
        }

    private fun load(showLoading: Boolean = true) = viewModelScope.launch {
        redrawing.withLock { redraw(showLoading) }
    }

    private suspend fun redraw(showLoading: Boolean) {
        if (showLoading) _uiState.value = DiagramUiState(loading = true)

        val summaries = personRepository.observePersonSummaries(treeId).first()
        if (summaries.isEmpty()) {
            _uiState.value = DiagramUiState(loading = false, isEmpty = true)
            return
        }

        // The engine needs the whole tree as one object graph, so it is materialised here
        // rather than being read record by record.
        val gedcom = runCatching { projector.project(treeId) }
            .getOrElse {
                _uiState.value = DiagramUiState(loading = false, error = it.message)
                return
            }

        val fulcrum = fulcrumGedcomId
            ?: treeRepository.getTree(treeId)?.rootPersonId
                ?.let { rootId -> summaries.firstOrNull { it.person.id == rootId } }
                ?.person?.gedcomId
            ?: summaries.firstOrNull()?.person?.gedcomId

        if (fulcrum == null) {
            _uiState.value = DiagramUiState(loading = false, isEmpty = true)
            return
        }

        val settings = settingsRepository.settings.first().diagram
        val session = engine.start(gedcom, fulcrum, settings)
        val families = familyRepository.observeFamilies(treeId).first()
        val portraitsByRowId = mediaRepository.observePersonPortraits(treeId).first()
        _uiState.value = DiagramUiState(
            session = session,
            peopleById = summaries.mapNotNull { summary ->
                summary.person.gedcomId?.let { it to summary }
            }.toMap(),
            // Re-keyed to GEDCOM ids, because that is the only identifier the engine
            // returns on a placed card.
            portraits = summaries.mapNotNull { summary ->
                val gedcomId = summary.person.gedcomId ?: return@mapNotNull null
                portraitsByRowId[summary.person.id]?.let { gedcomId to it }
            }.toMap(),
            familyIdsByGedcomId = families.mapNotNull { family ->
                family.gedcomId?.let { it to family.id }
            }.toMap(),
            loading = false,
            isEmpty = session == null,
        )
    }
}
