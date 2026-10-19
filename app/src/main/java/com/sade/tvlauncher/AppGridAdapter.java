package com.sade.tvlauncher;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Uygulama ızgarası ve favori şeridi için ortak adaptör (iki farklı item düzeni ile). */
public class AppGridAdapter extends RecyclerView.Adapter<AppGridAdapter.Holder> {

    public interface Listener {
        void onAppClick(AppEntry entry);

        /** true dönerse uzun basma tüketilmiş sayılır. */
        boolean onAppLongClick(AppEntry entry, boolean fromFavorites);
    }

    private final List<AppEntry> items = new ArrayList<>();
    private final Listener listener;
    private final int itemLayout;
    private final boolean favorites;
    private int itemWidth = -1;
    private int itemHeight = -1;

    public AppGridAdapter(Listener listener, int itemLayout, boolean favorites) {
        this.listener = listener;
        this.itemLayout = itemLayout;
        this.favorites = favorites;
        setHasStableIds(true);
    }

    public void setItems(List<AppEntry> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    /**
     * Izgara kartlarının piksel boyutunu ayarlar (favori şeridi için çağrılmaz).
     * Kart sayısı arttıkça kartlar küçültülerek daha çok uygulama ekrana sığdırılır.
     */
    public void setItemSize(int widthPx, int heightPx) {
        if (widthPx == itemWidth && heightPx == itemHeight) return;
        itemWidth = widthPx;
        itemHeight = heightPx;
        notifyDataSetChanged();
    }

    public int indexOf(AppEntry entry) {
        return items.indexOf(entry);
    }

    public void refreshItem(AppEntry entry) {
        int position = indexOf(entry);
        if (position >= 0) notifyItemChanged(position);
    }

    /** İki kartın yerini değiştirir (taşıma modu). */
    public void moveItem(int from, int to) {
        if (from == to) return;
        if (from < 0 || from >= items.size() || to < 0 || to >= items.size()) return;
        Collections.swap(items, from, to);
        notifyItemMoved(from, to);
    }

    public AppEntry itemAt(int position) {
        if (position < 0 || position >= items.size()) return null;
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).key.hashCode();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(itemLayout, parent, false);
        return new Holder(view, this);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final AppGridAdapter adapter;
        private final ImageView icon;
        private final TextView label;
        private final TextView star;

        Holder(@NonNull View itemView, AppGridAdapter adapter) {
            super(itemView);
            this.adapter = adapter;
            this.icon = itemView.findViewById(R.id.item_icon);
            this.label = itemView.findViewById(R.id.item_label);
            this.star = itemView.findViewById(R.id.item_star);

            itemView.setOnClickListener(v -> {
                AppEntry entry = adapter.itemAt(getBindingAdapterPosition());
                if (entry != null) adapter.listener.onAppClick(entry);
            });
            itemView.setOnLongClickListener(v -> {
                AppEntry entry = adapter.itemAt(getBindingAdapterPosition());
                return entry != null && adapter.listener.onAppLongClick(entry, adapter.favorites);
            });
        }

        void bind(AppEntry entry) {
            if (adapter.itemWidth > 0 && adapter.itemHeight > 0) {
                applySize(adapter.itemWidth, adapter.itemHeight);
            }
            icon.setImageDrawable(entry.icon);
            label.setText(entry.label);
            if (star != null) star.setVisibility(entry.favorite ? View.VISIBLE : View.GONE);
            // Kart zemini ikonun baskın renginden türetilen 45° degrade
            itemView.setBackground(TileBackgrounds.of(itemView.getContext(), entry));
        }

        /** Kart küçüldükçe ikon, yazı ve iç boşlukları orantılı olarak küçültür. */
        private void applySize(int widthPx, int heightPx) {
            float density = itemView.getResources().getDisplayMetrics().density;
            int dp5 = Math.round(5 * density);
            int dp8 = Math.round(8 * density);

            ViewGroup.LayoutParams lp = itemView.getLayoutParams();
            if (lp != null && (lp.width != widthPx || lp.height != heightPx)) {
                lp.width = widthPx;
                lp.height = heightPx;
                itemView.setLayoutParams(lp);
            }

            int pad = Math.max(dp5, Math.min(dp8, Math.round(heightPx * 0.065f)));
            if (itemView.getPaddingLeft() != pad || itemView.getPaddingTop() != pad
                    || itemView.getPaddingRight() != pad || itemView.getPaddingBottom() != pad) {
                itemView.setPadding(pad, pad, pad, pad);
            }

            boolean compact = heightPx < Math.round(96 * density);

            int iconSide = Math.min(Math.round(widthPx * 0.46f), heightPx - Math.round(30 * density));
            iconSide = Math.max(Math.round(26 * density), Math.min(Math.round(64 * density), iconSide));
            ViewGroup.LayoutParams iconParams = icon.getLayoutParams();
            if (iconParams != null && (iconParams.width != iconSide || iconParams.height != iconSide)) {
                iconParams.width = iconSide;
                iconParams.height = iconSide;
                icon.setLayoutParams(iconParams);
            }

            int marginTop = compact ? Math.round(3 * density) : dp8;
            ViewGroup.LayoutParams labelParams = label.getLayoutParams();
            if (labelParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) labelParams;
                if (margins.topMargin != marginTop) {
                    margins.topMargin = marginTop;
                    label.setLayoutParams(margins);
                }
            }
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 11f : 14f);

            if (star != null) {
                int starSide = compact ? Math.round(16 * density) : Math.round(18 * density);
                ViewGroup.LayoutParams starParams = star.getLayoutParams();
                if (starParams != null
                        && (starParams.width != starSide || starParams.height != starSide)) {
                    starParams.width = starSide;
                    starParams.height = starSide;
                    star.setLayoutParams(starParams);
                    star.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 9f : 10f);
                }
            }
        }
    }
}
