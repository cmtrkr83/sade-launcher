package com.sade.tvlauncher;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

/**
 * Kart arka planı: uygulama ikonunun baskın renginden türetilmiş, 45° (sol alt -> sağ üst)
 * açılı yumuşak degrade. Zemin rengiyle karıştırıldığı için koyu/minimal görünüm korunur.
 */
final class TileBackgrounds {

    /** Normal durumda baskın rengin zemine karışma oranı. */
    private static final float TINT_NORMAL = 0.40f;
    /** Basılı durumda biraz daha belirgin. */
    private static final float TINT_PRESSED = 0.55f;

    private static final int CORNER_DP = 14;
    private static final int STROKE_DP = 2;

    private TileBackgrounds() {
    }

    static Drawable of(Context context, AppEntry entry) {
        Resources res = context.getResources();
        int base = res.getColor(R.color.tile, context.getTheme());
        int accent = res.getColor(R.color.accent, context.getTheme());
        int tint = entry.dominantColor;

        // Renk bulunamadıysa düz zemin kullanılır (minimal görünüm bozulmaz).
        GradientDrawable normal = gradient(context, base, tint, TINT_NORMAL, 0, 0);
        GradientDrawable pressed = gradient(context, base, tint, TINT_PRESSED, 0, 0);
        GradientDrawable focused = gradient(context, base, tint, TINT_NORMAL, accent, dp(context, STROKE_DP));

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    private static GradientDrawable gradient(Context context, int base, int tint, float amount,
                                             int strokeColor, int strokeWidth) {
        int start = tint == 0 ? base : blend(base, tint, amount);
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.BL_TR, new int[]{start, base});
        drawable.setCornerRadius(dp(context, CORNER_DP));
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    /** from -> to yönünde amount (0..1) oranında karıştırır. */
    private static int blend(int from, int to, float amount) {
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * amount);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * amount);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount);
        return Color.rgb(clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }
}
