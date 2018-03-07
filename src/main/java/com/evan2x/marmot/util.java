
package com.evan2x.marmot;

import javax.lang.model.type.UnknownTypeException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;

import com.evan2x.marmot.object.RemoteData;

public class util {
  /**
   * 使用指定provider请求远程数据
   * @param hostPath 代理地址
   * @param cookies 附加的cookie
   * @param request 原request对象
   * @param response 原response对象
   * @return RemoteDataBean
   * @throws IOException
   */
  public static RemoteData requestRemoteData(String hostPath, List<Cookie> cookies, HttpServletRequest request, HttpServletResponse response)
          throws IOException {
    String queryString = request.getQueryString();
    String uri = request.getRequestURI();

    if (queryString == null) {
      queryString = "";
    } else {
      queryString = "?" + queryString;
    }

    if (hostPath.matches("^(?i)http(?:s)?://[\\s\\S]*$")) {
      hostPath = hostPath + uri + queryString;
    } else {
      String scheme = request.getScheme() + "://";
      hostPath = hostPath.replaceAll("^(?i)(?:[a-z]+:/+)|(?:/*)", "");
      hostPath = scheme + hostPath + uri + queryString;
    }

    return sendRequest(hostPath, cookies, request, response);
  }

  /**
   * 发起请求
   * @param url 请求地址
   * @param cookies 附加的cookies
   * @param request 原request对象
   * @param response 原response对象
   * @return RemoteDataBean
   * @throws IOException
   */
  private static RemoteData sendRequest(String url, List<Cookie> cookies, HttpServletRequest request, HttpServletResponse response)
          throws IOException, UnknownTypeException {
    URL action = new URL(url);
    HttpURLConnection connection = (HttpURLConnection) action.openConnection();
    setRequestHeader(request, connection, cookies);

    try {
      connection.connect();
    } catch(ConnectException e) {
      throw new ConnectException("Failed to connect to " + url + ": Connection refused");
    }

    int statusCode = connection.getResponseCode();
    if (statusCode == HttpURLConnection.HTTP_MOVED_PERM || statusCode == HttpURLConnection.HTTP_MOVED_TEMP) {
      String location = connection.getHeaderField("location");
      if (location != null) {
        return sendRequest(location, cookies, request, response);
      }
    }

    InputStream inputStream;
    String contentEncoding = connection.getContentEncoding();
    try {
      if (contentEncoding == null) {
        inputStream = connection.getInputStream();
      } else {
        switch (contentEncoding.toLowerCase()) {
          case "gzip":
            inputStream = new GZIPInputStream(connection.getInputStream());
            break;
          case "deflate":
            inputStream = new DeflaterInputStream(connection.getInputStream());
            break;
          default:
            throw new IOException("Does not support the Content-Encoding: " + contentEncoding);
        }
      }
    } catch (IOException e) {
      throw new IOException("Server returned HTTP response code: " + statusCode + " for URL: " + url);
    }

    RemoteData remoteData = new RemoteData();

    remoteData.setUrl(url);
    remoteData.setData(streamToByte(inputStream));

    setResponseHeader(response, connection);
    response.setHeader("X-Target-Url", url);

    inputStream.close();
    return remoteData;
  }

  /**
   * 设置发起请求的headers
   * @param request 原request对象
   * @param connection 发起请求的connection
   * @param cookies 向provider发起请求时, 携带上附加的cookie, 当本次请求中已存在一样的cookie name, 则不进行覆盖
   * @throws IOException
   */
  private static void setRequestHeader(HttpServletRequest request, HttpURLConnection connection, List<Cookie> cookies)
          throws IOException {
    // 提取原请求中的Headers
    Enumeration<String> names = request.getHeaderNames();
    // 跳过此Set中的header name
    List<String> breakHeaders = new ArrayList<String>(){{
      add("host");
      add("cookie");
    }};

    // 与原请求类型保持一致
    connection.setRequestMethod(request.getMethod());
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (name == null || breakHeaders.contains(name.toLowerCase())) {
        continue;
      }
      String value = request.getHeader(name);

      // 与原请求的header字段保持一致
      connection.setRequestProperty(name, value);
    }

    // 单独处理Cookie
    String cookieByHeader = request.getHeader("cookie");
    StringBuilder cookieBuilder = new StringBuilder();

    if (cookieByHeader != null && !cookieByHeader.isEmpty()) {
      cookieBuilder.append(cookieByHeader);
    } else {
      cookieByHeader = "";
    }
    if (cookies != null && !cookies.isEmpty()) {
      for (Cookie cookie : cookies) {

        String cookieName = cookie.getName();
        if (cookieByHeader.isEmpty() || cookieByHeader.matches("[^\b]*\\b"+ cookieName +"=.+")) {
          cookieBuilder.append(";").append(cookie.getName()).append("=").append(cookie.getValue());
        }
      }
    }

    connection.setRequestProperty("cookie", cookieBuilder.toString());

    // 该请求由marmot发起
    connection.setRequestProperty("X-Requested-With", "MarmotHttpRequest");

    // 处理请求的body
    BufferedReader reader = request.getReader();
    StringBuilder builder = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      builder.append(line);
    }

    if (!builder.toString().isEmpty()) {
      byte[] bytes = builder.toString().getBytes();
      connection.setDoOutput(true);
      connection.getOutputStream().write(bytes);
      reader.close();
    }
  }

  /**
   * 设置响应给浏览器端的headers
   * @param response 原response对象
   * @param connection 发起请求的connection
   */
  private static void setResponseHeader(HttpServletResponse response, HttpURLConnection connection)
          throws IOException {
    Map<String, List<String>> fields = connection.getHeaderFields();
    // 跳过此Set中的header name
    List<String> breakHeaders = new ArrayList<String>(){{
      add("content-encoding");
      add("transfer-encoding");
    }};

    for (String key : fields.keySet()) {
      if (key == null || breakHeaders.contains(key.toLowerCase())) {
        continue;
      }
      List<String> list = fields.get(key);

      // cookie 特殊处理
      if (key.equalsIgnoreCase("set-cookie")) {
        addCookies(response, list);
      } else {
        response.setHeader(key, list2string(list, ','));
      }
    }
  }

  /**
   * 提取content-type中的类型部分, 忽略掉charset部分. 当输入的content-type是无效的类型时, 将返回text/plain
   * @param contentType 原content-type字符串
   * @return 经过处理的content-type
   */
  public static String extractContentType(String contentType) {
    Matcher matcher = Pattern.compile("[a-zA-Z]+/[\\w+-.*]+").matcher(contentType);
    matcher.find();
    String result = matcher.group(0);
    if (result != null && !result.isEmpty()) {
      return result.toLowerCase();
    }

    return "text/plain";
  }

  /**
   * list to string
   * @param list 需要转为String的List
   * @param separator 字符串的分隔符
   * @return 转化后的字符串
   */
  private static String list2string(List<String> list, char separator) {
    StringBuilder builder = new StringBuilder();

    for (int i = 0; i < list.size(); i++) {
      if (i > 0) {
        builder.append(separator);
      }
      builder.append(list.get(i));
    }

    return builder.toString();
  }

  /**
   * 添加多个cookie
   * @param response response
   * @param cookies cookies列表
   */
  private static void addCookies(HttpServletResponse response, List<String> cookies) {
    for (String cookieItem : cookies) {
      String[] cookieProperties = cookieItem.split(";");

      // 取cookie的key 和 value
      String[] kv = cookieProperties[0].split("=");

      // 构造cookie
      Cookie cookie = new Cookie(kv[0], kv[1]);
      for (String cookiePropertyItem : cookieProperties) {
        String[] cookieProperty = cookiePropertyItem.split("=");
        String key = cookieProperty[0].trim();
        String value = "";

        if (cookieProperty.length > 1) {
          value = cookieProperty[1];
        }

        if (key.equalsIgnoreCase("max-age")) {
          cookie.setMaxAge(Integer.parseInt(value));
        }

        if (key.equalsIgnoreCase("path")) {
          cookie.setPath(value);
        }

        if (key.equalsIgnoreCase("domain")) {
          cookie.setDomain(value);
        }

        cookie.setSecure(key.equalsIgnoreCase("secure"));
        cookie.setHttpOnly(key.equalsIgnoreCase("httpOnly"));
      }

      response.addCookie(cookie);
    }
  }

  /**
   * 输入流转为byte[]
   * @param in
   * @return
   * @throws IOException
   */
  public static byte[] streamToByte(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[1024];
    int len;

    while ((len = in.read(buf)) != -1) {
      out.write(buf, 0, len);
    }

    return out.toByteArray();
  }

  /**
   * 多个斜杠"/"替换为一个
   * @param path 待处理路径
   * @return 替换后的路径
   */
  public static String uniqueBySerialSlash(String path){
    return path.replaceAll("(/)+\\1", "$1");
  }

  /**
   * 去除路径中首尾的斜杠"/"
   * @param path 待处理路径
   * @return 去除了首尾斜杠"/"的路径
   */
  public static String trimSlash(String path){
    int length = path.length();

    if (length < 2) {
      return path;
    }

    if (path.startsWith("/")) {
      path = path.substring(1, length);
    }

    if (length > 2 && path.endsWith("/")) {
      path = path.substring(0, length - 1);
    }

    return path;
  }

  /**
   * 根据matcher匹配到的子分组，对字符串中的$1,$2,$3....替换为指定分组中的内容
   * @param matcher 匹配组
   * @param str 待处理字符串
   * @return 经过替换的字符串
   */
  public static String replaceByMatcher(Matcher matcher, String str) {
    for (int i = 1; i <= matcher.groupCount(); i++) {
      str = str.replace("$" + i, matcher.group(i));
    }

    return str;
  }
}
