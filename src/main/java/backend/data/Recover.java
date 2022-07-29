package backend.data;

import backend.common.SubArray;
import backend.data.dataItem.DataItem;
import backend.data.logger.Logger;
import backend.data.page.Page;
import backend.data.page.PageX;
import backend.data.pageCache.PageCache;
import backend.transaction.TransactionManager;
import backend.utils.Panic;
import backend.utils.Parser;
import com.google.common.primitives.Bytes;

import java.util.*;

/**
 * @author Joe
 * @ClassName Recover.java
 * @Description
 * @createTime 2022年07月29日 15:25:00
 */
public class Recover {
    private static final byte LOG_TYPE_INSERT = 0;
    private static final byte LOG_TYPE_UPDATE = 1;

    private static final int REDO = 0;
    private static final int UNDO = 1;

    /**
     * 插入操作日志格式：[LogType] [XID] [Pgno] [Offset] [Raw]
     * 表示XID将Raw的内容插入到了Pgno页的Offset位移处
     */
    static class InsertLogInfo {
        long xid;
        int pgno;
        short offset;
        byte[] raw;
    }

    /**
     * 更新操作日志格式：[LogType] [XID] [UUID] [OldRaw] [NewRaw]
     * 表示XID将UUID这个dataitem从OldRaw更新为了NewRaw.
     */
    static class UpdateLogInfo {
        long xid;
        int pgno;
        short offset;
        byte[] oldRaw;
        byte[] newRaw;
    }

    /**
     * 恢复策略
     * @param tm
     * @param lg
     * @param pc
     */
    public static void recover(TransactionManager tm, Logger lg, PageCache pc) {
        System.out.println("Recovering...");

        lg.rewind();
        int maxPgno = 0;
        while(true) {
            byte[] log = lg.next();
            if(log == null) {
                break;
            }
            int pgno;
            if(isInsertLog(log)) {
                InsertLogInfo li = parseInsertLog(log);
                pgno = li.pgno;
            } else {
                UpdateLogInfo li = parseUpdateLog(log);
                pgno = li.pgno;
            }
            if(pgno > maxPgno) {
                maxPgno = pgno;
            }
        }
        if(maxPgno == 0) {
            maxPgno = 1;
        }
        pc.truncateByBgno(maxPgno);
        System.out.println("Truncate to " + maxPgno + " pages.");

        redoTranscations(tm, lg, pc);
        System.out.println("Redo Transactions Over.");

        undoTranscations(tm, lg, pc);
        System.out.println("Undo Transactions Over.");

        System.out.println("Recovery Over.");
    }

    /**
     * 重做已完成事务：commit、abort
     *
     * 1. 正序扫描事务 T 的所有日志
     * 2. 如果日志是插入操作 (Ti, I, A, x)，就将 x 重新插入 A 位置
     * 3. 如果日志是更新操作 (Ti, U, A, oldx, newx)，就将 A 位置的值设置为 newx
     * @param tm
     * @param lg
     * @param pc
     */
    private static void redoTranscations(TransactionManager tm, Logger lg, PageCache pc) {
        lg.rewind();
        while(true) {
            byte[] log = lg.next();
            if(log == null) {
                break;
            }
            if(isInsertLog(log)) {
                InsertLogInfo li = parseInsertLog(log);
                long xid = li.xid;
                // 重做非进行中的事务
                if(!tm.isActive(xid)) {
                    doInsertLog(pc, log, REDO);
                }
            } else {
                UpdateLogInfo xi = parseUpdateLog(log);
                long xid = xi.xid;
                if(!tm.isActive(xid)) {
                    doUpdateLog(pc, log, REDO);
                }
            }
        }
    }

    /**
     * 撤销未完成的事务：active
     *
     * 1. 倒序扫描事务 T 的所有日志
     * 2. 如果日志是插入操作 (Ti, I, A, x)，就将 A 位置的数据删除
     * 3. 如果日志是更新操作 (Ti, U, A, oldx, newx)，就将 A 位置的值设置为 oldx
     * @param tm
     * @param lg
     * @param pc
     */
    private static void undoTranscations(TransactionManager tm, Logger lg, PageCache pc) {
        //key : xid value: log
        Map<Long, List<byte[]>> logCache = new HashMap<>();
        lg.rewind();
        while(true) {
            byte[] log = lg.next();
            if(log == null) {
                break;
            }
            if(isInsertLog(log)) {
                InsertLogInfo li = parseInsertLog(log);
                long xid = li.xid;
                if(tm.isActive(xid)) {
                    if(!logCache.containsKey(xid)) {
                        logCache.put(xid, new ArrayList<>());
                    }
                    logCache.get(xid).add(log);
                }
            } else {
                UpdateLogInfo xi = parseUpdateLog(log);
                long xid = xi.xid;
                if(tm.isActive(xid)) {
                    if(!logCache.containsKey(xid)) {
                        logCache.put(xid, new ArrayList<>());
                    }
                    logCache.get(xid).add(log);
                }
            }
        }

        // 对所有active log进行倒序undo
        for(Map.Entry<Long, List<byte[]>> entry : logCache.entrySet()) {
            List<byte[]> logs = entry.getValue();
            for (int i = logs.size()-1; i >= 0; i --) {
                byte[] log = logs.get(i);
                if(isInsertLog(log)) {
                    doInsertLog(pc, log, UNDO);
                } else {
                    doUpdateLog(pc, log, UNDO);
                }
            }
            tm.abort(entry.getKey());
        }
    }

    private static boolean isInsertLog(byte[] log) {
        return log[0] == LOG_TYPE_INSERT;
    }

    // [LogType] [XID] [UID] [OldRaw] [NewRaw]
    private static final int OF_TYPE = 0;
    private static final int OF_XID = OF_TYPE+1;
    private static final int OF_UPDATE_UID = OF_XID+8;
    private static final int OF_UPDATE_RAW = OF_UPDATE_UID+8;

    /**
     * 生成更新日志
     * @param xid
     * @param di
     * @return
     */
    public static byte[] updateLog(long xid, DataItem di) {
        byte[] logType = {LOG_TYPE_UPDATE};
        byte[] xidRaw = Parser.long2Byte(xid);
        byte[] uidRaw = Parser.long2Byte(di.getUid());
        byte[] oldRaw = di.getOldRaw();
        SubArray raw = di. getRaw();
        byte[] newRaw = Arrays.copyOfRange(raw.raw, raw.start, raw.end);
        return Bytes.concat(logType, xidRaw, uidRaw, oldRaw, newRaw);
    }

    /**
     * 将日志数据转为 UpdateLogInfo对象
     * @param log
     * @return
     */
    private static UpdateLogInfo parseUpdateLog(byte[] log) {
        UpdateLogInfo li = new UpdateLogInfo();
        li.xid = Parser.parseLong(Arrays.copyOfRange(log, OF_XID, OF_UPDATE_UID));

        //将 uid 转为 pgno 和 offset
        long uid = Parser.parseLong(Arrays.copyOfRange(log, OF_UPDATE_UID, OF_UPDATE_RAW));
        //TODO 为什么这么做
        li.offset = (short)(uid & ((1L << 16) - 1));
        uid >>>= 32;
        li.pgno = (int)(uid & ((1L << 32) - 1));

        int length = (log.length - OF_UPDATE_RAW) / 2;
        li.oldRaw = Arrays.copyOfRange(log, OF_UPDATE_RAW, OF_UPDATE_RAW+length);
        li.newRaw = Arrays.copyOfRange(log, OF_UPDATE_RAW+length, OF_UPDATE_RAW+length*2);
        return li;
    }

    /**
     * updateLog 的重做和撤销处理
     * @param pc
     * @param log
     * @param flag [REDO/UNDO]
     */
    private static void doUpdateLog(PageCache pc, byte[] log, int flag) {
        int pgno;
        short offset;
        byte[] raw;
        if(flag == REDO) {
            UpdateLogInfo xi = parseUpdateLog(log);
            pgno = xi.pgno;
            offset = xi.offset;
            raw = xi.newRaw;
        } else { //UNDO
            UpdateLogInfo xi = parseUpdateLog(log);
            pgno = xi.pgno;
            offset = xi.offset;
            raw = xi.oldRaw;
        }
        Page pg = null;
        try {
            pg = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        try {
            //用于在数据库崩溃后重新打开时,修改数据使用
            //将raw插入pg中的offset位置，不更新offset
            PageX.recoverUpdate(pg, raw, offset);
        } finally {
            pg.release();
        }
    }

    // [LogType] [XID] [Pgno] [Offset] [Raw]
    private static final int OF_INSERT_PGNO = OF_XID+8;
    private static final int OF_INSERT_OFFSET = OF_INSERT_PGNO+4;
    private static final int OF_INSERT_RAW = OF_INSERT_OFFSET+2;

    /**
     * 生成插入日志
     * @param xid
     * @param pg
     * @param raw
     * @return
     */
    public static byte[] insertLog(long xid, Page pg, byte[] raw) {
        byte[] logTypeRaw = {LOG_TYPE_INSERT};
        byte[] xidRaw = Parser.long2Byte(xid);
        byte[] pgnoRaw = Parser.int2Byte(pg.getPageNumber());
        byte[] offsetRaw = Parser.short2Byte(PageX.getFSO(pg));
        return Bytes.concat(logTypeRaw, xidRaw, pgnoRaw, offsetRaw, raw);
    }

    /**
     * 将日志数据 Data 转为 InsertLogInfo对象
     * @param log
     * @return
     */
    private static InsertLogInfo parseInsertLog(byte[] log) {
        InsertLogInfo li = new InsertLogInfo();
        li.xid = Parser.parseLong(Arrays.copyOfRange(log, OF_XID, OF_INSERT_PGNO));
        li.pgno = Parser.parseInt(Arrays.copyOfRange(log, OF_INSERT_PGNO, OF_INSERT_OFFSET));
        li.offset = Parser.parseShort(Arrays.copyOfRange(log, OF_INSERT_OFFSET, OF_INSERT_RAW));
        li.raw = Arrays.copyOfRange(log, OF_INSERT_RAW, log.length);
        return li;
    }

    /**
     * insertLog 的重做和撤销处理
     * @param pc
     * @param log
     * @param flag
     */
    private static void doInsertLog(PageCache pc, byte[] log, int flag) {
        InsertLogInfo li = parseInsertLog(log);
        Page pg = null;
        try {
            pg = pc.getPage(li.pgno);
        } catch(Exception e) {
            Panic.panic(e);
        }
        try {
            if(flag == UNDO) {
                // 撤销：将插入的数据设为无效
                DataItem.setDataItemRawInvalid(li.raw);
            }
            PageX.recoverInsert(pg, li.raw, li.offset);
        } finally {
            pg.release();
        }
    }
}
