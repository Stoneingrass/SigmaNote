
package com.sigmanote.notes.ui.note.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sigmanote.notes.R
import com.sigmanote.notes.databinding.ItemHeaderBinding
import com.sigmanote.notes.databinding.ItemMessageBinding
import com.sigmanote.notes.databinding.ItemNoteLabelBinding
import com.sigmanote.notes.databinding.ItemNoteListBinding
import com.sigmanote.notes.databinding.ItemNoteListItemBinding
import com.sigmanote.notes.databinding.ItemNoteTextBinding
import com.sigmanote.notes.model.PrefsManager
import com.sigmanote.notes.ui.note.SwipeAction

class NoteAdapter(
    val context: Context,
    val callback: Callback,
    val prefsManager: PrefsManager
) : ListAdapter<NoteListItem, RecyclerView.ViewHolder>(NoteListDiffCallback()) {

    private val listNoteItemViewHolderPool = ArrayDeque<ListNoteItemViewHolder>()

    private val labelViewHolderPool = ArrayDeque<LabelChipViewHolder>()

    private val itemTouchHelper = ItemTouchHelper(SwipeTouchHelperCallback(callback))

    // Used by view holders with highlighted text.
    val highlightBackgroundColor = ContextCompat.getColor(context, R.color.color_highlight)
    val highlightForegroundColor = ContextCompat.getColor(context, R.color.color_on_highlight)

    init {
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ViewType.MESSAGE.ordinal -> MessageViewHolder(ItemMessageBinding
                .inflate(inflater, parent, false))
            ViewType.HEADER.ordinal -> HeaderViewHolder(ItemHeaderBinding
                .inflate(inflater, parent, false))
            ViewType.TEXT_NOTE.ordinal -> TextNoteViewHolder(ItemNoteTextBinding
                .inflate(inflater, parent, false))
            ViewType.LIST_NOTE.ordinal -> ListNoteViewHolder(ItemNoteListBinding
                .inflate(inflater, parent, false))
            else -> error("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is MessageViewHolder -> holder.bind(item as MessageItem, this)
            is HeaderViewHolder -> holder.bind(item as HeaderItem)
            is TextNoteViewHolder -> {
                // [onViewRecycled] is not always called so unbinding is also done here.
                holder.unbind(this)
                holder.bind(this, item as NoteItemText)
            }
            is ListNoteViewHolder -> {
                // [onViewRecycled] is not always called so unbinding is also done here.
                holder.unbind(this)
                holder.bind(this, item as NoteItemList)
            }
        }
    }

    override fun getItemViewType(position: Int) = getItem(position).type.ordinal

    override fun getItemId(position: Int) = getItem(position).id

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        // Used to recycle secondary view holders
        if (holder is NoteViewHolder<*>) {
            holder.unbind(this)
        }
    }

    @SuppressLint("InflateParams")
    fun obtainListNoteItemViewHolder(): ListNoteItemViewHolder =
        if (listNoteItemViewHolderPool.isNotEmpty()) {
            listNoteItemViewHolderPool.removeLast()
        } else {
            ListNoteItemViewHolder(ItemNoteListItemBinding.inflate(
                LayoutInflater.from(context), null, false))
        }

    @SuppressLint("InflateParams")
    fun obtainLabelViewHolder(): LabelChipViewHolder =
        if (labelViewHolderPool.isNotEmpty()) {
            labelViewHolderPool.removeLast()
        } else {
            LabelChipViewHolder(ItemNoteLabelBinding.inflate(
                LayoutInflater.from(context), null, false))
        }

    fun freeListNoteItemViewHolder(viewHolder: ListNoteItemViewHolder) {
        listNoteItemViewHolderPool += viewHolder
    }

    fun freeLabelViewHolder(viewHolder: LabelChipViewHolder) {
        labelViewHolderPool += viewHolder
    }

    fun updateForListLayoutChange() {
        notifyItemRangeChanged(0, itemCount)
    }

    enum class ViewType {
        MESSAGE,
        HEADER,
        TEXT_NOTE,
        LIST_NOTE
    }

    enum class SwipeDirection {
        LEFT, RIGHT
    }

    interface Callback {
        fun onNoteItemClicked(item: NoteItem, pos: Int)

        fun onNoteItemLongClicked(item: NoteItem, pos: Int)

        fun onMessageItemDismissed(item: MessageItem, pos: Int)

        fun onNoteActionButtonClicked(item: NoteItem, pos: Int)

        fun getNoteSwipeAction(direction: SwipeDirection): SwipeAction

        fun onNoteSwiped(pos: Int, direction: NoteAdapter.SwipeDirection)

        val strikethroughCheckedItems: Boolean
    }
}
