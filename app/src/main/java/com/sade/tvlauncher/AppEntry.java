package com.sade.tvlauncher;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** Launcher listesinde gösterilen tek bir uygulama. */
public final class AppEntry {

    /** "paket/aktivite" biçiminde sabit anahtar (favoriler için kullanılır). */
    public final String key;
    public final ComponentName component;
    public final String label;
    public final Drawable icon;
    public final boolean system;

    /** İkondan çıkarılan baskın renk (0 = bulunamadı). Kart degradesi bundan üretilir. */
    public final int dominantColor;

    /** Yerleşik durum değil, favori listesinden türetilir. */
    public boolean favorite;

    public AppEntry(ComponentName component, String label, Drawable icon, boolean system,
                    boolean favorite, int dominantColor) {
        this.component = component;
        this.key = component.flattenToShortString();
        this.label = label;
        this.icon = icon;
        this.system = system;
        this.favorite = favorite;
        this.dominantColor = dominantColor;
    }
}
