# TinyDB

TinyDB 是一个 Java 实现的简单的数据库，参考项目：NYADB2(https://github.com/qw4990/NYADB2)

## 实现的功能

1. 数据的可靠性和数据恢复
2. 两段锁协议（2PL）实现可串行化调度
3. MVCC
4. 两种事务隔离级别（读提交和可重复读）
5. 死锁处理
6. 简单的表和字段管理
7. 简陋的 SQL 解析（因为懒得写词法分析和自动机，就弄得比较简陋）
8. 基于 socket 的 server 和 client


## 运行方式
注意首先需要在 pom.xml 中调整编译版本，如果导入 IDE，请更改项目的编译版本以适应你的 JDK(支持JDK8)

1. 首先执行以下命令编译源码：
`mvn compile`

2. 接着执行以下命令以 /tmp/tinydb 作为路径创建数据库：
`mvn exec:java -Dexec.mainClass="backend.Launcher" -Dexec.args="-create /tmp/tinydb"`

3. 随后通过以下命令以默认参数启动数据库服务：
`mvn exec:java -Dexec.mainClass="backend.Launcher" -Dexec.args="-open /tmp/tinydb"`

4. 这时数据库服务就已经启动在本机的 9999 端口。重新启动一个终端，执行以下命令启动客户端连接数据库：
`mvn exec:java -Dexec.mainClass="client.Launcher"`
会启动一个交互式命令行，就可以在这里输入类 SQL 语法，回车会发送语句到服务，并输出执行的结果。
