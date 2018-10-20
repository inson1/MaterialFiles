/*
 * Copyright (c) 2018 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.materialfilemanager.filelist;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.util.SortedList;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.signature.ObjectKey;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import me.zhanghai.android.materialfilemanager.R;
import me.zhanghai.android.materialfilemanager.file.FormatUtils;
import me.zhanghai.android.materialfilemanager.file.MimeTypes;
import me.zhanghai.android.materialfilemanager.filesystem.File;
import me.zhanghai.android.materialfilemanager.glide.GlideApp;
import me.zhanghai.android.materialfilemanager.glide.IgnoreErrorDrawableImageViewTarget;
import me.zhanghai.android.materialfilemanager.ui.AnimatedSortedListAdapter;
import me.zhanghai.android.materialfilemanager.ui.CheckableFrameLayout;
import me.zhanghai.android.materialfilemanager.ui.CheckableItemBackground;
import me.zhanghai.android.materialfilemanager.util.StringCompat;
import me.zhanghai.android.materialfilemanager.util.ViewUtils;

public class FileListAdapter extends AnimatedSortedListAdapter<File, FileListAdapter.ViewHolder>
        implements FastScrollRecyclerView.SectionedAdapter {

    private static final Object PAYLOAD_SELECTED_CHANGED = new Object();

    private Comparator<File> mComparator;
    private final SortedList.Callback<File> mCallback = new SortedListAdapterCallback<File>(this) {
        @Override
        public int compare(File file1, File file2) {
            return mComparator.compare(file1, file2);
        }
        @Override
        public boolean areItemsTheSame(File oldItem, File newItem) {
            return Objects.equals(oldItem, newItem);
        }
        @Override
        public boolean areContentsTheSame(File oldItem, File newItem) {
            return oldItem == newItem || oldItem != null && oldItem.equalsIncludingInformation(
                    newItem);
        }
    };

    private Set<File> mSelectedFiles = new HashSet<>();
    private Map<File, Integer> mFilePositionMap = new HashMap<>();

    private FilePasteMode mPasteMode;

    private Fragment mFragment;
    private Listener mListener;

    public FileListAdapter(Fragment fragment, Listener listener) {
        init(File.class, mCallback);
        mFragment = fragment;
        mListener = listener;
    }

    public void setComparator(Comparator<File> comparator) {
        mComparator = comparator;
        refresh();
        rebuildFilePositionMap();
    }

    public void replaceSelectedFiles(Set<File> selectedFiles) {
        Set<File> selectedChangedUris = new HashSet<>();
        for (Iterator<File> iterator = mSelectedFiles.iterator(); iterator.hasNext(); ) {
            File file = iterator.next();
            if (!selectedFiles.contains(file)) {
                iterator.remove();
                selectedChangedUris.add(file);
            }
        }
        for (File file : selectedFiles) {
            if (!mSelectedFiles.contains(file)) {
                mSelectedFiles.add(file);
                selectedChangedUris.add(file);
            }
        }
        for (File file : selectedChangedUris) {
            Integer position = mFilePositionMap.get(file);
            if (position != null) {
                notifyItemChanged(position, PAYLOAD_SELECTED_CHANGED);
            }
        }
    }

    @Override
    public void clear() {
        super.clear();

        rebuildFilePositionMap();
    }

    @Override
    public void replace(List<File> list) {
        super.replace(list);

        rebuildFilePositionMap();
    }

    private void rebuildFilePositionMap() {
        mFilePositionMap.clear();
        for (int i = 0, count = getItemCount(); i < count; ++i) {
            File file = getItem(i);
            mFilePositionMap.put(file, i);
        }
    }

    public void setPasteMode(FilePasteMode pasteMode) {
        mPasteMode = pasteMode;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(ViewUtils.inflate(R.layout.file_item, parent));
        holder.itemLayout.setBackground(CheckableItemBackground.create(
                holder.itemLayout.getContext()));
        holder.menu = new PopupMenu(holder.menuButton.getContext(), holder.menuButton);
        holder.menu.inflate(R.menu.file_item);
        holder.menuButton.setOnClickListener(view -> holder.menu.show());
        holder.menuButton.setOnTouchListener(holder.menu.getDragToOpenListener());
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        File file = getItem(position);
        holder.itemLayout.setChecked(mSelectedFiles.contains(file));
        if (!payloads.isEmpty()) {
            return;
        }
        bindViewHolderAnimation(holder);
        holder.itemView.setOnClickListener(view -> {
            if (mSelectedFiles.isEmpty() || mPasteMode != FilePasteMode.NONE) {
                mListener.onOpenFile(file);
            } else {
                mListener.onSelectFile(file, !mSelectedFiles.contains(file));
            }
        });
        holder.itemLayout.setOnLongClickListener(view -> {
            if (mSelectedFiles.isEmpty() || mPasteMode != FilePasteMode.NONE) {
                mListener.onSelectFile(file, !mSelectedFiles.contains(file));
            } else {
                mListener.onOpenFile(file);
            }
            return true;
        });
        String mimeType = file.getMimeType();
        Drawable icon = AppCompatResources.getDrawable(holder.iconImage.getContext(),
                MimeTypes.getIconRes(mimeType));
        if (MimeTypes.supportsThumbnail(mimeType)) {
            GlideApp.with(mFragment)
                    .load(file.getUri())
                    .signature(new ObjectKey(file.getLastModificationTime()))
                    .placeholder(icon)
                    .into(new IgnoreErrorDrawableImageViewTarget(holder.iconImage));
        } else {
            GlideApp.with(mFragment)
                    .clear(holder.iconImage);
            holder.iconImage.setImageDrawable(icon);
        }
        holder.iconImage.setOnClickListener(view -> mListener.onSelectFile(file,
                !mSelectedFiles.contains(file)));
        Integer badgeIconRes;
        if (file.isSymbolicLink()) {
            badgeIconRes = file.isSymbolicLinkBroken() ? R.drawable.error_badge_icon_18dp
                    : R.drawable.symbolic_link_badge_icon_18dp;
        } else {
            badgeIconRes = null;
        }
        boolean hasBadge = badgeIconRes != null;
        ViewUtils.setVisibleOrGone(holder.badgeImage, hasBadge);
        if (hasBadge) {
            holder.badgeImage.setImageResource(badgeIconRes);
        }
        holder.nameText.setText(file.getName());
        String description;
        if (file.isDirectory()) {
            description = null;
        } else {
            Context context = holder.descriptionText.getContext();
            String descriptionSeparator = context.getString(
                    R.string.file_item_description_separator);
            String lastModificationTime = FormatUtils.formatShortTime(
                    file.getLastModificationTime(), context);
            String size = FormatUtils.formatHumanReadableSize(file.getSize(), context);
            description = StringCompat.join(descriptionSeparator, lastModificationTime, size);
        }
        holder.descriptionText.setText(description);
        holder.menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.action_open_as:
                    mListener.onOpenFileAs(file);
                    return true;
                case R.id.action_cut:
                    mListener.onCutFile(file);
                    return true;
                case R.id.action_copy:
                    mListener.onCopyFile(file);
                    return true;
                case R.id.action_delete:
                    mListener.onDeleteFile(file);
                    return true;
                case R.id.action_rename:
                    mListener.onRenameFile(file);
                    return true;
                case R.id.action_send:
                    mListener.onSendFile(file);
                    return true;
                case R.id.action_copy_path:
                    mListener.onCopyPath(file);
                    return true;
                case R.id.action_properties:
                    mListener.onOpenProperties(file);
                    return true;
                default:
                    return false;
            }
        });
    }

    @NonNull
    @Override
    public String getSectionName(int position) {
        File file = getItem(position);
        String name = file.getName();
        if (TextUtils.isEmpty(name)) {
            return "";

        }
        return name.substring(0, 1).toUpperCase();
    }

    public interface Listener {
        void onSelectFile(File file, boolean selected);
        void onOpenFile(File file);
        void onOpenFileAs(File file);
        void onCutFile(File file);
        void onCopyFile(File file);
        void onDeleteFile(File file);
        void onRenameFile(File file);
        void onSendFile(File file);
        void onCopyPath(File file);
        void onOpenProperties(File file);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.item)
        public CheckableFrameLayout itemLayout;
        @BindView(R.id.icon)
        public ImageView iconImage;
        @BindView(R.id.badge)
        public ImageView badgeImage;
        @BindView(R.id.name)
        public TextView nameText;
        @BindView(R.id.description)
        public TextView descriptionText;
        @BindView(R.id.menu)
        public ImageButton menuButton;

        public PopupMenu menu;

        public ViewHolder(View itemView) {
            super(itemView);

            ButterKnife.bind(this, itemView);
        }
    }
}
