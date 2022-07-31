package backend.parser.statement;

/**
 * <insert statement>
 *     insert into <table name> values <value list>
 *         insert into student values 5 "Zhang Yuanjia" 22
 */
public class Insert {
    public String tableName;
    public String[] values;
}
