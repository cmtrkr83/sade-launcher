package com.sade.tvlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cihazdaki başlatılabilir uygulamaları toplar.
 * Arka plan iş parçacığında çağrılmak üzere tasarlanmıştır (paket yöneticisi + ikon yükleme maliyetli).
 */
public final class AppRepository {

    /** İkonları bu piksel boyutuna kadar küçültüyoruz: 2GB RAM'li cihazda bellek dostu. */
    private static final int MAX_ICON_PX = 144;

    private AppRepository() {
    }

    public static List<AppEntry> load(Context context, Set<String> favoriteKeys) {
        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();

        // Paket başına tek giriş tutarız; TV (leanback) aktivitesi tercih edilir.
        Map<String, AppEntry> byPackage = new HashMap<>();

        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Intent classic = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);

        collect(context, pm, leanback, true, self, byPackage, favoriteKeys);
        collect(context, pm, classic, false, self, byPackage, favoriteKeys);

        List<AppEntry> apps = new ArrayList<>(byPackage.values());

        // Türkçe alfabeye göre sırala (İ/ı/I doğru sıralansın).
        final Collator collator = Collator.getInstance(new Locale("tr", "TR"));
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(apps, (a, b) -> collator.compare(a.label, b.label));

        return apps;
    }

    private static void collect(Context context, PackageManager pm, Intent intent, boolean leanback,
                                String self, Map<String, AppEntry> out, Set<String> favoriteKeys) {
        List<ResolveInfo> resolved;
        try {
            resolved = pm.queryIntentActivities(intent, 0);
        } catch (Throwable t) {
            return;
        }

        for (ResolveInfo info : resolved) {
            ActivityInfo activity = info.activityInfo;
            if (activity == null) continue;

            String pkg = activity.packageName;
            if (pkg == null || pkg.equals(self)) continue;
            if (!activity.exported) continue;

            // Paket başına tek giriş: leanback geçişi önce çalıştığı için
            // var olan (daha iyi) kaydın üzerine yazmayız.
            if (out.containsKey(pkg)) continue;

            CharSequence label = activity.loadLabel(pm);
            String name = label == null ? "" : label.toString().trim();
            if (name.isEmpty()) name = pkg;

            Drawable icon = loadIcon(pm, activity);
            boolean system = (activity.applicationInfo != null)
                    && ((activity.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0);

            // Favori anahtarı, AppEntry.key ile birebir aynı biçimde üretilmeli
            // (ComponentName.flattenToShortString -> "pkg/.Aktivite").
            ComponentName component = new ComponentName(pkg, activity.name);

            AppEntry entry = new AppEntry(
                    component,
                    name, icon, system, favoriteKeys.contains(component.flattenToShortString()),
                    dominantColor(icon));
            out.put(pkg, entry);
        }
    }

    /** Aynı paket için birden fazla aktivite varsa ilk bulunan (leanback) korunur. */
    private static Drawable loadIcon(PackageManager pm, ActivityInfo activity) {
        Drawable drawable = null;
        try {
            drawable = activity.loadIcon(pm);
        } catch (Throwable ignored) {
        }
        return downscale(drawable);
    }

    /**
     * İkonun kart degradesi için kullanılacak baskın rengini bulur.
     * Doygun pikseller 3 bitlik kutulara ayrılır, en kalabalık kutu ortalaması döner.
     * Beyaza/siyaha yakın pikseller elenir; renk bulunamazsa 0 döner (düz zemin kullanılır).
     */
    private static int dominantColor(Drawable icon) {
        if (icon == null) return 0;

        final int size = 32;
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            icon.setBounds(0, 0, size, size);
            icon.draw(canvas);

            int[] counts = new int[512];
            int[] sumR = new int[512];
            int[] sumG = new int[512];
            int[] sumB = new int[512];

            int allR = 0;
            int allG = 0;
            int allB = 0;
            int allN = 0;

            float[] hsv = new float[3];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int pixel = bitmap.getPixel(x, y);
                    if ((pixel >>> 24) < 200) continue;

                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;

                    int max = Math.max(r, Math.max(g, b));
                    int min = Math.min(r, Math.min(g, b));
                    // Neredeyse siyah ve neredeyse beyaz pikseller kart rengini bozar.
                    if (max < 35 || min > 220) continue;

                    Color.colorToHSV(pixel, hsv);

                    allR += r;
                    allG += g;
                    allB += b;
                    allN++;

                    if (hsv[1] < 0.30f) continue;
                    int bucket = ((r >> 5) << 6) | ((g >> 5) << 3) | (b >> 5);
                    counts[bucket]++;
                    sumR[bucket] += r;
                    sumG[bucket] += g;
                    sumB[bucket] += b;
                }
            }

            int best = -1;
            for (int i = 0; i < counts.length; i++) {
                if (best < 0 || counts[i] > counts[best]) best = i;
            }

            int color;
            if (best >= 0 && counts[best] >= 6) {
                int n = counts[best];
                color = Color.rgb(sumR[best] / n, sumG[best] / n, sumB[best] / n);
            } else if (allN > 0) {
                color = Color.rgb(allR / allN, allG / allN, allB / allN);
            } else {
                return 0;
            }

            return capBrightness(color);
        } catch (Throwable t) {
            return 0;
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    /** Kartların koyu kalması için rengin parlaklığını alt/üst sınır aralığına çeker. */
    private static int capBrightness(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[2] >= 0.30f && hsv[2] <= 0.72f) return color;
        hsv[2] = Math.max(0.30f, Math.min(0.72f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    /** Büyük ikonları (ör. 512x512) küçülterek bellek kullanımını azaltır. */
    private static Drawable downscale(Drawable source) {
        if (source == null) return null;

        int width = source.getIntrinsicWidth();
        int height = source.getIntrinsicHeight();
        if (width <= 0 || height <= 0 || (width <= MAX_ICON_PX && height <= MAX_ICON_PX)) {
            return source;
        }

        float ratio = Math.min((float) MAX_ICON_PX / width, (float) MAX_ICON_PX / height);
        int targetWidth = Math.max(1, Math.round(width * ratio));
        int targetHeight = Math.max(1, Math.round(height * ratio));

        try {
            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            source.setBounds(0, 0, targetWidth, targetHeight);
            source.draw(canvas);
            return new BitmapDrawable(null, bitmap);
        } catch (Throwable t) {
            return source;
        }
    }
}
