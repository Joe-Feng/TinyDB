package backend.version;

import backend.common.SubArray;
import backend.data.dataItem.DataItem;
import backend.utils.Parser;
import com.google.common.primitives.Bytes;

import java.util.Arrays;

/**
 * VM 通过管理所有的数据项，向上层提供了记录（Entry）的概念。上层模块通过 VM 操作数据的最小单位，就是记录。
 * VM 则在其内部，为每个记录，维护了多个版本（Version）。每当上层模块对某个记录进行修改时，VM 就会为这个记录创建一个新的版本。
 *
 * 一条记录存储在一条 Data Item 中，所以 Entry 中保存一个 DataItem 的引用即可
 *
 * VM向上层抽象出entry
 * entry结构：
 * [XMIN] [XMAX] [data]
 * XMIN ：创建该条记录（版本）的事务编号，
 * XMAX ：删除该条记录（版本）的事务编号，
 * XMAX 这个变量，也就解释了为什么 DM 层不提供删除操作，当想删除一个版本时，只需要设置其 XMAX，
 * 这样，这个版本对每一个 XMAX 之后的事务都是不可见的，也就等价于删除了。
 * DATA ：这条记录持有的数据
 */
public class Entry {
    private static final int OF_XMIN = 0;
    private static final int OF_XMAX = OF_XMIN+8;
    private static final int OF_DATA = OF_XMAX+8;

    /**
     * 资源标号，用于缓存框架中的方法中
     */
    private long uid;
    /**
     * 一条记录存储在一条 Data Item 中，所以 Entry 中保存一个 DataItem 的引用即可
     */
    private DataItem dataItem;
    private VersionManager vm;

    /**
     * 创建新的记录
     * @param vm
     * @param dataItem
     * @param uid
     * @return
     */
    public static Entry newEntry(VersionManager vm, DataItem dataItem, long uid) {
        Entry entry = new Entry();
        entry.uid = uid;
        entry.dataItem = dataItem;
        entry.vm = vm;
        return entry;
    }

    /**
     * 获取记录
     * @param vm
     * @param uid
     * @return
     * @throws Exception
     */
    public static Entry loadEntry(VersionManager vm, long uid) throws Exception {
        DataItem di = ((VersionManagerImpl)vm).dm.read(uid);
        return newEntry(vm, di, uid);
    }

    /**
     * 包装为记录格式
     * @param xid 事务号
     * @param data 数据
     * @return
     */
    public static byte[] wrapEntryRaw(long xid, byte[] data) {
        byte[] xmin = Parser.long2Byte(xid);
        byte[] xmax = new byte[8];
        return Bytes.concat(xmin, xmax, data);
    }

    public void release() {
        ((VersionManagerImpl)vm).releaseEntry(this);
    }

    public void remove() {
        dataItem.release();
    }

    /**
     * 以拷贝的形式返回数据内容
     * @return
     */
    public byte[] data() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            byte[] data = new byte[sa.end - sa.start - OF_DATA];
            System.arraycopy(sa.raw, sa.start+OF_DATA, data, 0, data.length);
            return data;
        } finally {
            dataItem.rUnLock();
        }
    }

    /**
     * 获取创建该条记录（版本）的事务编号
     * @return
     */
    public long getXmin() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start+OF_XMIN, sa.start+OF_XMAX));
        } finally {
            dataItem.rUnLock();
        }
    }

    /**
     * 获取删除该记录的事务编号
     * @return
     */
    public long getXmax() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start+OF_XMAX, sa.start+OF_DATA));
        } finally {
            dataItem.rUnLock();
        }
    }

    /**
     * 设置删除记录的事务
     * DM 会保证对 DataItem 的修改是原子性的，所以要使用 before() 和 after()
     * @param xid
     */
    public void setXmax(long xid) {
        dataItem.before();
        try {
            SubArray sa = dataItem.data();
            System.arraycopy(Parser.long2Byte(xid), 0, sa.raw, sa.start+OF_XMAX, 8);
        } finally {
            dataItem.after(xid);
        }
    }

    /**
     * 获取资源编号（缓存中）
     * @return
     */
    public long getUid() {
        return uid;
    }
}
