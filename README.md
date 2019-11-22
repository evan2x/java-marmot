# JAVA Marmot

打包jar

```shell
$ mvn clean package
```

将仓库部署到当前目录下, 使用GitHub作为仓库地址

```shell
$ ./scripts/deploy.sh
```

maven 仓库地址配置

```xml
<repository>
    <id>marmot</id>
    <url>https://raw.githubusercontent.com/evan2x/java-marmot/master/repository</url>
</repository>
```

maven 依赖配置

```xml

<dependency>
    <groupId>com.evan2x</groupId>
    <artifactId>marmot</artifactId>
    <version>0.3.11</version>
</dependency>
```

让SpringMVC支持Marmot proxy 视图的拦截器:

```java
com.evan2x.marmot.support.spring.ProxyViewInterceptor
```
