package com.github.matheuscruzsouza.androidserver.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.matheuscruzsouza.androidserver.R;
import com.github.matheuscruzsouza.androidserver.model.IamUser;

import java.util.ArrayList;
import java.util.List;

public class UserAdminAdapter extends RecyclerView.Adapter<UserAdminAdapter.UserViewHolder> {

    public interface OnUserActionListener {
        void onDeleteUser(IamUser user);
        void onManageUser(IamUser user);
    }

    private List<IamUser> userList = new ArrayList<>();
    private final OnUserActionListener listener;

    public UserAdminAdapter(OnUserActionListener listener) {
        this.listener = listener;
    }

    public void updateData(List<IamUser> newList) {
        this.userList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_admin, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        IamUser user = userList.get(position);
        holder.tvUserName.setText(user.getName());
        holder.tvUserHandle.setText(user.getAccessKey());

        String secret = user.getSecretKey();
        if (secret != null && secret.length() > 6) {
            holder.tvUserSecret.setText("Key: " + secret.substring(0, 4) + "•••" + secret.substring(secret.length() - 2));
        } else {
            holder.tvUserSecret.setText("Key: ••••••••");
        }
        
        String policy = user.getPolicy() != null ? user.getPolicy() : "ReadOnly";
        if (!user.isEnabled()) {
            holder.tvUserPolicy.setText("Desativado");
            holder.tvUserPolicy.setTextColor(Color.parseColor("#8E8E93"));
            holder.itemView.setAlpha(0.6f);
        } else {
            holder.itemView.setAlpha(1.0f);
            holder.tvUserPolicy.setText(policy);
            if ("Admin".equalsIgnoreCase(policy)) {
                holder.tvUserPolicy.setTextColor(Color.parseColor("#FF453A"));
            } else if ("WriteOnly".equalsIgnoreCase(policy)) {
                holder.tvUserPolicy.setTextColor(Color.parseColor("#FF9F0A"));
            } else {
                holder.tvUserPolicy.setTextColor(Color.parseColor("#0A84FF"));
            }
        }

        holder.btnConfigUser.setOnClickListener(v -> {
            if (listener != null) {
                listener.onManageUser(user);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteUser(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName;
        TextView tvUserHandle;
        TextView tvUserSecret;
        TextView tvUserPolicy;
        Button btnConfigUser;
        Button btnDelete;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUserHandle = itemView.findViewById(R.id.tvUserHandle);
            tvUserSecret = itemView.findViewById(R.id.tvUserSecret);
            tvUserPolicy = itemView.findViewById(R.id.tvUserPolicy);
            btnConfigUser = itemView.findViewById(R.id.btnConfigUser);
            btnDelete = itemView.findViewById(R.id.btnDeleteUser);
        }
    }
}
