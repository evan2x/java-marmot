package com.evan2x.marmot;

import com.evan2x.marmot.object.Route;
import com.evan2x.marmot.object.Router;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RouterParser {

  /**
   * 解析路由
   * @param request 请求对象
   * @param routerFilePath 路由入口文件路径
   * @return 返回一个RouteBean, 包含了路由匹配结果
   * @throws MalformedURLException
   * @throws XMLStreamException
   */
  public static Router parse(HttpServletRequest request, String routerFilePath, Set<String> scannedFilePaths, boolean openRouterTag)
          throws MalformedURLException, XMLStreamException {
    Router router = new Router();
    List<Cookie> cookies = new ArrayList<>();
    ServletContext context = request.getSession().getServletContext();

    URL routerUrl = context.getResource(routerFilePath);
    if (routerUrl == null) {
      return router;
    }

    String scannedFilePath = routerUrl.getPath();
    scannedFilePaths.add(scannedFilePath);

    boolean openRoutesTag = false;
    boolean openCookiesTag = false;
    String extendsProxy = "";
    String extendsContentType = "";

    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventReader reader = factory.createXMLEventReader(context.getResourceAsStream(routerFilePath));

    while (reader.hasNext()) {
      XMLEvent event = reader.nextEvent();

      if (event.isStartElement()) {
        StartElement start = event.asStartElement();
        String name = start.getName().toString();

        if (name.equals(RouterConstants.ROUTER_TAG)) {
          extendsProxy = getProxy(start);
          openRouterTag = true;
        }

        // 开启cookies匹配
        if (openRouterTag && name.equals(RouterConstants.COOKIES_TAG)) {
          openCookiesTag = true;
        }

        // 转化cookie为bean
        if (openCookiesTag && name.equals(RouterConstants.COOKIE_TAG)) {
          cookies.add(convertCookie(start));
        }

        // import 标签匹配
        if (openRouterTag && name.equals(RouterConstants.IMPORT_TAG) && scannedFilePaths.contains(scannedFilePath)) {
          String src = getAttribute(start, RouterConstants.SRC_ATTRIBUTE);

          if (util.isNotEmpty(src)) {
            String importFilePath = routerFilePath.replaceAll("[^/]+\\.xml$", "") + src;
            router = parse(request, importFilePath, scannedFilePaths, openRouterTag);
            if (router.isMatched()) {
              return router;
            }
          }
        }

        // routes 标签匹配
        if (openRouterTag && (name.equals(RouterConstants.ROUTES_TAG) || name.equals(RouterConstants.ROUTE_MAP_TAG))) {
          extendsProxy = getProxy(start);
          extendsContentType = getAttribute(start, RouterConstants.CONTENT_TYPE_ATTRIBUTE);
          openRoutesTag = true;
        }

        // 转化route为bean
        if (openRoutesTag && name.equals(RouterConstants.ROUTE_TAG)) {
          String uri = util.uniqueSerialSlash(request.getRequestURI());
          Route route = convertRoute(uri, extendsProxy, extendsContentType, start);
          // 路由匹配成功跳出循环
          if (util.isNotEmpty(route.getPathName())) {
            router.setRoute(route);
            router.setMatched(true);
            break;
          }
        }
      }

      if(event.isEndElement()){
        EndElement end = event.asEndElement();
        String name = end.getName().toString();

        // 关闭cookie匹配
        if (name.equals(RouterConstants.COOKIES_TAG)) {
          openCookiesTag = false;
          router.setCookies(cookies);
        }

        // 关闭路由匹配
        if (name.equals(RouterConstants.ROUTES_TAG) || name.equals(RouterConstants.ROUTE_MAP_TAG)) {
          openRoutesTag = false;
        }

        // 结束整个路由的匹配
        if (name.equals(RouterConstants.ROUTER_TAG)) {
          break;
        }
      }
    }

    reader.close();
    return router;
  }

  /**
   * 将当前匹配到的结果转化为Cookie
   * @param element 匹配到的路由节点
   * @return Cookie
   */
  private static Cookie convertCookie(StartElement element) {
    String name = getAttribute(element, RouterConstants.COOKIE_NAME_ATTRIBUTE);
    String value = getAttribute(element, RouterConstants.COOKIE_VALUE_ATTRIBUTE);

    if (util.isNotEmpty(name) && util.isNotEmpty(value)) {
      Cookie cookie = new Cookie(name, value);

      String path = getAttribute(element, RouterConstants.COOKIE_PATH_ATTRIBUTE);
      if (util.isNotEmpty(path)) {
        cookie.setPath(path);
      }

      String domain = getAttribute(element, RouterConstants.COOKIE_DOMAIN_ATTRIBUTE);
      if (util.isNotEmpty(domain)) {
        cookie.setDomain(domain);
      }

      String maxAge = getAttribute(element, RouterConstants.COOKIE_MAX_AGE_ATTRIBUTE);
      if (util.isNotEmpty(maxAge)) {
        cookie.setMaxAge(Integer.parseInt(maxAge));
      }

      String secure = getAttribute(element, RouterConstants.COOKIE_SECURE_ATTRIBUTE);
      if (util.isNotEmpty(secure)) {
        cookie.setSecure(secure.equals("true"));
      }

      String httpOnly = getAttribute(element, RouterConstants.COOKIE_HTTP_ONLY_ATTRIBUTE);
      if (util.isNotEmpty(httpOnly)) {
        cookie.setHttpOnly(httpOnly.equals("true"));
      }

      return cookie;
    }

    return null;
  }

  /**
   * 将当前匹配到的结果转化为Route对象
   * @param uri 当前请求的url路径
   * @param defaultProxy 默认的proxy
   * @param defaultContentType 默认的content-type
   * @param element 匹配到的路由节点
   * @return RouteBean
   */
  private static Route convertRoute(String uri, String defaultProxy, String defaultContentType, StartElement element) {
    Route route = new Route();

    String rule = getRule(element);

    if (util.isNotEmpty(rule)) {
      Pattern pattern = Pattern.compile(rule);
      Matcher matcher = pattern.matcher(uri);

      // 匹配成功
      if (matcher.find()) {
        route.setPathRule(rule);
        route.setPathName(uri);

        String proxy = getProxy(element);
        if (util.isEmpty(proxy)) {
          proxy = defaultProxy;
        }

        if (util.isNotEmpty(proxy)) {
          route.setProxy(proxy);
        }

        String contentType = getAttribute(element, RouterConstants.CONTENT_TYPE_ATTRIBUTE);
        if (util.isEmpty(contentType)) {
          contentType = defaultContentType;
        }

        if (util.isNotEmpty(contentType)) {
          route.setContentType(contentType);
        }

        String location = getLocation(element);
        if (util.isNotEmpty(location)) {
          route.setLocation(location);
        }

        String redirect = getAttribute(element, RouterConstants.REDIRECT_ATTRIBUTE);
        if (util.isNotEmpty(redirect)) {
          route.setRedirect(redirect);
        }
      }
    }

    return route;
  }

  /**
   * 获取代理节点元素的属性值
   * @param element 节点元素
   * @return
   */
  private static String getProxy(StartElement element) {
    String proxy = getAttribute(element, RouterConstants.PROXY_ATTRIBUTE);

    if (util.isEmpty(proxy)) {
      proxy = getAttribute(element, RouterConstants.PROVIDER_ATTRIBUTE);
    }

    return proxy;
  }

  /**
   * 获取路由的匹配规则
   * @param element 节点元素
   * @return
   */
  private static String getRule(StartElement element) {
    String rule = getAttribute(element, RouterConstants.RULE_ATTRIBUTE);

    if (util.isEmpty(rule)) {
      rule = getAttribute(element, RouterConstants.URI_ATTRIBUTE);
    }

    if (util.isNotEmpty(rule)) {
      rule =  util.trimEnd(rule, '/');

      if (!rule.startsWith("^")) {
        rule = "^" + rule;
      }

      if (!rule.endsWith("$")) {
        rule = rule + "$";
      }
    }

    return rule;
  }

  /**
   * 获取路由映射的资源地址
   * @param element
   * @return
   */
  private static String getLocation(StartElement element) {
    String location = getAttribute(element, RouterConstants.LOCATION_ATTRIBUTE);

    if (util.isEmpty(location)) {
      location = getAttribute(element, RouterConstants.TARGET_ATTRIBUTE);
    }

    return location;
  }

  /**
   * 获取指定节点元素的属性值
   * @param element 节点元素
   * @param attrName 属性名
   * @return
   */
  private static String getAttribute(StartElement element, String attrName) {
    String value = null;
    Attribute attr = element.getAttributeByName(new QName(attrName));

    if (attr != null) {
      value = attr.getValue();
    }

    return value;
  }
}
