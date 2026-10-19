package com.sade.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.net.Inet4Address;
import java.net.InetAddress;

/**
 * Sade TV Launcher - tek ekran, animasyonsuz, D-pad odaklı.
 */
public class MainActivity extends Activity implements AppGridAdapter.Listener {

    private static final String PREFS = "sade_launcher";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_ORDER = "order_keys";
    private static final String KEY_HINT_SHOWN = "hint_shown";
    private static final long RELOAD_INTERVAL_MS = 30_000L;

    /** Izgarada satır başına kart sayısı. */
    /**
     * Izgara düzeni uygulama sayısına göre dinamik seçilir:
     *   ≤12   → 4 sütun × 3 satır
     *   13–20 → 5 sütun × 4 satır
     *   21–30 → 6 sütun × 5 satır
     *   >30   → 5 sütun × 4 satır (kaydırmalı)
     */
    private int gridColumns = 4;
    private int gridRows = 3;
    private GridLayoutManager gridLayout;
    private int appliedCardWidth = -1;
    private int appliedCardHeight = -1;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView clockView;
    private TextView dayView;
    private TextView weekdayView;
    private View headerSettings;
    private ImageView netIcon;
    private TextView netIp;
    private SysMonitorView sysMonitor;
    private View favoritesSection;
    private RecyclerView favoritesRow;
    private RecyclerView appGrid;
    private TextView moveHint;

    private ConnectivityManager connectivityManager;
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            ui.post(MainActivity.this::updateNetworkStatus);
        }

        @Override
        public void onLost(Network network) {
            ui.post(MainActivity.this::updateNetworkStatus);
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
            ui.post(MainActivity.this::updateNetworkStatus);
        }
    };

    private AppGridAdapter gridAdapter;
    private AppGridAdapter favoritesAdapter;

    /** Uygulama kurulunca/kaldırılınca ızgarayı tazeler. */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            lastLoadAt = 0L;
            loadApps();
        }
    };

    private final List<AppEntry> apps = new ArrayList<>();
    private final Set<String> favoriteKeys = new HashSet<>();
    private final List<String> orderKeys = new ArrayList<>();

    /** Kart taşıma (sıralama) modu durumu. */
    private boolean moveMode = false;
    private int movePos = -1;
    private List<AppEntry> moveSnapshot;

    private long lastLoadAt = 0L;
    private boolean gridFocused = false;
    private int focusAttempts = 0;
    private Toast currentToast;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            ui.postDelayed(this, 20_000L);
        }
    };

    /** Ana ekran görünürken 2 sn'de bir RAM/CPU/Disk halkalarını tazeler. */
    private final Runnable sysTick = new Runnable() {
        @Override
        public void run() {
            updateSystemStats();
            ui.postDelayed(this, 2_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        clockView = findViewById(R.id.clock);
        dayView = findViewById(R.id.date_day);
        weekdayView = findViewById(R.id.date_weekday);
        headerSettings = findViewById(R.id.header_settings);
        netIcon = findViewById(R.id.net_status);
        netIp = findViewById(R.id.net_ip);
        sysMonitor = findViewById(R.id.sys_monitor);
        favoritesSection = findViewById(R.id.favorites_section);
        favoritesRow = findViewById(R.id.favorites_row);
        appGrid = findViewById(R.id.app_grid);
        moveHint = findViewById(R.id.move_hint);

        headerSettings.setOnClickListener(v -> openSettings(Settings.ACTION_SETTINGS));
        // Uzun basma: varsayılan launcher rolünü iste (rol bizde değilse)
        headerSettings.setOnLongClickListener(v -> requestHomeRole());

        loadFavoriteKeys();
        loadOrderKeys();
        setupLists();
        setupNetworkStatus();
        registerPackageReceiver();
        updateClock();
        loadApps();
        showFirstRunHint();
    }

    /** İlk açılışta uzun basma ipucunu bir kez gösterir. */
    private void showFirstRunHint() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_HINT_SHOWN, false)) return;
        prefs.edit().putBoolean(KEY_HINT_SHOWN, true).apply();
        ui.postDelayed(() -> toast(getString(R.string.hint_long_press)), 1500L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(clockTick);
        ui.post(clockTick);
        // Sistem halkaları yalnızca ana ekran görünürken çalışır.
        ui.removeCallbacks(sysTick);
        SystemStats.resetCpu();
        updateSystemStats();
        ui.postDelayed(sysTick, 2_000L);
        updateNetworkStatus();
        if (System.currentTimeMillis() - lastLoadAt > RELOAD_INTERVAL_MS) {
            loadApps();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(clockTick);
        // Arka planda ölçüm yok: başka uygulama açılınca halkalar durur.
        ui.removeCallbacks(sysTick);
    }

    /** RAM / CPU frekansı / disk yüzdelerini halkalara yazar. */
    private void updateSystemStats() {
        if (sysMonitor == null) return;
        sysMonitor.setValues(
                SystemStats.ramPercent(this),
                SystemStats.cpuClockMhz(),
                SystemStats.cpuMinMhz(),
                SystemStats.cpuMaxMhz(),
                SystemStats.diskPercent());
    }

    /** Uygulama ekleme/kaldırma olaylarını dinler (menüden "Kaldır" sonrası liste tazelenir). */
    private void registerPackageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(packageReceiver, filter);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(packageReceiver);
        } catch (Throwable ignored) {
        }
        if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Throwable ignored) {
            }
        }
        worker.shutdownNow();
        ui.removeCallbacksAndMessages(null);
    }

    /** Ana ekranda geri tuşu hiçbir şey yapmaz. */
    @Override
    public void onBackPressed() {
        // bilinçli olarak boş
    }

    /** Taşıma modunda D-pad'i ızgara içinde yönlendirir; OK bırakır, GERİ iptal eder. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (moveMode) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int key = event.getKeyCode();
                int pos = movePos;
                int column = pos % gridColumns;
                int count = apps.size();
                switch (key) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (column > 0) moveCard(pos, pos - 1);
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (column < gridColumns - 1 && pos + 1 < count) moveCard(pos, pos + 1);
                        break;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (pos - gridColumns >= 0) moveCard(pos, pos - gridColumns);
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (pos + gridColumns < count) moveCard(pos, pos + gridColumns);
                        break;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_NUMPAD_ENTER:
                        finishMoveMode(true);
                        break;
                    case KeyEvent.KEYCODE_BACK:
                        finishMoveMode(false);
                        break;
                    default:
                        break;
                }
            }
            // Taşıma modunda tüm tuşları biz tüketiyoruz.
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Launcher'dan çıkıldıysa yarım kalan taşımayı iptal et.
        if (moveMode) finishMoveMode(false);
    }

    private void startMoveMode(AppEntry entry) {
        int position = gridAdapter.indexOf(entry);
        if (position < 0) return;

        moveSnapshot = new ArrayList<>(apps);
        moveMode = true;
        movePos = position;
        moveHint.setVisibility(View.VISIBLE);
        focusCard(position);
    }

    /** Taşıma modunu bitirir; save=true ise yeni sıra kalıcı olarak kaydedilir. */
    private void finishMoveMode(boolean save) {
        if (!moveMode) return;
        moveMode = false;
        moveHint.setVisibility(View.GONE);

        int returnTo = movePos;

        if (save) {
            orderKeys.clear();
            for (AppEntry entry : apps) orderKeys.add(entry.key);
            persistOrder();
            toast(getString(R.string.move_saved));
        } else if (moveSnapshot != null) {
            apps.clear();
            apps.addAll(moveSnapshot);
            gridAdapter.setItems(apps);
            toast(getString(R.string.move_cancelled));
        }

        moveSnapshot = null;
        movePos = -1;
        rebuildFavorites();
        focusCard(Math.max(0, Math.min(returnTo, apps.size() - 1)));
    }

    /** Kartı hedef konuma taşır; adaptör, liste ve odak birlikte güncellenir. */
    private void moveCard(int from, int to) {
        if (from == to) return;
        Collections.swap(apps, from, to);
        gridAdapter.moveItem(from, to);
        movePos = to;
        appGrid.scrollToPosition(to);
        focusCard(to);
    }

    private void focusCard(int position) {
        ui.post(() -> {
            RecyclerView.ViewHolder holder = appGrid.findViewHolderForAdapterPosition(position);
            if (holder != null) {
                holder.itemView.requestFocus();
            }
        });
    }

    // ------------------------------------------------------------------ kurulum

    private void setupLists() {
        gridAdapter = new AppGridAdapter(this, R.layout.item_app, false);
        favoritesAdapter = new AppGridAdapter(this, R.layout.item_favorite, true);

        GridLayoutManager gridLayout = new GridLayoutManager(this, gridColumns);
        this.gridLayout = gridLayout;
        appGrid.setLayoutManager(gridLayout);
        appGrid.setAdapter(gridAdapter);
        appGrid.setItemAnimator(null);
        appGrid.setHasFixedSize(true);
        appGrid.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // Favoriler satırı görünür/gizli olduğunda ızgara yüksekliği değişir;
        // kart boyutunu yeni alana göre yeniden hesapla.
        appGrid.addOnLayoutChangeListener((v, left, top, right, bottom,
                                           oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                // Yerleşim sırasında adaptöre dokunmamak için bir sonraki tura bırak.
                appGrid.post(this::applyCardSize);
            }
        });

        favoritesRow.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        favoritesRow.setAdapter(favoritesAdapter);
        favoritesRow.setItemAnimator(null);
        favoritesRow.setHasFixedSize(true);
        favoritesRow.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // D-pad odak sırası: Ayarlar -> favoriler -> ızgara
        headerSettings.setNextFocusDownId(R.id.favorites_row);
        favoritesRow.setNextFocusUpId(R.id.header_settings);
        favoritesRow.setNextFocusDownId(R.id.app_grid);
        appGrid.setNextFocusUpId(R.id.favorites_row);
    }

    /**
     * Bağlantı durumu simgesi: aktif ağ ethernet ise ethernet, değilse Wi-Fi simgesi.
     * İnternet doğrulanmışsa yeşil, aksi halde kırmızı.
     */
    private void setupNetworkStatus() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            netIcon.setVisibility(View.GONE);
            if (netIp != null) netIp.setVisibility(View.GONE);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } catch (Throwable ignored) {
            }
        }
        updateNetworkStatus();
    }

    private void updateNetworkStatus() {
        if (netIcon == null || connectivityManager == null) return;

        boolean ethernet = false;
        boolean online = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network active = connectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities =
                    active == null ? null : connectivityManager.getNetworkCapabilities(active);
            if (capabilities != null) {
                ethernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
        } else {
            // API 23: yalnızca basit durum
            android.net.NetworkInfo info = connectivityManager.getActiveNetworkInfo();
            online = info != null && info.isConnected();
        }

        netIcon.setImageResource(ethernet ? R.drawable.ic_ethernet : R.drawable.ic_wifi);
        netIcon.setContentDescription(getString(
                online ? R.string.net_status_online : R.string.net_status_offline));
        int color = getResources().getColor(online ? R.color.ok : R.color.error, getTheme());
        netIcon.setImageTintList(ColorStateList.valueOf(color));

        String ip = findIpv4Address();
        if (netIp != null) {
            netIp.setText(ip);
            netIp.setVisibility(ip.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    /** Aktif ağın IPv4 adresini döner (ethernet veya Wi-Fi); bulunamazsa boş metin. */
    private String findIpv4Address() {
        if (connectivityManager == null) return "";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network active = connectivityManager.getActiveNetwork();
                LinkProperties properties =
                        active == null ? null : connectivityManager.getLinkProperties(active);
                if (properties != null) {
                    for (LinkAddress linkAddress : properties.getLinkAddresses()) {
                        InetAddress address = linkAddress.getAddress();
                        if (address instanceof Inet4Address
                                && !address.isLoopbackAddress()
                                && !address.isLinkLocalAddress()) {
                            return address.getHostAddress();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** Varsayılan launcher rolünü ister; istek gösterildiyse true döner. */
    private boolean requestHomeRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        RoleManager roles = getSystemService(RoleManager.class);
        if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_HOME)
                || roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            return false;
        }
        try {
            startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_HOME));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ veri

    private void loadFavoriteKeys() {
        favoriteKeys.clear();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        favoriteKeys.addAll(prefs.getStringSet(KEY_FAVORITES, Collections.<String>emptySet()));
    }

    private void persistFavorites() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_FAVORITES, new HashSet<>(favoriteKeys))
                .apply();
    }

    private void loadOrderKeys() {
        orderKeys.clear();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ORDER, "");
        if (raw == null || raw.isEmpty()) return;
        for (String key : raw.split("\n")) {
            if (!key.isEmpty()) orderKeys.add(key);
        }
    }

    private void persistOrder() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_ORDER, TextUtils.join("\n", orderKeys))
                .apply();
    }

    private void loadApps() {
        lastLoadAt = System.currentTimeMillis();
        final Set<String> favoritesSnapshot = new HashSet<>(favoriteKeys);

        worker.execute(() -> {
            final List<AppEntry> loaded = AppRepository.load(getApplicationContext(), favoritesSnapshot);
            ui.post(() -> bindApps(loaded));
        });
    }

    private void bindApps(List<AppEntry> loaded) {
        if (isFinishing() || isDestroyed()) return;

        List<AppEntry> ordered = applyStoredOrder(loaded);

        apps.clear();
        apps.addAll(ordered);

        // Favori durumunu tek doğruluk kaynağından (favoriteKeys) tazele:
        // böylece yeniden yüklemede yıldız/menü durumu asla bayatlamaz.
        for (AppEntry entry : apps) {
            entry.favorite = favoriteKeys.contains(entry.key);
        }

        gridAdapter.setItems(apps);
        rebuildFavorites();
        updateGridLayout();

        if (!gridFocused) {
            ensureGridFocus();
        }
    }

    // ------------------------------------------------------- dinamik ızgara

    /** Uygulama sayısına göre sütun/satır hedefini seçer ve kartları boyutlandırır. */
    private void updateGridLayout() {
        int count = apps.size();
        int columns;
        int rows;
        if (count <= 12) {
            columns = 4;
            rows = 3;
        } else if (count <= 20) {
            columns = 5;
            rows = 4;
        } else if (count <= 30) {
            columns = 6;
            rows = 5;
        } else {
            columns = 5;
            rows = 4;
        }

        gridRows = rows;
        if (columns != gridColumns) {
            gridColumns = columns;
            if (gridLayout != null) gridLayout.setSpanCount(columns);
        }
        applyCardSize();
        // İlk yüklemede ızgara henüz ölçülmemiş olabilir.
        appGrid.post(this::applyCardSize);
    }

    /**
     * Ölçülen ızgara alanına göre kart boyutunu hesaplar. Kartlar hedeflenen
     * satır sayısı ekrana tam sığacak şekilde küçültülür; minimum boyutun altına
     * inilmesi gerekirse satır sayısı azaltılır ve ızgara kaydırılır.
     */
    private void applyCardSize() {
        if (gridAdapter == null || gridColumns <= 0) return;

        int width = appGrid.getWidth() - appGrid.getPaddingLeft() - appGrid.getPaddingRight();
        int height = appGrid.getHeight() - appGrid.getPaddingTop() - appGrid.getPaddingBottom();
        if (width <= 0 || height <= 0) return;

        int gap = dp(12);        // item_app.xml: 6dp + 6dp dış boşluk
        int minHeight = dp(68);
        int minWidth = dp(96);

        int rows = gridRows;
        while (rows > 2 && (height - (rows - 1) * gap) / rows - gap < minHeight) {
            rows--;
        }

        int cardWidth = width / gridColumns - gap;
        int cardHeight = (height - (rows - 1) * gap) / rows - gap;

        cardWidth = Math.max(minWidth, Math.min(dp(200), cardWidth));
        cardHeight = Math.max(minHeight, Math.min(dp(124), cardHeight));

        // Favori şeridi yüksekliği ızgara alanını etkilediği için hesaplama
        // birkaç turda oturabilir; 2dp'den küçük farklarda tekrar düzenlemeyi kes.
        if (appliedCardHeight > 0
                && Math.abs(cardWidth - appliedCardWidth) <= dp(2)
                && Math.abs(cardHeight - appliedCardHeight) <= dp(2)) {
            return;
        }
        appliedCardWidth = cardWidth;
        appliedCardHeight = cardHeight;

        gridAdapter.setItemSize(cardWidth, cardHeight);

        // Favori kartları, ızgara kartlarıyla birebir aynı boyutta olmalı.
        if (favoritesAdapter != null) {
            favoritesAdapter.setItemSize(cardWidth, cardHeight);
            ViewGroup.LayoutParams params = favoritesRow.getLayoutParams();
            if (params != null) {
                int rowHeight = cardHeight + gap; // üst/alt 6dp dış boşluk
                if (params.height != rowHeight) {
                    params.height = rowHeight;
                    favoritesRow.setLayoutParams(params);
                }
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * Kullanıcının kaydettiği özel sırayı uygular. Listedeki sırada olmayan
     * (yeni kurulmuş) uygulamalar mevcut alfabetik sırayla sona eklenir.
     */
    private List<AppEntry> applyStoredOrder(List<AppEntry> loaded) {
        if (orderKeys.isEmpty()) return loaded;

        Map<String, AppEntry> byKey = new HashMap<>();
        for (AppEntry entry : loaded) {
            byKey.put(entry.key, entry);
        }

        List<AppEntry> ordered = new ArrayList<>(loaded.size());
        Set<String> added = new HashSet<>();
        for (String key : orderKeys) {
            AppEntry entry = byKey.get(key);
            if (entry != null && added.add(key)) {
                ordered.add(entry);
            }
        }
        for (AppEntry entry : loaded) {
            if (added.add(entry.key)) {
                ordered.add(entry);
            }
        }
        return ordered;
    }

    /**
     * Izgaranın ilk kutusuna odağı verir. Liste arka planda yüklendiği için
     * birkaç kez denenir; çocuk görünümler oluşmadan odak istenemez.
     */
    private void ensureGridFocus() {
        if (gridFocused || isFinishing() || isDestroyed()) return;

        View focused = getCurrentFocus();
        if (focused != null && isInside(appGrid, focused)) {
            gridFocused = true;
            return;
        }

        if (appGrid.getChildCount() > 0 && appGrid.getChildAt(0).requestFocus()) {
            gridFocused = true;
            return;
        }

        if (++focusAttempts < 15) {
            ui.postDelayed(this::ensureGridFocus, 120L);
        }
    }

    private static boolean isInside(View parent, View child) {
        View current = child;
        while (current != null) {
            if (current == parent) return true;
            ViewParent viewParent = current.getParent();
            current = (viewParent instanceof View) ? (View) viewParent : null;
        }
        return false;
    }

    private void rebuildFavorites() {
        List<AppEntry> favorites = new ArrayList<>();
        for (AppEntry entry : apps) {
            if (favoriteKeys.contains(entry.key)) favorites.add(entry);
        }

        favoritesAdapter.setItems(favorites);
        favoritesSection.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ etkileşim

    @Override
    public void onAppClick(AppEntry entry) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(entry.component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (Throwable t) {
            toast(getString(R.string.err_launch, entry.label));
        }
    }

    @Override
    public boolean onAppLongClick(AppEntry entry, boolean fromFavorites) {
        showAppMenu(entry, fromFavorites);
        return true;
    }

    /** Uzun basma menüsü: favori, taşı, aç, uygulama bilgisi, kaldır. */
    private void showAppMenu(final AppEntry entry, boolean fromFavorites) {
        final List<CharSequence> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        labels.add(getString(entry.favorite ? R.string.menu_unfavorite : R.string.menu_favorite));
        actions.add(() -> toggleFavorite(entry));

        if (!fromFavorites) {
            labels.add(getString(R.string.menu_move));
            actions.add(() -> startMoveMode(entry));
        }

        labels.add(getString(R.string.menu_open));
        actions.add(() -> onAppClick(entry));

        labels.add(getString(R.string.menu_app_info));
        actions.add(() -> openAppInfo(entry));

        if (!entry.system) {
            labels.add(getString(R.string.menu_uninstall));
            actions.add(() -> uninstall(entry));
        }

        if (!orderKeys.isEmpty()) {
            labels.add(getString(R.string.menu_reset_order));
            actions.add(this::resetOrder);
        }

        try {
            new AlertDialog.Builder(this, R.style.SadeDialog)
                    .setTitle(entry.label)
                    .setItems(labels.toArray(new CharSequence[0]),
                            (dialog, which) -> actions.get(which).run())
                    .setNegativeButton(R.string.menu_cancel, null)
                    .show();
        } catch (Throwable t) {
            toast(getString(R.string.err_open_settings));
        }
    }

    private void toggleFavorite(AppEntry entry) {
        entry.favorite = !entry.favorite;
        if (entry.favorite) {
            favoriteKeys.add(entry.key);
        } else {
            favoriteKeys.remove(entry.key);
        }

        persistFavorites();
        rebuildFavorites();
        gridAdapter.refreshItem(entry);

        toast(getString(entry.favorite ? R.string.fav_added : R.string.fav_removed, entry.label));
    }

    private void openAppInfo(AppEntry entry) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + entry.component.getPackageName()));
            startActivity(intent);
        } catch (Throwable t) {
            toast(getString(R.string.err_open_settings));
        }
    }

    private void uninstall(AppEntry entry) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + entry.component.getPackageName()));
            startActivity(intent);
        } catch (Throwable t) {
            toast(getString(R.string.err_open_settings));
        }
    }

    /** Özel sıralamayı siler, listeyi alfabetik sıraya döndürür. */
    private void resetOrder() {
        orderKeys.clear();
        persistOrder();
        loadApps();
        toast(getString(R.string.order_reset));
    }

    // ------------------------------------------------------------------ yardımcılar

    private void updateClock() {
        Date now = new Date();
        Locale locale = Locale.getDefault();
        clockView.setText(new SimpleDateFormat("HH:mm", locale).format(now));
        dayView.setText(capitalize(new SimpleDateFormat("d MMMM", locale).format(now)));
        weekdayView.setText(capitalize(new SimpleDateFormat("EEEE", locale).format(now)));
    }

    /** Ayarlar ekranlarından birini açar; hata olursa kullanıcıyı bilgilendirir. */
    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Throwable t) {
            toast(getString(R.string.err_open_settings));
        }
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void toast(String message) {
        if (currentToast != null) currentToast.cancel();
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        currentToast.show();
    }
}
