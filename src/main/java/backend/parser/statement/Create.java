package backend.parser.statement;

/**
 * <create statement>
 *     create table <table name>
 *     <field name> <field type>
 *     <field name> <field type>
 *     ...
 *     <field name> <field type>
 *     [(index <field name list>)]
 *         create table students
 *         id int32,
 *         name string,
 *         age int32,
 *         (index id name)
 */
public class Create {
    /**
     * 表名：  [a-zA-Z][a-zA-Z0-9_]*
     */
    public String tableName;
    /**
     * 字段名：  [a-zA-Z][a-zA-Z0-9_]*
     */
    public String[] fieldName;
    /**
     * int32 int64 string
     */
    public String[] fieldType;
    /**
     * 索引
     */
    public String[] index;
}
