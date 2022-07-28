package backend.common;

/**
 * @author Joe
 * @ClassName SubArray.java
 * @Description 共享内存数组：通过 SubArray 来（松散地）规定这个数组的可使用范围
 * @createTime 2022年07月28日 21:41:00
 */
public class SubArray {
    public byte[] raw;
    public int start;
    public int end;

    public SubArray(byte[] raw, int start, int end) {
        this.raw = raw;
        this.start = start;
        this.end = end;
    }
}
