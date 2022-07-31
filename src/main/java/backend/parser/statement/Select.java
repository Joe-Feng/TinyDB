package backend.parser.statement;

/**
 * <select statement>
 *     select (*|<field name list>) from <table name> [<where statement>]
 *         select * from student where id = 1
 *         select name from student where id > 1 and id < 4
 *         select name, age, id from student where id = 12
 */
public class Select {
    public String tableName;
    public String[] fields;
    public Where where;
}
