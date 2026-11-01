package com.github.matheuscruzsouza.androidserver.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.matheuscruzsouza.androidserver.R;
import com.github.matheuscruzsouza.androidserver.model.S3ObjectItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileAdminAdapter extends RecyclerView.Adapter<FileAdminAdapter.FileViewHolder> {

    public interface OnFileActionListener {
        void onOpenFile(S3ObjectItem file);
        void onDeleteFile(S3ObjectItem file);
    }

    private List<S3ObjectItem> fileList = new ArrayList<>();
    private final OnFileActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

    public FileAdminAdapter(OnFileActionListener listener) {
        this.listener = listener;
    }

    public void updateData(List<S3ObjectItem> newList) {
        this.fileList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_admin, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        S3ObjectItem file = fileList.get(position);
        holder.tvFileName.setText(file.getKey());
        holder.tvFileBucket.setText(file.getBucket());

        String sizeStr = formatFileSize(file.getSize());
        String dateStr = dateFormat.format(new Date(file.getLastModified()));

        holder.tvFileDetails.setText(sizeStr + " • " + dateStr);

        holder.btnOpen.setOnClickListener(v -> {
            if (listener != null) listener.onOpenFile(file);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOpenFile(file);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteFile(file);
        });
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName;
        TextView tvFileBucket;
        TextView tvFileDetails;
        Button btnOpen;
        Button btnDelete;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileBucket = itemView.findViewById(R.id.tvFileBucket);
            tvFileDetails = itemView.findViewById(R.id.tvFileDetails);
            btnOpen = itemView.findViewById(R.id.btnOpenFile);
            btnDelete = itemView.findViewById(R.id.btnDeleteFile);
        }
    }
}
