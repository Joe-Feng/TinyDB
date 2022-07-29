package backend.utils;

/**
 * @author Joe
 * @ClassName Types.java
 * @Description
 * @createTime 2022年07月29日 16:34:00
 */
public class Types {
    public static long addressToUid(int pgno, short offset) {
        long u0 = (long)pgno;
        long u1 = (long)offset;
        return u0 << 32 | u1;
    }
}
