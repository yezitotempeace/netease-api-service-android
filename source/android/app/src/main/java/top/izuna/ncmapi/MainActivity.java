package top.izuna.ncmapi;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 界面只负责下发启动/停止指令并显示状态。
 * 真正的 Node 服务跑在 :node 独立进程里，崩溃不会带走界面。
 */
public class MainActivity extends Activity {
    private TextView status;
    private TextView logView;
    private Button btnStart;
    private Button btnStop;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean portOpen = false;
    private volatile boolean polling = false;
    private volatile long stopRequestedAt = 0L;

    private final Runnable uiTick = new Runnable() {
        @Override
        public void run() {
            render();
            if (polling) handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        installCrashLogger();
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);

        TextView title = new TextView(this);
        title.setText("网易云服务");
        title.setTextSize(22);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("启动后 Folia 连接 127.0.0.1:3000\n服务跑在独立进程，界面崩溃不影响服务");
        hint.setTextSize(12);
        hint.setPadding(0, 16, 0, 0);
        root.addView(hint);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 40, 0, 40);
        status.setText("检查中…");
        root.addView(status);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        btnStart = new Button(this);
        btnStart.setText("启动服务");
        btnStart.setOnClickListener(v -> sendAction(NodeService.ACTION_START));
        row.addView(btnStart);

        btnStop = new Button(this);
        btnStop.setText("停止服务");
        btnStop.setOnClickListener(v -> stopNow());
        row.addView(btnStop);

        root.addView(row);

        logView = new TextView(this);
        logView.setTextSize(10);
        logView.setPadding(0, 32, 0, 0);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(logView);
        root.addView(scroller);

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        startPortProbe();
    }

    @Override
    protected void onResume() {
        super.onResume();
        polling = true;
        handler.post(uiTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        polling = false;
        handler.removeCallbacks(uiTick);
    }

    /** 端口探测必须在后台线程，主线程做 Socket 会抛 NetworkOnMainThreadException 直接闪退。 */
    private void startPortProbe() {
        Thread t = new Thread(() -> {
            while (true) {
                portOpen = probePort();
                // 用户点过停止后，只要端口还活着就继续杀，直到用户主动点「启动」为止
                long stoppedAt = stopRequestedAt;
                if (portOpen && stoppedAt > 0) {
                    killNodeProcessNow();
                }
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "port-probe");
        t.setDaemon(true);
        t.start();
    }

    private boolean probePort() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 3000), 300);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private void sendAction(String action) {
        stopRequestedAt = 0L;
        if (NodeService.ACTION_START.equals(action)) {
            armStartToken();
        }
        try {
            Intent intent = new Intent(this, NodeService.class);
            intent.setAction(action);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Throwable t) {
            appendCrash("sendAction:" + action, t);
        }
        handler.postDelayed(this::render, 600);
    }

    /** 点停止：立刻结束 :node 进程，不等服务回调。 */
    private void stopNow() {
        stopRequestedAt = System.currentTimeMillis();
        clearStartToken();
        status.setText("正在停止…");
        try {
            Intent intent = new Intent(this, NodeService.class);
            intent.setAction(NodeService.ACTION_STOP);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Throwable t) {
            appendCrash("stopIntent", t);
        }
        killNodeProcessNow();
        for (int i = 1; i <= 8; i++) {
            handler.postDelayed(this::render, 80L * i);
        }
    }

    /** 写入一次性启动令牌，服务消费后立即删除；系统重放旧指令时没有令牌可用。 */
    private void armStartToken() {
        try (FileOutputStream fos = openFileOutput("start.token", MODE_PRIVATE)) {
            fos.write(String.valueOf(System.currentTimeMillis()).getBytes("UTF-8"));
        } catch (Throwable t) {
            appendCrash("armStartToken", t);
        }
    }

    private void clearStartToken() {
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(getFilesDir(), "start.token").delete();
        } catch (Throwable ignored) {
        }
    }

    /** 同 UID 下可以直接给自己应用的 :node 进程发 SIGKILL，端口立即释放。 */
    private void killNodeProcessNow() {
        try {
            File[] entries = new File("/proc").listFiles();
            if (entries == null) return;
            for (File dir : entries) {
                if (!dir.isDirectory()) continue;
                String pid = dir.getName();
                if (pid.isEmpty() || !Character.isDigit(pid.charAt(0))) continue;
                String cmd = readSmall(new File(dir, "cmdline"));
                if (cmd != null && cmd.contains("top.izuna.ncmapi:node")) {
                    int targetPid;
                    try {
                        targetPid = Integer.parseInt(pid);
                    } catch (Throwable e) {
                        continue;
                    }
                    // 优先用系统 API 发 SIGKILL（同 UID 允许），失败再退回 shell
                    try {
                        android.os.Process.sendSignal(targetPid, 9);
                        continue;
                    } catch (Throwable ignored) {
                    }
                    try {
                        Runtime.getRuntime().exec(new String[]{"kill", "-9", pid});
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            appendCrash("killNodeProcessNow", t);
        }
    }

    private String readSmall(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[512];
            int n = in.read(buf);
            if (n <= 0) return "";
            return new String(buf, 0, n).replace("\u0000", " ");
        } catch (Throwable e) {
            return null;
        }
    }

    private void render() {
        try {
            String statusText = readFile(NodeService.STATUS_FILE).trim();
            String shown;
            if (portOpen) {
                shown = "● 服务运行中\n127.0.0.1:3000 可访问";
            } else if (statusText.startsWith("failed")) {
                shown = "✕ 启动失败\n" + statusText;
            } else if (statusText.startsWith("exited")) {
                shown = "○ 服务已退出（" + statusText + "）";
            } else if ("starting".equals(statusText)) {
                shown = "… 正在启动";
            } else {
                shown = "○ 未启动";
            }
            status.setText(shown);
            btnStart.setEnabled(!portOpen);
            btnStop.setEnabled(true);

            String log = readFile(NodeService.LOG_FILE);
            if (log.length() > 4000) log = log.substring(log.length() - 4000);
            logView.setText(log);
        } catch (Throwable t) {
            appendCrash("render", t);
        }
    }

    private String readFile(String name) {
        File f = new File(getFilesDir(), name);
        if (!f.exists()) return "";
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            appendCrash("uncaught on " + thread.getName(), throwable);
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }

    private void appendCrash(String where, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String text = "=== " + where + " ===\n" + sw + "\n";
            try (FileOutputStream fos = openFileOutput("crash.txt", MODE_APPEND)) {
                fos.write(text.getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }
}
