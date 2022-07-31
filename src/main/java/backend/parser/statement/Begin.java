package backend.parser.statement;

/**
 * <begin statement>
 *     begin [isolation level (read committed|repeatable read)]
 *         begin isolation level read committed
 */
public class Begin {
    /**
     * 默认为 READ COMMITTED
     */
    public boolean isRepeatableRead;
}
