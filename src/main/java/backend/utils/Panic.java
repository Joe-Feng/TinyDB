package backend.utils;

/**
 * @author Joe
 * @ClassName Panic.java
 * @Description 异常处理
 * @createTime 2022年07月28日 19:35:00
 */
public class Panic {
    public static void panic(Exception err) {
        err.printStackTrace();
        System.exit(1);
    }
}
