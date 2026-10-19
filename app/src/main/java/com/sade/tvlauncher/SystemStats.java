package com.sade.tvlauncher;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Ucuz sistem ölçümleri (her biri birkaç ms):
 *   RAM  : ActivityManager.MemoryInfo
 *   Disk : StatFs(/data)
 *   CPU  : cpufreq "time_in_state" sayaç farkından ortalama çalışma frekansı
 *
 * Not: Bu cihazda /proc/stat ve /proc/loadavg uygulamalara kapalı (EACCES).
 * Bu yüzden CPU için yanıltıcı bir "doluluk yüzdesi" yerine gerçek ortalama
 * çalışma frekansı ölçülür; halka bu frekansın en düşük–en yüksek aralıktaki
 * yerini gösterir.
 */
public final class SystemStats {

    private static final String CPU_BASE = "/sys/devices/system/cpu/";

    private static int coreCount = 0;
    private static int maxMhz = 0;
    private static int minMhz = 0;

    /** Çekirdek başına son okunan frekans sayaçları. */
    private static long[][] prevTick;
    private static int lastMhz = 0;

    private SystemStats() {
    }

    /** CPU sayaç tabanını sıfırlar (ana ekrana dönüşte eski örnek karışmasın). */
    public static void resetCpu() {
        prevTick = null;
    }

    public static int coreCount() {
        if (coreCount > 0) return coreCount;
        int n = 0;
        String s = readLine(CPU_BASE + "present");
        if (s != null) {
            for (String part : s.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                try {
                    if (p.contains("-")) {
                        String[] ab = p.split("-");
                        n += Integer.parseInt(ab[1].trim()) - Integer.parseInt(ab[0].trim()) + 1;
                    } else {
                        Integer.parseInt(p);
                        n++;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (n <= 0) n = Math.max(1, Runtime.getRuntime().availableProcessors());
        coreCount = n;
        return coreCount;
    }

    public static int cpuMaxMhz() {
        if (maxMhz <= 0) {
            maxMhz = parseInt(readLine(CPU_BASE + "cpu0/cpufreq/cpuinfo_max_freq")) / 1000;
            if (maxMhz <= 0) maxMhz = 1700;
        }
        return maxMhz;
    }

    public static int cpuMinMhz() {
        if (minMhz <= 0) {
            minMhz = parseInt(readLine(CPU_BASE + "cpu0/cpufreq/cpuinfo_min_freq")) / 1000;
            if (minMhz <= 0) minMhz = Math.max(1, cpuMaxMhz() / 2);
        }
        return minMhz;
    }

    /** Tüm çekirdeklerin ortalama çalışma frekansı (MHz). */
    public static int cpuClockMhz() {
        int cores = coreCount();
        if (prevTick == null || prevTick.length != cores) prevTick = new long[cores][];

        long weighted = 0L;
        long total = 0L;
        for (int c = 0; c < cores; c++) {
            long[][] pairs = readFreqs(c);
            if (pairs == null || pairs.length == 0) continue;

            long[] prev = prevTick[c];
            if (prev == null || prev.length != pairs.length) {
                long[] fresh = new long[pairs.length];
                for (int i = 0; i < pairs.length; i++) fresh[i] = pairs[i][1];
                prevTick[c] = fresh;
                continue;
            }
            for (int i = 0; i < pairs.length; i++) {
                long d = pairs[i][1] - prev[i];
                prev[i] = pairs[i][1];
                if (d <= 0L) continue;
                weighted += pairs[i][0] * d;
                total += d;
            }
        }
        // İlk ölçümde (veya sayaç yoksa) anlık frekansı kullan.
        long khz = total > 0L ? (weighted / total) : currentKhz(cores);
        lastMhz = (int) (khz / 1000L);
        return lastMhz;
    }

    private static int currentKhz(int cores) {
        long sum = 0L;
        int n = 0;
        for (int c = 0; c < cores; c++) {
            int v = parseInt(readLine(CPU_BASE + "cpu" + c + "/cpufreq/scaling_cur_freq"));
            if (v > 0) {
                sum += v;
                n++;
            }
        }
        return n > 0 ? (int) (sum / n) : lastMhz * 1000;
    }

    /** Bir çekirdeğin {frekans, süre} çiftleri; okunamazsa null. */
    private static long[][] readFreqs(int core) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(
                    CPU_BASE + "cpu" + core + "/cpufreq/stats/time_in_state"));
            List<long[]> out = new ArrayList<>(12);
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 2) continue;
                try {
                    out.add(new long[]{Long.parseLong(p[0]), Long.parseLong(p[1])});
                } catch (NumberFormatException ignored) {
                }
            }
            return out.toArray(new long[0][]);
        } catch (Exception e) {
            return null;
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Kullanılan RAM yüzdesi. */
    public static int ramPercent(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return 0;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            if (mi.totalMem <= 0L) return 0;
            long used = mi.totalMem - mi.availMem;
            int pct = Math.round(100f * used / mi.totalMem);
            return clamp(pct);
        } catch (Exception e) {
            return 0;
        }
    }

    /** /data bölümünün doluluk yüzdesi. */
    public static int diskPercent() {
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes();
            long free = fs.getAvailableBytes();
            if (total <= 0L) return 0;
            int pct = Math.round(100f * (total - free) / total);
            return clamp(pct);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static int parseInt(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String readLine(String path) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(path));
            return r.readLine();
        } catch (Exception e) {
            return null;
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
