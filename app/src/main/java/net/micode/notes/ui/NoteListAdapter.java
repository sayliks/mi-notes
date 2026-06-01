package net.micode.notes.ui;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.entity.NoteEntity;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;

import java.util.HashSet;
import java.util.List;

public class NoteListAdapter extends ListAdapter<NoteEntity, NoteListAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(NoteEntity note, int position);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(NoteEntity note, int position);
    }

    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;
    private final HashSet<Long> selectedIds = new HashSet<>();
    private boolean choiceMode = false;

    protected NoteListAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<NoteEntity> DIFF_CALLBACK = new DiffUtil.ItemCallback<NoteEntity>() {
        @Override
        public boolean areItemsTheSame(@NonNull NoteEntity oldItem, @NonNull NoteEntity newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull NoteEntity oldItem, @NonNull NoteEntity newItem) {
            return oldItem.id == newItem.id
                    && oldItem.type == newItem.type
                    && oldItem.parentId == newItem.parentId
                    && oldItem.bgColorId == newItem.bgColorId
                    && oldItem.alertDate == newItem.alertDate
                    && oldItem.modifiedDate == newItem.modifiedDate
                    && oldItem.notesCount == newItem.notesCount
                    && oldItem.isDeleted == newItem.isDeleted
                    && java.util.Objects.equals(oldItem.title, newItem.title)
                    && java.util.Objects.equals(oldItem.content, newItem.content);
        }
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.note_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NoteEntity note = getItem(position);
        holder.bind(note, position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    // 多选相关
    public void setChoiceMode(boolean mode) {
        choiceMode = mode;
        if (!mode) selectedIds.clear();
        notifyDataSetChanged();
    }

    public boolean isInChoiceMode() {
        return choiceMode;
    }

    public void toggleSelection(long noteId) {
        if (selectedIds.contains(noteId)) {
            selectedIds.remove(noteId);
        } else {
            selectedIds.add(noteId);
        }
    }

    public void selectAll(boolean checked) {
        selectedIds.clear();
        if (checked) {
            List<NoteEntity> currentList = getCurrentList();
            for (NoteEntity note : currentList) {
                if (note.type == Notes.TYPE_NOTE) {
                    selectedIds.add(note.id);
                }
            }
        }
        notifyDataSetChanged();
    }

    public boolean isAllSelected() {
        int noteCount = 0;
        for (NoteEntity note : getCurrentList()) {
            if (note.type == Notes.TYPE_NOTE) noteCount++;
        }
        return noteCount > 0 && selectedIds.size() == noteCount;
    }

    public HashSet<Long> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    public boolean isSelected(long noteId) {
        return selectedIds.contains(noteId);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView alertIcon;
        TextView title;
        TextView time;
        TextView callName;
        CheckBox checkBox;

        ViewHolder(View itemView) {
            super(itemView);
            alertIcon = itemView.findViewById(R.id.iv_alert_icon);
            title = itemView.findViewById(R.id.tv_title);
            time = itemView.findViewById(R.id.tv_time);
            callName = itemView.findViewById(R.id.tv_name);
            checkBox = itemView.findViewById(android.R.id.checkbox);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onItemClick(getItem(pos), pos);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && longClickListener != null) {
                    return longClickListener.onItemLongClick(getItem(pos), pos);
                }
                return false;
            });
        }

        void bind(NoteEntity note, int position) {
            // 多选 checkbox
            if (choiceMode && note.type == Notes.TYPE_NOTE) {
                checkBox.setVisibility(View.VISIBLE);
                checkBox.setChecked(selectedIds.contains(note.id));
            } else {
                checkBox.setVisibility(View.GONE);
            }

            // 内容绑定
            if (note.id == Notes.ID_CALL_RECORD_FOLDER) {
                callName.setVisibility(View.GONE);
                alertIcon.setVisibility(View.VISIBLE);
                title.setTextAppearance(itemView.getContext(), R.style.TextAppearancePrimaryItem);
                title.setText(itemView.getContext().getString(R.string.call_record_folder_name)
                        + itemView.getContext().getString(R.string.format_folder_files_count, note.notesCount));
                alertIcon.setImageResource(R.drawable.call_record);
            } else if (note.parentId == Notes.ID_CALL_RECORD_FOLDER) {
                callName.setVisibility(View.VISIBLE);
                callName.setText("");
                title.setTextAppearance(itemView.getContext(), R.style.TextAppearanceSecondaryItem);
                title.setText(DataUtils.getFormattedSnippet(note.title != null ? note.title : ""));
                if (note.alertDate > 0) {
                    alertIcon.setImageResource(R.drawable.clock);
                    alertIcon.setVisibility(View.VISIBLE);
                } else {
                    alertIcon.setVisibility(View.GONE);
                }
            } else {
                callName.setVisibility(View.GONE);
                title.setTextAppearance(itemView.getContext(), R.style.TextAppearancePrimaryItem);
                if (note.type == Notes.TYPE_FOLDER) {
                    title.setText((note.title != null ? note.title : "")
                            + itemView.getContext().getString(R.string.format_folder_files_count, note.notesCount));
                    alertIcon.setVisibility(View.GONE);
                } else {
                    title.setText(DataUtils.getFormattedSnippet(note.title != null ? note.title : ""));
                    if (note.alertDate > 0) {
                        alertIcon.setImageResource(R.drawable.clock);
                        alertIcon.setVisibility(View.VISIBLE);
                    } else {
                        alertIcon.setVisibility(View.GONE);
                    }
                }
            }
            time.setText(DateUtils.getRelativeTimeSpanString(note.modifiedDate));

            // 背景色
            setBackground(note, position);
        }

        private void setBackground(NoteEntity note, int position) {
            List<NoteEntity> list = getCurrentList();
            int size = list.size();

            if (note.type == Notes.TYPE_NOTE) {
                boolean isFirst = position == 0;
                boolean isLast = position == size - 1;
                boolean isSingle = size == 1;

                // 判断是否紧跟文件夹之后
                boolean followsFolder = false;
                if (!isFirst && position > 0) {
                    int prevType = list.get(position - 1).type;
                    followsFolder = (prevType == Notes.TYPE_FOLDER || prevType == Notes.TYPE_SYSTEM);
                }

                // 判断文件夹后面是否只有一个便签
                boolean oneFollowingFolder = false;
                boolean multiFollowingFolder = false;
                if (followsFolder) {
                    boolean hasNext = position + 1 < size;
                    if (!hasNext) {
                        oneFollowingFolder = true;
                    } else {
                        int nextType = list.get(position + 1).type;
                        if (nextType == Notes.TYPE_NOTE) {
                            multiFollowingFolder = true;
                        } else {
                            oneFollowingFolder = true;
                        }
                    }
                }

                int bgId = note.bgColorId;
                if (isSingle || oneFollowingFolder) {
                    itemView.setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(bgId));
                } else if (isLast) {
                    itemView.setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(bgId));
                } else if (isFirst || multiFollowingFolder) {
                    itemView.setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(bgId));
                } else {
                    itemView.setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(bgId));
                }
            } else {
                itemView.setBackgroundResource(NoteItemBgResources.getFolderBgRes());
            }
        }
    }
}
