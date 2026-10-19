package com.sade.tvlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.Locale;

/**
 * Üst barda RAM / CPU / Disk halkaları.
 * Sadece çizim yapar; veri beslemesi MainActivity'den gelir (ana ekran görünürken).
 */
public class SysMonitorView extends View {

    private static final int COLOR_ACCENT = 0xFFF0B24A;
    private static final int COLOR_TRACK = 0xFF2A303A;
    private static final int COLOR_TEXT = 0xFFF2F4F7;
    private static final int COLOR_MUTED = 0xFF8A93A0;
    private static final int COLOR_WARN = 0xFFE5544B;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pctText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private final int[] values = new int[]{0, 0, 0};
    private final String[] texts = new String[]{"0%", "0%", "0%"};
    private String[] labels = new String[]{"RAM", "CPU", "DISK"};

    private float diameter;
    private float strokeW;
    private float gap;
    private float labelH;

    public SysMonitorView(Context c) {
        super(c);
        init(c);
    }

    public SysMonitorView(Context c, AttributeSet a) {
        super(c, a);
        init(c);
    }

    public SysMonitorView(Context c, AttributeSet a, int defStyle) {
        super(c, a, defStyle);
        init(c);
    }

    private void init(Context c) {
        diameter = dp(42);
        strokeW = dp(3);
        gap = dp(12);
        labelH = dp(13);

        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(strokeW);
        track.setColor(COLOR_TRACK);

        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(strokeW);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(COLOR_ACCENT);

        pctText.setColor(COLOR_TEXT);
        pctText.setTextAlign(Paint.Align.CENTER);
        pctText.setTextSize(sp(10.5f));

        labelText.setColor(COLOR_MUTED);
        labelText.setTextAlign(Paint.Align.CENTER);
        labelText.setTextSize(sp(8.5f));

        labels = new String[]{
                c.getString(R.string.sys_ram),
                c.getString(R.string.sys_cpu),
                c.getString(R.string.sys_disk)
        };

        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * @param ramPct   kullanılan RAM yüzdesi
     * @param cpuMhz   ortalama CPU frekansı (MHz)
     * @param cpuMinMhz CPU'nun en düşük frekansı
     * @param cpuMaxMhz CPU'nun en yüksek frekansı
     * @param diskPct  /data doluluk yüzdesi
     */
    public void setValues(int ramPct, int cpuMhz, int cpuMinMhz, int cpuMaxMhz, int diskPct) {
        int ram = clamp(ramPct);
        int disk = clamp(diskPct);

        int span = Math.max(1, cpuMaxMhz - cpuMinMhz);
        int cpu = clamp(Math.round(100f * (cpuMhz - cpuMinMhz) / span));
        String cpuTxt = cpuMhz > 0
                ? String.format(Locale.getDefault(), "%.1fG", cpuMhz / 1000f)
                : "--";

        String ramTxt = ram + "%";
        String diskTxt = disk + "%";

        boolean changed = values[0] != ram || values[1] != cpu || values[2] != disk
                || !ramTxt.equals(texts[0]) || !cpuTxt.equals(texts[1]) || !diskTxt.equals(texts[2]);

        values[0] = ram;
        values[1] = cpu;
        values[2] = disk;
        texts[0] = ramTxt;
        texts[1] = cpuTxt;
        texts[2] = diskTxt;

        if (changed) invalidate();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = (int) Math.ceil(3 * diameter + 2 * gap) + getPaddingLeft() + getPaddingRight();
        int h = (int) Math.ceil(diameter + labelH) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(w, widthSpec), resolveSize(h, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cy = getPaddingTop() + diameter / 2f;
        float r = diameter / 2f;

        for (int i = 0; i < 3; i++) {
            float cx = getPaddingLeft() + r + i * (diameter + gap);
            oval.set(cx - r, cy - r, cx + r, cy + r);

            canvas.drawArc(oval, 0f, 360f, false, track);

            int v = values[i];
            if (v > 0) {
                arc.setColor(v >= 90 ? COLOR_WARN : COLOR_ACCENT);
                canvas.drawArc(oval, -90f, 360f * v / 100f, false, arc);
            }

            Paint.FontMetrics fm = pctText.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(texts[i], cx, baseline, pctText);

            float labelY = getPaddingTop() + diameter + labelH * 0.72f;
            canvas.drawText(labels[i], cx, labelY, labelText);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }
}
