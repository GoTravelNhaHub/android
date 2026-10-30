package com.gotravelnha.app.ui.planner

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gotravelnha.app.R
import com.gotravelnha.app.data.AdaptFrom
import com.gotravelnha.app.data.AgentTurn
import com.gotravelnha.app.data.ApiException
import com.gotravelnha.app.data.ChatBody
import com.gotravelnha.app.data.ChatMessage
import com.gotravelnha.app.data.ChatPlan
import com.gotravelnha.app.data.PlannerBody
import com.gotravelnha.app.data.PlannerResult
import com.gotravelnha.app.data.Revise
import com.gotravelnha.app.ui.AppState
import com.gotravelnha.app.ui.PlannerSeed
import com.gotravelnha.app.ui.components.userMessage
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val MAX_TRIP_DAYS = 10

/** Limits of /api/planner/chat */
private const val MAX_MESSAGES = 40
private const val MAX_MESSAGE_LENGTH = 800

/** A chat bubble. Local notes come from the app, so they are not sent to the agent. */
data class ChatNote(
    val id: Int,
    val fromUser: Boolean,
    val text: String,
    val local: Boolean = false,
    val changes: List<String> = emptyList(),
    val replies: List<String> = emptyList(),
    val showsPlan: Boolean = false,
)

enum class PlannerView { Chat, Plan }

class PlannerModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()

    var brief by mutableStateOf(PlannerBrief())
    var adapt by mutableStateOf<PlannerSeed?>(null)
    val notes = mutableStateListOf<ChatNote>()
    var draft by mutableStateOf("")
    var view by mutableStateOf(PlannerView.Chat)

    var thinking by mutableStateOf(false)
        private set
    var generating by mutableStateOf(false)
        private set
    var working by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var result by mutableStateOf<PlannerResult?>(null)
        private set
    var savedTripId by mutableStateOf<String?>(null)
        private set

    private var nextNoteId by mutableIntStateOf(0)

    val busy get() = thinking || generating
    val started get() = notes.any { it.fromUser } || result != null || brief != PlannerBrief()

    init {
        welcome()
    }

    private fun welcome() {
        addNote(
            fromUser = false,
            text = context.getString(R.string.chat_welcome),
            local = true,
            replies = listOf(context.getString(R.string.chat_welcome_reply_1), context.getString(R.string.chat_welcome_reply_2)),
        )
    }

    private fun addNote(fromUser: Boolean, text: String, local: Boolean = false, changes: List<String> = emptyList(), replies: List<String> = emptyList(), showsPlan: Boolean = false) {
        notes += ChatNote(nextNoteId++, fromUser, text, local, changes, replies, showsPlan)
    }

    fun startOver() {
        brief = PlannerBrief()
        adapt = null
        result = null
        savedTripId = null
        error = null
        draft = ""
        view = PlannerView.Chat
        notes.clear()
        welcome()
    }

    fun applySeed(seed: PlannerSeed) {
        startOver()
        notes.clear()
        val start = LocalDate.now().plusDays(14)
        brief = PlannerBrief(
            destination = seed.destination,
            startDate = seed.days?.let { start.toString() },
            endDate = seed.days?.let { start.plusDays((it.coerceIn(1, MAX_TRIP_DAYS) - 1).toLong()).toString() },
        )
        adapt = seed.takeIf { it.adaptFromId != null }
        val text = adapt?.let { context.getString(R.string.chat_adapt_ready, it.adaptTitle.orEmpty()) }
            ?: context.getString(R.string.chat_destination_ready, seed.destination)
        addNote(fromUser = false, text = text, local = true)
    }

    /** One message from the family: the agent updates the brief, then picks the next step. */
    fun send(app: AppState, language: String, message: String) {
        val spoken = message.trim()
        if (busy || spoken.isEmpty()) return
        draft = ""
        addNote(fromUser = true, text = spoken)
        val sentId = notes.last().id
        thinking = true
        viewModelScope.launch {
            val outcome = runCatching { app.api.plannerChat(chatBody(language)) }
            thinking = false
            outcome.onSuccess { handle(it, spoken, app, language) }.onFailure { failure ->
                // Give the message back so nothing is typed twice
                notes.removeAll { it.id == sentId }
                draft = spoken
                addNote(fromUser = false, text = failureText(failure, language, R.string.chat_failed), local = true)
            }
        }
    }

    private fun chatBody(language: String) = ChatBody(
        messages = notes.filter { !it.local && it.text.isNotBlank() }.takeLast(MAX_MESSAGES)
            .map { ChatMessage(if (it.fromUser) "user" else "assistant", it.text.take(MAX_MESSAGE_LENGTH)) },
        brief = brief.toAgentBrief(),
        language = language,
        plan = result?.itinerary?.let { plan -> ChatPlan(plan.title.take(200), plan.days.map { it.theme.take(200) }) },
    )

    private fun handle(turn: AgentTurn, spoken: String, app: AppState, language: String) {
        val destinationChanged = turn.updates.destination.let { it != null && it != brief.destination }
        val (updated, changes) = brief.applying(turn.updates)
        brief = updated
        addNote(fromUser = false, text = turn.reply, changes = changes.map(::describe), replies = turn.quickReplies)
        when (turn.action) {
            "plan" -> plan(app, language)
            // A new city is a new trip, not an edit of the old one
            "revise" -> if (destinationChanged) plan(app, language) else generate(app, language, turn.change ?: spoken)
        }
    }

    private fun describe(change: BriefChange): String = when (change) {
        is BriefChange.Destination -> context.getString(R.string.chat_update_destination, change.value)
        is BriefChange.Dates -> context.getString(R.string.chat_update_dates, dateRange(change.start, change.end))
        is BriefChange.Travellers -> context.getString(R.string.chat_update_travellers, change.ages.size, change.ages.joinToString(", "))
        is BriefChange.Budget -> context.getString(R.string.chat_update_budget, budgetLabel(change.value))
        is BriefChange.Pace -> context.getString(R.string.chat_update_pace, context.getString(paceLabel(change.value)))
        is BriefChange.Interests -> context.getString(R.string.chat_update_interests, change.value)
    }

    /** Builds the plan, or asks for what is still missing. */
    fun plan(app: AppState, language: String) {
        if (busy) return
        val question = when (brief.missing.firstOrNull()) {
            BriefField.Destination -> R.string.chat_ask_city
            BriefField.Dates -> R.string.chat_ask_dates
            BriefField.Ages -> R.string.chat_ask_ages
            null -> null
        }
        if (question != null) addNote(fromUser = false, text = context.getString(question), local = true) else generate(app, language, null)
    }

    private fun generate(app: AppState, language: String, change: String?) {
        val previous = result
        val revise = if (change != null && previous != null) Revise(previous.generationId, change.take(300)) else null
        val start = brief.startDate
        val end = brief.endDate
        if (start == null || end == null) return
        generating = true
        error = null
        viewModelScope.launch {
            runCatching {
                app.api.plan(
                    PlannerBody(
                        destination = brief.destination.trim(),
                        startDate = start,
                        endDate = end,
                        party = brief.party,
                        budget = brief.budget,
                        pace = brief.pace,
                        interests = brief.interests.trim().ifBlank { null },
                        language = language,
                        revise = revise,
                        adaptFrom = if (revise == null) adapt?.adaptFromId?.let { AdaptFrom(it) } else null,
                    ),
                )
            }.onSuccess { plan ->
                result = plan
                savedTripId = null
                val days = plan.itinerary.days.size
                val destination = brief.destination.trim()
                addNote(
                    fromUser = false,
                    text = context.getString(if (revise != null) R.string.chat_plan_updated else R.string.chat_plan_ready, days, destination),
                    replies = if (revise != null) emptyList() else listOf(R.string.chat_plan_reply_1, R.string.chat_plan_reply_2, R.string.chat_plan_reply_3).map(context::getString),
                    showsPlan = true,
                )
                // Show a first plan straight away; changes stay in the conversation
                if (previous == null) view = PlannerView.Plan
            }.onFailure { failure ->
                val text = if ((failure as? ApiException)?.status == 429) {
                    context.getString(if (app.signedIn) R.string.chat_limit_user else R.string.chat_limit_guest)
                } else {
                    failureText(failure, language, R.string.planner_failed)
                }
                addNote(fromUser = false, text = text, local = true)
            }
            generating = false
        }
    }

    /** Backend messages are in English, so other languages get the translated fallback. */
    private fun failureText(failure: Throwable, language: String, fallback: Int) =
        failure.userMessage()?.takeIf { language == "en" } ?: context.getString(fallback)

    fun save(app: AppState, onSaved: (String) -> Unit) {
        val generation = result ?: return
        working = true
        viewModelScope.launch {
            runCatching { app.api.saveGeneration(generation.generationId) }
                .onSuccess { savedTripId = it; onSaved(it) }
                .onFailure { error = it.userMessage() ?: context.getString(R.string.error_generic) }
            working = false
        }
    }
}

fun dateRange(start: String, end: String): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return "${LocalDate.parse(start).format(format)} – ${LocalDate.parse(end).format(format)}"
}

fun budgetLabel(value: Double) = "S$${"%,d".format(value.toLong())}"

fun paceLabel(pace: String) = when (pace) {
    "relaxed" -> R.string.pace_relaxed
    "packed" -> R.string.pace_packed
    else -> R.string.pace_balanced
}
