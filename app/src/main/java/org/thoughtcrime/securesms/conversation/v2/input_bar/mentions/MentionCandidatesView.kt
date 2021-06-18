package org.thoughtcrime.securesms.conversation.v2.input_bar.mentions

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.utilities.toPx
import org.thoughtcrime.securesms.mms.GlideRequests
import org.session.libsession.messaging.mentions.Mention

class MentionCandidatesView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : ListView(context, attrs, defStyleAttr) {
    private var candidates = listOf<Mention>()
        set(newValue) { field = newValue; snAdapter.mentionCandidates = newValue }
    var glide: GlideRequests? = null
        set(newValue) { field = newValue; snAdapter.glide = newValue }
    var openGroupServer: String? = null
        set(newValue) { field = newValue; snAdapter.openGroupServer = openGroupServer }
    var openGroupRoom: String? = null
        set(newValue) { field = newValue; snAdapter.openGroupRoom = openGroupRoom }
    var onCandidateSelected: ((Mention) -> Unit)? = null

    private val snAdapter by lazy { Adapter(context) }

    private class Adapter(private val context: Context) : BaseAdapter() {
        var mentionCandidates = listOf<Mention>()
            set(newValue) { field = newValue; notifyDataSetChanged() }
        var glide: GlideRequests? = null
        var openGroupServer: String? = null
        var openGroupRoom: String? = null

        override fun getCount(): Int { return mentionCandidates.count() }
        override fun getItemId(position: Int): Long { return position.toLong() }
        override fun getItem(position: Int): Mention { return mentionCandidates[position] }

        override fun getView(position: Int, cellToBeReused: View?, parent: ViewGroup): View {
            val cell = cellToBeReused as MentionCandidateView? ?: MentionCandidateView.inflate(LayoutInflater.from(context), parent)
            val mentionCandidate = getItem(position)
            cell.glide = glide
            cell.candidate = mentionCandidate
            cell.openGroupServer = openGroupServer
            cell.openGroupRoom = openGroupRoom
            return cell
        }
    }

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)

    init {
        clipToOutline = true
        adapter = snAdapter
        snAdapter.mentionCandidates = candidates
        setOnItemClickListener { _, _, position, _ ->
            onCandidateSelected?.invoke(candidates[position])
        }
    }

    fun show(mentionCandidates: List<Mention>, threadID: Long) {
        val openGroup = DatabaseFactory.getLokiThreadDatabase(context).getOpenGroupChat(threadID)
        if (openGroup != null) {
            openGroupServer = openGroup.server
            openGroupRoom = openGroup.room
        }
        this.candidates = mentionCandidates
        val layoutParams = this.layoutParams as ViewGroup.LayoutParams
        layoutParams.height = toPx(Math.min(mentionCandidates.count(), 4) * 44, resources)
        this.layoutParams = layoutParams
    }

    fun hide() {
        val layoutParams = this.layoutParams as ViewGroup.LayoutParams
        layoutParams.height = 0
        this.layoutParams = layoutParams
    }
}