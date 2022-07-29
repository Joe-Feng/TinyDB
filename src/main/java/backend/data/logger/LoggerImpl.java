package backend.data.logger;

import backend.utils.Panic;
import backend.utils.Parser;
import com.google.common.primitives.Bytes;
import common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 日志文件读写
 *
 * 日志文件标准格式为：
 * [XChecksum] [Log1] [Log2] ... [LogN] [BadTail]
 * XChecksum 为后续所有日志计算的Checksum，int类型
 * BadTail 是在数据库崩溃时，没有来得及写完的日志数据，这个 BadTail 不一定存在
 *
 *
 * 每条正确日志的格式为：
 * [Size] [Checksum] [Data]
 * Size 4字节int 标识Data长度
 * Checksum 4字节int,是该条日志的校验和
 */
public class LoggerImpl implements Logger{
    /**
     * 种子，用于生成校验和
     */
    private static final int SEED = 13331;

    private static final int OF_SIZE = 0;
    private static final int OF_CHECKSUM = OF_SIZE + 4;
    private static final int OF_DATA = OF_CHECKSUM + 4;

    public static final String LOG_SUFFIX = ".log";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock lock;

    /**
     * 当前日志指针的位置
     */
    private long position;

    /**
     * 初始化时记录，log操作不更新
     */
    private long fileSize;

    /**
     * 后续所有日志计算的Checksum
     */
    private int xChecksum;

    LoggerImpl(RandomAccessFile raf, FileChannel fc) {
        this.file = raf;
        this.fc = fc;
        lock = new ReentrantLock();
    }

    LoggerImpl(RandomAccessFile raf, FileChannel fc, int xChecksum) {
        this.file = raf;
        this.fc = fc;
        this.xChecksum = xChecksum;
        lock = new ReentrantLock();
    }

    /**
     * 从一个已存在日志文件获取初始化数据
     */
    void init() {
        long size = 0;
        try {
            size = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }
        if(size < 4) {
            Panic.panic(Error.BadLogFileException);
        }

        ByteBuffer raw = ByteBuffer.allocate(4);
        try {
            fc.position(0);
            fc.read(raw);
        } catch (IOException e) {
            Panic.panic(e);
        }
        int xChecksum = Parser.parseInt(raw.array());
        this.fileSize = size;
        this.xChecksum = xChecksum;

        checkAndRemoveTail();
    }


    /**
     * 在打开一个日志文件时，需要首先校验日志文件的 XChecksum，并移除文件尾部可能存在的 BadTail，
     * 由于 BadTail 该条日志尚未写入完成，文件的校验和也就不会包含该日志的校验和，去掉 BadTail 即可保证日志文件的一致性。
     *
     * 检查并移除bad tail
     */
    private void checkAndRemoveTail() {
        rewind();

        int xCheck = 0;
        while(true) {
            byte[] log = internNext();
            if(log == null) {
                break;
            }
            xCheck = calChecksum(xCheck, log);
        }
        if(xCheck != xChecksum) {
            Panic.panic(Error.BadLogFileException);
        }

        try {
            //截断文件到正常日志的结尾
            truncate(position);
        } catch (Exception e) {
            Panic.panic(e);
        }
        try {
            //设置文件指针指向 position 位置
            file.seek(position);
        } catch (IOException e) {
            Panic.panic(e);
        }
        rewind();
    }

    /**
     * 生成一条日志的校验和
     * 单条日志的校验和，其实就是通过一个指定的种子实现的
     * @param xCheck
     * @param log
     * @return
     */
    private int calChecksum(int xCheck, byte[] log) {
        for (byte b : log) {
            xCheck = xCheck * SEED + b;
        }
        return xCheck;
    }

    /**
     * 向日志文件写入日志
     * @param data
     */
    @Override
    public void log(byte[] data) {
        //将数据包裹成日志格式
        byte[] log = wrapLog(data);
        //插入日志
        ByteBuffer buf = ByteBuffer.wrap(log);
        lock.lock();
        try {
            fc.position(fc.size());
            fc.write(buf);
        } catch(IOException e) {
            Panic.panic(e);
        } finally {
            lock.unlock();
        }
        //更新文件校验和
        updateXChecksum(log);
    }

    /**
     * 更新文件的校验和
     * @param log
     */
    private void updateXChecksum(byte[] log) {
        this.xChecksum = calChecksum(this.xChecksum, log);
        try {
            fc.position(0);
            fc.write(ByteBuffer.wrap(Parser.int2Byte(xChecksum)));
            fc.force(false);
        } catch(IOException e) {
            Panic.panic(e);
        }
    }

    /**
     * 将数据包裹成日志格式
     * @param data
     * @return
     */
    private byte[] wrapLog(byte[] data) {
        byte[] checksum = Parser.int2Byte(calChecksum(0, data));
        byte[] size = Parser.int2Byte(data.length);
        return Bytes.concat(size, checksum, data);
    }

    /**
     * 截断日志到 x 地址位置
     * @param x
     * @throws Exception
     */
    @Override
    public void truncate(long x) throws Exception {
        lock.lock();
        try {
            fc.truncate(x);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取一条日志的 Data
     * @return
     */
    private byte[] internNext() {
        // position 指向该条日志的起始位置，是绝对地址
        // OF_DATA 是相对地址，值为8
        if(position + OF_DATA >= fileSize) {
            return null;
        }
        //读取当前日志的 Size
        ByteBuffer tmp = ByteBuffer.allocate(4);
        try {
            fc.position(position);
            fc.read(tmp);
        } catch(IOException e) {
            Panic.panic(e);
        }
        int size = Parser.parseInt(tmp.array());
        if(position + size + OF_DATA > fileSize) {
            return null;
        }
        //读取当前日志的 Data
        ByteBuffer buf = ByteBuffer.allocate(OF_DATA + size);
        try {
            fc.position(position);
            fc.read(buf);
        } catch(IOException e) {
            Panic.panic(e);
        }

        byte[] log = buf.array();
        //根据Data生成校验和，并和当前日志校验和比较，判断数据是否正确
        int checkSum1 = calChecksum(0, Arrays.copyOfRange(log, OF_DATA, log.length));
        int checkSum2 = Parser.parseInt(Arrays.copyOfRange(log, OF_CHECKSUM, OF_DATA));
        if(checkSum1 != checkSum2) {
            return null;
        }
        // position 指向下一条日志的起始位置
        position += log.length;
        return log;
    }

    /**
     * Logger 被实现成迭代器模式，通过 next() 方法，不断地从文件中读取下一条日志，并将其中的 Data 解析出来并返回。
     * 迭代器模式：提供一种方法顺序访问一个聚合对象中各个元素, 而又无须暴露该对象的内部表示
     * @return 日志中的 data 数据
     */
    @Override
    public byte[] next() {
        lock.lock();
        try {
            byte[] log = internNext();
            if(log == null) {
                return null;
            }
            return Arrays.copyOfRange(log, OF_DATA, log.length);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 初始化 position
     */
    @Override
    public void rewind() {
        position = 4;
    }

    @Override
    public void close() {
        try {
            fc.close();
            file.close();
        } catch(IOException e) {
            Panic.panic(e);
        }
    }
}
