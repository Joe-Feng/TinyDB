package backend.transaction;

import backend.utils.Panic;
import common.Error;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Joe
 * @ClassName TransactionManager.java
 * @Description TM 通过维护 XID 文件来维护事务的状态，并提供接口供其他模块来查询某个事务的状态
 * @createTime 2022年07月28日 19:27:00
 */
public interface TransactionManager {
    long begin(); //开启一个事务
    void commit(long xid); //提交一个事务
    void abort(long xid); //取消一个事务
    boolean isActive(long xid); //查询一个事务的状态是否早正在进行的状态
    boolean isCommitted(long xid); //查询一个事务的状态是否已经提交
    boolean isAborted(long xid); //查询一个事务的状态是否已取消
    void close(); //关闭TM

    /**
     * 创建一个 xid 文件并创建 TM
     * @param path
     * @return
     */
    public static TransactionManagerImpl create(String path) {
        File f = new File(path+TransactionManagerImpl.XID_SUFFIX);
        try {
            if(!f.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e) {
            Panic.panic(e);
        }
        if(!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }


        //从零创建 XID 文件时需要写一个空的 XID 文件头，即设置 xidCounter 为 0，否则后续在校验时会不合法
        ByteBuffer buf = ByteBuffer.wrap(new byte[TransactionManagerImpl.LEN_XID_HEADER_LENGTH]);
        try {
            fc.position(0);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }

        return new TransactionManagerImpl(raf, fc);
    }

    /**
     * 从一个已有的 xid 文件来创建 TM
     * @param path
     * @return
     */
    public static TransactionManagerImpl open(String path) {
        File f = new File(path+TransactionManagerImpl.XID_SUFFIX);
        if(!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        if(!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        return new TransactionManagerImpl(raf, fc);
    }
}
