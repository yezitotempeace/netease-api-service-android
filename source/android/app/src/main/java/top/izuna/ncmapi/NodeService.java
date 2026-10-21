package top.izuna.ncmapi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 纯手动服务：只有 ACTION_START 会启动 Node；
 * 其它任何情况（ACTION_STOP、系统重启、null intent）一律立刻结束进程。
 * 不做任何自拉起。
 */
public class NodeService extends Service {
    private static final String TAG = "NcmApiService";
    public static final String ACTION_START = "top.izuna.ncmapi.action.START";
    public static final String ACTION_STOP = "top.izuna.ncmapi.action.STOP";
    public static final String STATUS_FILE = "ncm-status.txt";
    public static final String LOG_FILE = "ncm-log.txt";
    private static final String CHANNEL_ID = "ncm_service";

    private volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        try {
            startForeground(1001, buildNotification("网易云服务"));
        } catch (Throwable t) {
            Log.w(TAG, "startForeground failed", t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        // 只认「启动」；停止、系统重启、空 intent 全部就地结束，绝不自动拉起
        if (!ACTION_START.equals(action)) {
            appendLog(ACTION_STOP.equals(action) ? "停止服务" : "拒绝自动启动");
            writeStatus("stopped");
            stopForeground(true);
            stopSelf();
            Process.killProcess(Process.myPid());
            return START_NOT_STICKY;
        }

        // 只有界面亲手写下的「启动令牌」才允许启动；
        // 系统重放旧的 START intent 时令牌已被消费，这里会直接拒绝。
        if (!consumeStartToken()) {
            appendLog("拒绝自动启动（没有启动令牌）");
            writeStatus("stopped");
            stopForeground(true);
            stopSelf();
            Process.killProcess(Process.myPid());
            return START_NOT_STICKY;
        }

        if (!running) {
            running = true;
            writeStatus("starting");
            appendLog("开始启动服务…");

            // Node 需要大栈（默认 Java 线程只有 1MB，会栈溢出）
            Thread worker = new Thread(null, new Runnable() {
                @Override
                public void run() {
                    try {
                        File dir = prepareRuntime();
                        appendLog("运行目录：" + dir.getAbsolutePath());
                        writeStatus("running");
                        updateNotification("网易云服务运行中 · 127.0.0.1:3000");
                        int code = NodeRunner.startNode(
                                dir.getAbsolutePath(),
                                new File(dir, "main.js").getAbsolutePath());
                        appendLog("Node 已退出，code=" + code);
                        writeStatus("exited:" + code);
                    } catch (Throwable t) {
                        Log.e(TAG, "node start failed", t);
                        appendLog("启动失败：" + Log.getStackTraceString(t));
                        writeStatus("failed:" + t.getClass().getSimpleName() + ":" + t.getMessage());
                    } finally {
                        running = false;
                    }
                }
            }, "node-main", 32L * 1024 * 1024);
            worker.start();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // 不调用 node::Stop（它需要 Environment*，传空会段错误）
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** 启动令牌：界面点「启动」时写入，服务消费后删除；系统重放不会带令牌。 */
    private boolean consumeStartToken() {
        File token = new File(getFilesDir(), "start.token");
        if (!token.exists()) return false;
        boolean fresh = System.currentTimeMillis() - token.lastModified() < 15000;
        //noinspection ResultOfMethodCallIgnored
        token.delete();
        return fresh;
    }

    private void createChannel() {        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "网易云服务", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("保持网易云 API 服务在后台运行");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle("网易云服务")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true);
        return b.build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(1001, buildNotification(text));
    }

    /** 首次运行把 assets/ncm.zip 解压到 filesDir/ncm。 */
    private File prepareRuntime() throws IOException {
        File root = new File(getFilesDir(), "ncm");
        File entry = new File(root, "main.js");
        if (entry.exists()) {
            appendLog("运行环境已就绪");
            return root;
        }
        root.mkdirs();
        appendLog("解压内置运行时…");
        int count = 0;
        try (InputStream in = getAssets().open("ncm.zip");
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry ze;
            byte[] buf = new byte[16384];
            while ((ze = zis.getNextEntry()) != null) {
                File out = new File(root, ze.getName());
                if (ze.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int n;
                    while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                }
                count++;
            }
        }
        appendLog("解压完成，共 " + count + " 个文件");
        return root;
    }

    private void writeStatus(String status) {
        try (FileOutputStream fos = openFileOutput(STATUS_FILE, MODE_PRIVATE)) {
            fos.write(status.getBytes("UTF-8"));
        } catch (IOException e) {
            Log.w(TAG, "writeStatus failed", e);
        }
    }

    private void appendLog(String line) {
        Log.i(TAG, line);
        try (FileOutputStream fos = openFileOutput(LOG_FILE, MODE_APPEND)) {
            fos.write((line + "\n").getBytes("UTF-8"));
        } catch (IOException e) {
            Log.w(TAG, "appendLog failed", e);
        }
    }
}
