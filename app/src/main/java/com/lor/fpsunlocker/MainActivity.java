package com.lor.fpsunlocker;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single-screen, zero-dependency helper app designed to be imported into Meta Wolf
 * alongside Legends of Runeterra for 1-tap 60 / 60+ FPS unlocking without root.
 */
public class MainActivity extends Activity {

    private static final int COLOR_BG = Color.parseColor("#0B1120");
    private static final int COLOR_CARD = Color.parseColor("#162033");
    private static final int COLOR_CARD_BORDER = Color.parseColor("#26344D");
    private static final int COLOR_TEXT_PRIMARY = Color.parseColor("#F8FAFC");
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#94A3B8");
    private static final int COLOR_GOLD = Color.parseColor("#D4AF37");
    private static final int COLOR_GOLD_TEXT = Color.parseColor("#0B1120");
    private static final int COLOR_EMERALD = Color.parseColor("#10B981");
    private static final int COLOR_AMBER_BG = Color.parseColor("#2B2111");
    private static final int COLOR_AMBER_BORDER = Color.parseColor("#B45309");
    private static final int COLOR_AMBER_TEXT = Color.parseColor("#FDE68A");
    private static final int COLOR_RED = Color.parseColor("#EF4444");
    private static final int COLOR_BTN_MUTED = Color.parseColor("#24324A");
    private static final int COLOR_LOG_BG = Color.parseColor("#090E1A");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StringBuilder logHistory = new StringBuilder();
    private final List<File> discoveredFiles = new ArrayList<>();

    private TextView tvEnvBadge;
    private TextView tvStatusTitle;
    private TextView tvStatusDetail;
    private TextView tvLog;
    private Button btn60Plus;
    private Button btn60;
    private Button btnReset;
    private Button btnRefresh;
    private Button btnCopyLog;

    private boolean isBusy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        appendLog("Khởi động ứng dụng (Android " + Build.VERSION.RELEASE + " / SDK " + Build.VERSION.SDK_INT + ")");
        appendLog("dataDir: " + getApplicationInfo().dataDir);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, dp(22), pad, dp(28));
        scrollView.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Header
        TextView tvTitle = new TextView(this);
        tvTitle.setText("LoR 60+ FPS Unlocker");
        tvTitle.setTextColor(COLOR_GOLD);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(tvTitle);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText("Mở khóa 60 / 90 / 120 FPS cho Legends of Runeterra (Không cần Root • Dùng trong Meta Wolf)");
        tvSubtitle.setTextColor(COLOR_TEXT_SECONDARY);
        tvSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvSubtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(tvSubtitle);

        // Status Card
        LinearLayout statusCard = createCard(COLOR_CARD, COLOR_CARD_BORDER);
        tvEnvBadge = new TextView(this);
        tvEnvBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvEnvBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvEnvBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.bottomMargin = dp(10);
        statusCard.addView(tvEnvBadge, badgeParams);

        tvStatusTitle = new TextView(this);
        tvStatusTitle.setText("Đang kiểm tra file cấu hình...");
        tvStatusTitle.setTextColor(COLOR_TEXT_PRIMARY);
        tvStatusTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvStatusTitle.setTypeface(Typeface.DEFAULT_BOLD);
        statusCard.addView(tvStatusTitle);

        tvStatusDetail = new TextView(this);
        tvStatusDetail.setTextColor(COLOR_TEXT_SECONDARY);
        tvStatusDetail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tvStatusDetail.setPadding(0, dp(6), 0, 0);
        statusCard.addView(tvStatusDetail);

        root.addView(statusCard, cardMarginParams(dp(12)));

        // Important Warning Card
        LinearLayout warnCard = createCard(COLOR_AMBER_BG, COLOR_AMBER_BORDER);
        TextView tvWarn = new TextView(this);
        tvWarn.setText("⚠ Lưu ý quan trọng: Trước khi bấm mở khóa, hãy tắt hẳn (Kill) LoR trong Meta Wolf. Nếu game còn chạy ngầm, game sẽ tự ghi đè lại về 30 FPS.");
        tvWarn.setTextColor(COLOR_AMBER_TEXT);
        tvWarn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        warnCard.addView(tvWarn);
        root.addView(warnCard, cardMarginParams(dp(14)));

        // Action Buttons
        btn60Plus = createActionButton("Mở khóa 60+ FPS (90Hz / 120Hz)", COLOR_GOLD, COLOR_GOLD_TEXT);
        btn60Plus.setOnClickListener(v -> applyMode(PlayerPrefsPatcher.FpsMode.FPS_60_PLUS));
        root.addView(btn60Plus, buttonMarginParams(dp(10)));

        btn60 = createActionButton("Mở khóa 60 FPS (Ổn định, mát máy)", COLOR_EMERALD, COLOR_GOLD_TEXT);
        btn60.setOnClickListener(v -> applyMode(PlayerPrefsPatcher.FpsMode.FPS_60));
        root.addView(btn60, buttonMarginParams(dp(10)));

        btnReset = createActionButton("Khôi phục mặc định (30 FPS / Dynamic)", COLOR_BTN_MUTED, COLOR_TEXT_PRIMARY);
        btnReset.setOnClickListener(v -> applyMode(PlayerPrefsPatcher.FpsMode.RESET_DEFAULT));
        root.addView(btnReset, buttonMarginParams(dp(10)));

        // Utility row: Refresh & Copy Log
        LinearLayout utilRow = new LinearLayout(this);
        utilRow.setOrientation(LinearLayout.HORIZONTAL);

        btnRefresh = createSmallButton("Quét lại", COLOR_BTN_MUTED);
        btnRefresh.setOnClickListener(v -> refreshScan());
        LinearLayout.LayoutParams leftBtn = new LinearLayout.LayoutParams(0, dp(42), 1f);
        leftBtn.rightMargin = dp(6);
        utilRow.addView(btnRefresh, leftBtn);

        btnCopyLog = createSmallButton("Copy Log hỗ trợ", COLOR_BTN_MUTED);
        btnCopyLog.setOnClickListener(v -> copyLogToClipboard());
        LinearLayout.LayoutParams rightBtn = new LinearLayout.LayoutParams(0, dp(42), 1f);
        rightBtn.leftMargin = dp(6);
        utilRow.addView(btnCopyLog, rightBtn);

        root.addView(utilRow, cardMarginParams(dp(16)));

        // Quick Guide Card
        LinearLayout guideCard = createCard(COLOR_CARD, COLOR_CARD_BORDER);
        TextView tvGuideTitle = new TextView(this);
        tvGuideTitle.setText("Hướng dẫn nhanh (chỉ làm 1 lần)");
        tvGuideTitle.setTextColor(COLOR_TEXT_PRIMARY);
        tvGuideTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvGuideTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvGuideTitle.setPadding(0, 0, 0, dp(6));
        guideCard.addView(tvGuideTitle);

        TextView tvGuideBody = new TextView(this);
        tvGuideBody.setText(
                "1. Mở Meta Wolf -> Settings -> Import Local App -> chọn cả LoR và app này.\n"
                        + "2. Mở LoR bên trong Meta Wolf, đăng nhập và tải xong dữ liệu game.\n"
                        + "3. Thoát game -> trong Meta Wolf nhấn giữ icon LoR -> tắt tiến trình (Kill).\n"
                        + "4. Mở app này từ bên trong Meta Wolf -> bấm chọn 60 FPS hoặc 60+ FPS.\n"
                        + "5. Tạo lối tắt (Shortcut) cho LoR từ Meta Wolf ra màn hình chính để chơi."
        );
        tvGuideBody.setTextColor(COLOR_TEXT_SECONDARY);
        tvGuideBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tvGuideBody.setLineSpacing(dp(3), 1f);
        guideCard.addView(tvGuideBody);
        root.addView(guideCard, cardMarginParams(dp(14)));

        // Diagnostic Log Card
        TextView tvLogHeader = new TextView(this);
        tvLogHeader.setText("Nhật ký kiểm tra (không truy cập Internet • không đọc mật khẩu)");
        tvLogHeader.setTextColor(COLOR_TEXT_SECONDARY);
        tvLogHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tvLogHeader.setPadding(dp(2), 0, 0, dp(6));
        root.addView(tvLogHeader);

        tvLog = new TextView(this);
        tvLog.setBackground(createRoundedRect(COLOR_LOG_BG, COLOR_CARD_BORDER, dp(10)));
        tvLog.setTextColor(Color.parseColor("#CBD5E1"));
        tvLog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setPadding(dp(12), dp(10), dp(12), dp(10));
        tvLog.setTextIsSelectable(true);
        root.addView(tvLog, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scrollView);
    }

    private void refreshScan() {
        if (isBusy) {
            return;
        }
        setBusy(true);
        final String dataDir = getApplicationInfo().dataDir;

        executor.execute(() -> {
            LorConfigLocator.ScanResult scan = LorConfigLocator.scan(dataDir);
            List<String> fileSummaries = new ArrayList<>();
            String firstStateSummary = null;

            for (File f : scan.configFiles) {
                try {
                    String xml = PlayerPrefsPatcher.readFileUtf8(f);
                    PlayerPrefsPatcher.InspectResult info = PlayerPrefsPatcher.inspect(xml);
                    String state = info.describeCurrentState();
                    if (firstStateSummary == null) {
                        firstStateSummary = state;
                    }
                    fileSummaries.add("• " + shortenPath(f.getAbsolutePath())
                            + "\n  Trạng thái: " + state
                            + " | Tài khoản: " + info.accountPrefixes.size());
                } catch (IOException e) {
                    fileSummaries.add("• " + shortenPath(f.getAbsolutePath()) + " (Lỗi đọc: " + e.getMessage() + ")");
                }
            }

            final String finalState = firstStateSummary;
            runOnUiThread(() -> {
                discoveredFiles.clear();
                discoveredFiles.addAll(scan.configFiles);

                if (scan.insideCloner) {
                    tvEnvBadge.setText("✓ ĐANG CHẠY TRONG META WOLF / CLONER");
                    tvEnvBadge.setTextColor(COLOR_GOLD_TEXT);
                    tvEnvBadge.setBackground(createRoundedRect(COLOR_EMERALD, COLOR_EMERALD, dp(6)));
                } else {
                    tvEnvBadge.setText("✕ ĐANG CHẠY NGOÀI META WOLF");
                    tvEnvBadge.setTextColor(Color.WHITE);
                    tvEnvBadge.setBackground(createRoundedRect(COLOR_RED, COLOR_RED, dp(6)));
                }

                if (scan.configFiles.isEmpty()) {
                    tvStatusTitle.setText(scan.insideCloner
                            ? "Chưa tìm thấy file cấu hình LoR"
                            : "Hãy import và mở app này bên trong Meta Wolf");
                    StringBuilder detail = new StringBuilder();
                    for (String note : scan.diagnosticNotes) {
                        detail.append(note).append("\n");
                    }
                    tvStatusDetail.setText(detail.toString().trim());
                    appendLog("Quét xong: không tìm thấy file playerprefs (đã kiểm tra " + scan.searchedRoots.size() + " thư mục gốc).");
                } else {
                    tvStatusTitle.setText("Đã sẵn sàng (" + scan.configFiles.size() + " file) • " + finalState);
                    StringBuilder detail = new StringBuilder();
                    for (String s : fileSummaries) {
                        detail.append(s).append("\n");
                    }
                    tvStatusDetail.setText(detail.toString().trim());
                    appendLog("Tìm thấy " + scan.configFiles.size() + " file cấu hình -> " + finalState);
                }

                setBusy(false);
            });
        });
    }

    private void applyMode(PlayerPrefsPatcher.FpsMode mode) {
        if (isBusy) {
            return;
        }
        if (discoveredFiles.isEmpty()) {
            Toast.makeText(this, "Chưa tìm thấy file cấu hình LoR. Hãy kiểm tra hướng dẫn bên dưới!", Toast.LENGTH_LONG).show();
            refreshScan();
            return;
        }

        setBusy(true);
        final List<File> targets = new ArrayList<>(discoveredFiles);
        appendLog("Đang áp dụng chế độ: " + mode.displayName + "...");

        executor.execute(() -> {
            int successCount = 0;
            StringBuilder messages = new StringBuilder();

            for (File file : targets) {
                PlayerPrefsPatcher.PatchResult res = PlayerPrefsPatcher.applyToFile(file, mode);
                if (res.success) {
                    successCount++;
                }
                messages.append(res.success ? "[OK] " : "[LỖI] ")
                        .append(res.message)
                        .append("\n");
            }

            final int finalSuccess = successCount;
            final String msgText = messages.toString().trim();

            runOnUiThread(() -> {
                appendLog(msgText);
                if (finalSuccess > 0) {
                    Toast.makeText(this, "Thành công! Hãy mở LoR qua Meta Wolf để trải nghiệm.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Chưa áp dụng được — xem chi tiết trong mục Nhật ký.", Toast.LENGTH_LONG).show();
                }
                setBusy(false);
                refreshScan();
            });
        });
    }

    private void copyLogToClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("LoR FPS Unlocker Log", logHistory.toString()));
            Toast.makeText(this, "Đã copy nhật ký vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show();
        }
    }

    private void setBusy(boolean busy) {
        this.isBusy = busy;
        boolean canPatch = !busy && !discoveredFiles.isEmpty();
        btn60Plus.setEnabled(canPatch);
        btn60.setEnabled(canPatch);
        btnReset.setEnabled(canPatch);
        btnRefresh.setEnabled(!busy);

        btn60Plus.setAlpha(canPatch ? 1f : 0.45f);
        btn60.setAlpha(canPatch ? 1f : 0.45f);
        btnReset.setAlpha(canPatch ? 1f : 0.45f);
    }

    private void appendLog(String line) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        if (logHistory.length() > 0) {
            logHistory.append("\n");
        }
        logHistory.append("[").append(time).append("] ").append(line);
        if (tvLog != null) {
            tvLog.setText(logHistory.toString());
        }
    }

    private static String shortenPath(String fullPath) {
        if (fullPath == null) {
            return "";
        }
        String marker = LorConfigLocator.META_WOLF_PACKAGE + "/";
        int idx = fullPath.indexOf(marker);
        if (idx >= 0) {
            return ".../" + fullPath.substring(idx + marker.length());
        }
        return fullPath;
    }

    private LinearLayout createCard(int bgColor, int borderColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createRoundedRect(bgColor, borderColor, dp(12)));
        int p = dp(14);
        card.setPadding(p, p, p, p);
        return card;
    }

    private Button createActionButton(String text, int bgColor, int textColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackground(createRoundedRect(bgColor, bgColor, dp(10)));
        btn.setGravity(Gravity.CENTER);
        btn.setMinimumHeight(dp(50));
        btn.setPadding(dp(16), dp(12), dp(16), dp(12));
        return btn;
    }

    private Button createSmallButton(String text, int bgColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(COLOR_TEXT_PRIMARY);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setBackground(createRoundedRect(bgColor, COLOR_CARD_BORDER, dp(8)));
        btn.setGravity(Gravity.CENTER);
        return btn;
    }

    private GradientDrawable createRoundedRect(int fillColor, int strokeColor, int radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        gd.setStroke(dp(1), strokeColor);
        return gd;
    }

    private LinearLayout.LayoutParams cardMarginParams(int bottomMarginPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = bottomMarginPx;
        return lp;
    }

    private LinearLayout.LayoutParams buttonMarginParams(int bottomMarginPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = bottomMarginPx;
        return lp;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
