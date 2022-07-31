package backend.parser.statement;

/**
 * <drop statement>
 *     drop table <table name>
 *         drop table students
 */
public class Drop {
    public String tableName;
}
