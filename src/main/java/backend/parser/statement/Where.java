package backend.parser.statement;

/**
 * 目前 Where 只支持两个条件的与和或
 * 只支持已索引字段作为 Where 的条件
 * 计算 Where 的范围，具体可以查看 Table 的 parseWhere() 和 calWhere() 方法，以及 Field 类的 calExp() 方法。
 * <where statement>
 *     where <field name> (>|<|=) <value> [(and|or) <field name> (>|<|=) <value>]
 *         where age > 10 or age < 3
 */
public class Where {
    /**
     * 表达式1
     */
    public SingleExpression singleExp1;
    /**
     *逻辑运算符：or and
     */
    public String logicOp;
    /**
     * 表达式2
     */
    public SingleExpression singleExp2;
}
