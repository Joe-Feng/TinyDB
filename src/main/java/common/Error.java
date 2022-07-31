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

    /**
     * 版本控制异常
     */
    public static final Exception DeadlockException = new RuntimeException("Deadlock!");
    public static final Exception ConcurrentUpdateException = new RuntimeException("Concurrent update issue!");
    public static final Exception NullEntryException = new RuntimeException("Null entry!");

    /**
     * 表与字段管理异常
     */
    public static final Exception InvalidFieldException = new RuntimeException("Invalid field type!");
    public static final Exception FieldNotFoundException = new RuntimeException("Field not found!");
    public static final Exception FieldNotIndexedException = new RuntimeException("Field not indexed!");
    public static final Exception InvalidLogOpException = new RuntimeException("Invalid logic operation!");
    public static final Exception InvalidValuesException = new RuntimeException("Invalid values!");
    public static final Exception DuplicatedTableException = new RuntimeException("Duplicated table!");
    public static final Exception TableNotFoundException = new RuntimeException("Table not found!");

    /**
     * SQL 解析异常
     */
    public static final Exception InvalidCommandException = new RuntimeException("Invalid command!");
    public static final Exception TableNoIndexException = new RuntimeException("Table has no index!");

    /**
     * 传输异常
     */
    public static final Exception InvalidPkgDataException = new RuntimeException("Invalid package data!");

    /**
     * 服务器异常
     */
    public static final Exception NestedTransactionException = new RuntimeException("Nested transaction not supported!");
    public static final Exception NoTransactionException = new RuntimeException("Not in transaction!");

    /**
     * 启动异常
     */
    public static final Exception InvalidMemException = new RuntimeException("Invalid memory!");
}
