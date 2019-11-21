
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
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;

import com.evan2x.marmot.object.RemoteData;

public class util {
  /**
   * 使用指定proxy请求远程数据
   * @param hostPath 代理地址
   * @param cookies 附加的cookie
   * @param request 原request对象
   * @param response 原response对象
   * @return RemoteDataBean
   * @throws IOException
   */
  public static RemoteData requestRemoteData(String hostPath, List<Cookie> cookies, HttpServletRequest request, HttpServletResponse response)
          throws IOException {
    String qs = request.getQueryString();
    String pathname = request.getRequestURI();
    String url;

    if (hostPath.matches("^(?i)http(?:s)?://[\\s\\S]*$")) {
      url = normalizeUrl(hostPath, pathname, qs);
    } else {
      String scheme = request.getScheme() + "://";
      hostPath = hostPath.replaceAll("^(?i)(?:[a-z]+:/+)|(?:/*)", "");
      url = normalizeUrl(scheme + hostPath, pathname, qs);
    }

    return sendRequest(url, cookies, request, response);
  }

  private static String normalizeUrl(String host, String pathname, String qs) {
    host = util.trimEnd(host, '/');
    pathname = util.trimStart(pathname, '/');

    if (qs == null) {
      qs = "";
    } else {
      qs = "?" + qs;
    }

    return host + "/" + pathname + qs;
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
   * @param cookies 向proxy发起请求时, 携带上附加的cookie, 当本次请求中已存在一样的cookie name, 则不进行覆盖
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

    if (isNotEmpty(cookieByHeader)) {
      cookieBuilder.append(cookieByHeader);
    } else {
      cookieByHeader = "";
    }

    if (cookies != null && !cookies.isEmpty()) {
      for (Cookie cookie : cookies) {
        String cookieName = cookie.getName();
        if (isEmpty(cookieByHeader) || cookieByHeader.matches("[^\b]*\\b"+ cookieName +"=.+")) {
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

    if (isNotEmpty(builder.toString())) {
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
   * 将输入流转为字符串
   * @param stream 输入流
   * @return 解析后的的JSON Object
   * @throws IOException
   */
  public static String stream2string(InputStream stream)
    throws IOException {
    if (stream == null) {
      return null;
    }

    InputStreamReader inputReader = new InputStreamReader(stream, "UTF-8");
    BufferedReader buffer = new BufferedReader(inputReader);
    StringBuilder builder = new StringBuilder();
    String line;
    while ((line = buffer.readLine()) != null) {
      builder.append(line);
    }

    buffer.close();
    inputReader.close();
    stream.close();

    return builder.toString();
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
  public static String uniqueSerialSlash(String path){
    return path.replaceAll("(/)+\\1", "$1");
  }

  /**
   * 去除字符串头部中指定的字符列表
   * @param str
   * @param trimChars
   * @return
   */
  public static String trimStart(String str, char[] trimChars) {
    int index = 0;

    while (index < str.length()) {
      boolean exists = false;

      for (char trimChar : trimChars) {
        if (str.charAt(index) == trimChar) {
          exists = true;
          break;
        }
      }

      if (!exists) {
        break;
      }

      index++;
    }

    return str.substring(index, str.length());
  }

  /**
   * 去除字符串头部中指定的字符
   * @param str
   * @param trimChar
   * @return
   */
  public static String trimStart(String str, char trimChar) {
    char[] trimChars = { trimChar };
    return trimStart(str, trimChars);
  }

  /**
   * 去除字符串头部中指定的空白符
   * @param str
   * @return
   */
  public static String trimStart(String str) {
    return trimStart(str, ' ');
  }

  /**
   * 去除字符串尾部中指定的字符列表
   * @param str
   * @param trimChars
   * @return
   */
  public static String trimEnd(String str, char[] trimChars) {
    int index = str.length() - 1;

    while (index > 0) {
      boolean exists = false;

      for (char trimChar : trimChars) {
        if (str.charAt(index) == trimChar) {
          exists = true;
          break;
        }
      }

      if (!exists) {
        break;
      }

      index--;
    }

    return str.substring(0, index + 1);
  }

  /**
   * 去除字符串尾部中指定的字符
   * @param str
   * @param trimChar
   * @return
   */
  public static String trimEnd(String str, char trimChar) {
    char[] trimChars = { trimChar };
    return trimEnd(str, trimChars);
  }

  /**
   * 去除字符串尾部中的空白符
   * @param str
   * @return
   */
  public static String trimEnd(String str) {
    return trimEnd(str, ' ');
  }

  public static boolean isEmpty(String str) {
    return str == null || str.length() == 0;
  }

  public static boolean isNotEmpty(String str) {
    return !isEmpty(str);
  }
}
