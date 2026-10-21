package top.izuna.ncmapi;

/** 通过 JNI 调用内置 Node 运行时。 */
public class NodeRunner {
    static {
        System.loadLibrary("node");
        System.loadLibrary("ncmnode");
    }

    /** 阻塞运行 Node，直到进程结束。必须在后台线程调用。 */
    public static native int startNode(String dataDir, String scriptPath);

    /** 通知 Node 事件循环停止，让 startNode 返回。 */
    public static native void stopNode();
}