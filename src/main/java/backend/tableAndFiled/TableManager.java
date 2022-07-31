package backend.tableAndFiled;

import backend.data.DataManager;
import backend.parser.statement.*;
import backend.utils.Parser;
import backend.version.VersionManager;

/**
 * 由于 TableManager 已经是直接被最外层 Server 调用（MYDB 是 C/S 结构），
 * 这些方法直接返回执行的结果，例如错误信息或者结果信息的字节数组（可读）。
 */
public interface TableManager {
    BeginRes begin(Begin begin);
    byte[] commit(long xid) throws Exception;
    byte[] abort(long xid);

    byte[] show(long xid);
    byte[] create(long xid, Create create) throws Exception;

    byte[] insert(long xid, Insert insert) throws Exception;
    byte[] read(long xid, Select select) throws Exception;
    byte[] update(long xid, Update update) throws Exception;
    byte[] delete(long xid, Delete delete) throws Exception;

    public static TableManager create(String path, VersionManager vm, DataManager dm) {
        Booter booter = Booter.create(path);
        booter.update(Parser.long2Byte(0));
        return new TableManagerImpl(vm, dm, booter);
    }

    public static TableManager open(String path, VersionManager vm, DataManager dm) {
        Booter booter = Booter.open(path);
        return new TableManagerImpl(vm, dm, booter);
    }
}
