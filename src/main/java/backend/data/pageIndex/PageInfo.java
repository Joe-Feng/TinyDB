package backend.data.pageIndex;

/**
 * @author Joe
 * @ClassName PageInfo.java
 * @Description
 * @createTime 2022年07月29日 16:39:00
 */
public class PageInfo {
    public int pgno;
    public int freeSpace;

    public PageInfo(int pgno, int freeSpace) {
        this.pgno = pgno;
        this.freeSpace = freeSpace;
    }
}
