# JAVA Marmot

打包jar

```shell
$ mvn clean package
```

将仓库部署到当前目录下, 使用GitHub作为仓库地址

```shell
$ ./scripts.deploy.sh
```

maven 仓库地址配置

```xml
<repository>
    <id>marmot-support</id>
    <url>https://raw.githubusercontent.com/evan2x/java-marmot/master/repository</url>
</repository>
```

maven 依赖配置

```xml

<dependency>
    <groupId>com.creditease</groupId>
    <artifactId>marmot</artifactId>
    <version>0.3.6</version>
</dependency>
```

SpringMVC 拦截器:

```java
com.creditease.marmot.support.spring.ProviderViewInterceptor
```