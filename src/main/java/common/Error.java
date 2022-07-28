package common;

/**
 * @author Joe
 * @ClassName Error.java
 * @Description 统一异常处理
 * @createTime 2022年07月28日 19:04:00
 */
public class Error {
    /**
     * 统一适用异常
     */
    public static final Exception CacheFullException = new RuntimeException("Cache is full!");
    public static final Exception FileExistsException = new RuntimeException("File already exists!");
    public static final Exception FileNotExistsException = new RuntimeException("File does not exists!");
    public static final Exception FileCannotRWException = new RuntimeException("File cannot read or write!");

    /**
     * 事务管理异常
     */
    public static final Exception BadXIDFileException = new RuntimeException("Bad XID file!");

    /**
     * 数据管理异常
     */
    public static final Exception BadLogFileException = new RuntimeException("Bad log file!");
    public static final Exception MemTooSmallException = new RuntimeException("Memory too small!");
    public static final Exception DataTooLargeException = new RuntimeException("Data too large!");
    public static final Exception DatabaseBusyException = new RuntimeException("Database is busy!");
}
