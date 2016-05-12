
package cn.creditease.marmot.filter;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import cn.creditease.marmot.bean.RemoteDataBean;
import cn.creditease.marmot.bean.RouteBean;
import cn.creditease.marmot.bean.RouterBean;
import cn.creditease.marmot.util;

/**
 * 根据路由匹配重新定向请求地址
 */

public class RewriteFilter implements Filter {

  private String routerEntry = "/router/main.xml";

  private static final String SRC_ATTRIBUTE = "src";
  private static final String RULE_ATTRIBUTE = "rule";
  private static final String LOCATION_ATTRIBUTE = "location";
  private static final String PROVIDER_ATTRIBUTE = "provider";
  private static final String CONTENT_TYPE_ATTRIBUTE = "content-type";
  private static final String REDIRECT_ATTRIBUTE = "redirect";

  /** @deprecated */
  private static final String URI_ATTRIBUTE = "uri";
  /** @deprecated */
  private static final String TARGET_ATTRIBUTE = "target";

  private static final String ROUTER_TAG = "router";
  private static final String IMPORT_TAG = "import";
  private static final String ROUTES_TAG = "routes";
  private static final String ROUTE_TAG = "route";

  /** @deprecated */
  private static final String ROUTE_MAP_TAG = "route-map";

  private static final String COOKIES_TAG = "cookies";
  private static final String COOKIE_TAG = "cookie";
  private static final String COOKIE_NAME_ATTRIBUTE = "name";
  private static final String COOKIE_VALUE_ATTRIBUTE = "value";
  private static final String COOKIE_DOMAIN_ATTRIBUTE = "domain";
  private static final String COOKIE_PATH_ATTRIBUTE = "path";
  private static final String COOKIE_MAX_AGE_ATTRIBUTE = "max-age";
  private static final String COOKIE_SECURE_ATTRIBUTE = "secure";
  private static final String COOKIE_HTTP_ONLY_ATTRIBUTE = "http-only";

  @Override
  public void init(FilterConfig config) throws ServletException {
    String routerFilePath = config.getInitParameter("routerFile");
    if (routerFilePath != null && !routerFilePath.isEmpty()) {
      if (!routerFilePath.startsWith("/")) {
        routerFilePath = "/" + routerFilePath;
      }
      this.routerEntry = routerFilePath;
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
          throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest)request;
    HttpServletResponse resp = (HttpServletResponse)response;
    req.setCharacterEncoding("UTF-8");
    resp.setCharacterEncoding("UTF-8");

    try {
      if (!processor(req, resp)) {
        chain.doFilter(request, response);
      }
    } catch (XMLStreamException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void destroy() {}

  /**
   * 路由的处理器
   * @param request servlet request
   * @param response servlet response
   * @return 处理结果, true为已处理, false未处理
   * @throws XMLStreamException
   * @throws ServletException
   * @throws IOException
   */
  private boolean processor(HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException, XMLStreamException {
    if (!this.routerEntry.endsWith(".xml")) {
      return false;
    }

    ServletContext context = request.getSession().getServletContext();
    String uri = util.uniqueBySerialSlash(request.getRequestURI());
    Set<String> scannedPaths = new HashSet<>();
    RouterBean router = routerParser(uri, context, this.routerEntry, scannedPaths, false);
    RouteBean route = router.getRoute();

    String provider = route.getProvider();
    String location = route.getLocation();
    String redirect = route.getRedirect();
    String contentType = route.getContentType();

    // 路由匹配成功
    if (router.isMatched()) {
      List<Cookie> cookies = router.getCookies();

      // 请求重定向
      if(redirect != null && !redirect.isEmpty()) {
        response.sendRedirect(redirect);
        return true;
      }

      if (contentType != null && !contentType.isEmpty()) {
        response.setContentType(contentType);
      }

      if (cookies != null) {
        for (Cookie cookie : cookies) {
          response.addCookie(cookie);
        }
      }

      // provider属性存在时, 该路由会被转发到provider中指定的地址
      if (provider != null && !provider.isEmpty()) {
        RemoteDataBean remoteData = util.requestRemoteData(provider, cookies, request, response);
        List<String> contentTypes = new ArrayList<String>(){{
          add("text/html");
          add("text/htm");
        }};

        // 只有指定了路由的content-type为text/html或text/htm, 才会用远程数据渲染本地模板
        if (contentType != null && !contentType.isEmpty() && contentTypes.contains(contentType.toLowerCase())) {
          context.setAttribute("location", location);
          context.setAttribute("contentType", contentType);
          context.setAttribute("url", remoteData.getUrl());
          context.setAttribute("data", remoteData.getData());
          request.getRequestDispatcher(location).forward(request, response);
          return true;

          // 其他情况一律直接返回远程数据
        } else {
          String data = remoteData.getData();

          if (data != null) {
            PrintWriter writer = response.getWriter();
            writer.write(data);
            writer.flush();
            writer.close();
          }

          return true;
        }

        // 将请求直接转发到指定的location
      } else if (location != null && !location.isEmpty()) {
        context.setAttribute("location", location);
        if (contentType != null && !contentType.isEmpty()) {
          request.getRequestDispatcher(location).include(request, response);
        } else {
          request.getRequestDispatcher(location).forward(request, response);
        }

        return true;
      }
    }

    return false;
  }

  /**
   * 将当前匹配到的结果转化为Cookie
   * @param element 匹配到的路由节点
   * @return Cookie
   */
  private Cookie convertCookie (StartElement element) {
    Cookie cookie = null;

    Attribute nameAttr = element.getAttributeByName(new QName(COOKIE_NAME_ATTRIBUTE));
    Attribute valueAttr = element.getAttributeByName(new QName(COOKIE_VALUE_ATTRIBUTE));

    if (nameAttr != null && valueAttr != null && !nameAttr.getValue().isEmpty() && !valueAttr.getValue().isEmpty()) {
      cookie = new Cookie(nameAttr.getValue(), valueAttr.getValue());

      Attribute pathAttr = element.getAttributeByName(new QName(COOKIE_PATH_ATTRIBUTE));
      if (pathAttr != null && !pathAttr.getValue().isEmpty()) {
        cookie.setPath(pathAttr.getValue());
      }

      Attribute domainAttr = element.getAttributeByName(new QName(COOKIE_DOMAIN_ATTRIBUTE));
      if (domainAttr != null && !domainAttr.getValue().isEmpty()) {
        cookie.setDomain(domainAttr.getValue());
      }

      Attribute maxAgeAttr = element.getAttributeByName(new QName(COOKIE_MAX_AGE_ATTRIBUTE));
      if (maxAgeAttr != null && !maxAgeAttr.getValue().isEmpty()) {
        cookie.setMaxAge(Integer.parseInt(maxAgeAttr.getValue()));
      }

      Attribute secureAttr = element.getAttributeByName(new QName(COOKIE_SECURE_ATTRIBUTE));
      if (secureAttr != null && !secureAttr.getValue().isEmpty()) {
        cookie.setSecure(secureAttr.getValue().equals("true"));
      }

      Attribute httpOnlyAttr = element.getAttributeByName(new QName(COOKIE_HTTP_ONLY_ATTRIBUTE));
      if (httpOnlyAttr != null && !httpOnlyAttr.getValue().isEmpty()) {
        cookie.setHttpOnly(httpOnlyAttr.getValue().equals("true"));
      }
    }

    return cookie;
  }

  /**
   * 将当前匹配到的结果转化为RouteBean
   * @param pathname 当前请求的url路径
   * @param defaultProvider 路由父级标签的provider
   * @param element 匹配到的路由节点
   * @return RouteBean
   */
  private RouteBean convertRouteBean (String pathname, String defaultProvider, String defaultContentType, StartElement element) {
    RouteBean route = new RouteBean();

    Attribute ruleAttr = element.getAttributeByName(new QName(RULE_ATTRIBUTE));
    Attribute locationAttr = element.getAttributeByName(new QName(LOCATION_ATTRIBUTE));
    Attribute providerAttr = element.getAttributeByName(new QName(PROVIDER_ATTRIBUTE));
    Attribute redirectAttr = element.getAttributeByName(new QName(REDIRECT_ATTRIBUTE));
    Attribute contentTypeAttr = element.getAttributeByName(new QName(CONTENT_TYPE_ATTRIBUTE));

    /** @deprecated */
    Attribute uriAttr = element.getAttributeByName(new QName(URI_ATTRIBUTE));
    /** @deprecated */
    Attribute targetAttr = element.getAttributeByName(new QName(TARGET_ATTRIBUTE));

    if (ruleAttr != null || uriAttr != null) {

      String rule = "";
      if (ruleAttr != null && !ruleAttr.getValue().isEmpty()) {
        rule = util.trimSlash(ruleAttr.getValue());
      } else if (uriAttr != null && !uriAttr.getValue().isEmpty()) {
        rule = util.trimSlash(uriAttr.getValue());
      }

      pathname = util.trimSlash(pathname);
      // 路由匹配成功
      if (pathname.matches(rule)) {
        route.setPathName(pathname);
        route.setPathRule(rule);

        // 优先使用自身的provider属性,不存在的话使用默认的provider(由routes或router标签上继承而来)
        if (providerAttr != null && !providerAttr.getValue().isEmpty()) {
          route.setProvider(providerAttr.getValue());
        } else {
          route.setProvider(defaultProvider);
        }

        // 优先使用自身的content-type属性,不存在的话使用默认的content-type(由routes标签上继承而来)
        if (contentTypeAttr != null && !contentTypeAttr.getValue().isEmpty()) {
          route.setContentType(util.extractContentType(contentTypeAttr.getValue()));
        } else {
          route.setContentType(defaultContentType);
        }

        if (locationAttr != null && !locationAttr.getValue().isEmpty()) {
          route.setLocation(locationAttr.getValue());
        } else if(targetAttr != null && !targetAttr.getValue().isEmpty()) {
          route.setLocation(targetAttr.getValue());
        }

        if (redirectAttr !=null && !redirectAttr.getValue().isEmpty()) {
          route.setRedirect(redirectAttr.getValue());
        }
      }
    }

    return route;
  }

  /**
   * 路由解析器
   * @param pathname 请求的uri路径
   * @param context servlet的上下文
   * @param routerFilePath 路由入口文件路径
   * @return 返回一个RouteBean, 包含了路由匹配结果
   * @throws MalformedURLException
   * @throws XMLStreamException
   */
  private RouterBean routerParser(String pathname, ServletContext context, String routerFilePath, Set<String> scannedFilePaths, boolean openRouterTag)
          throws MalformedURLException, XMLStreamException {

    RouterBean router = new RouterBean();
    List<Cookie> cookies = new ArrayList<>();
    RouteBean route;

    URL routerUrl = context.getResource(routerFilePath);
    if (routerUrl == null) {
      return router;
    }

    String scannedFilePath = routerUrl.getPath();
    scannedFilePaths.add(scannedFilePath);

    boolean openRoutesTag = false;
    boolean openCookiesTag = false;
    String defaultProvider = "";
    String defaultContentType = "";

    XMLInputFactory factory = XMLInputFactory.newInstance();
    XMLEventReader reader = factory.createXMLEventReader(context.getResourceAsStream(routerFilePath));

    while (reader.hasNext()) {
      XMLEvent event = reader.nextEvent();

      if (event.isStartElement()) {
        StartElement start = event.asStartElement();
        String name = start.getName().toString();

        if (name.equals(ROUTER_TAG)) {
          Attribute defaultProviderAttr = start.getAttributeByName(new QName(PROVIDER_ATTRIBUTE));
          if (defaultProviderAttr != null && !defaultProviderAttr.getValue().isEmpty()) {
            defaultProvider = defaultProviderAttr.getValue();
          }
          openRouterTag = true;
        }

        // 开启cookies匹配
        if (name.equals(COOKIES_TAG) && openRouterTag) {
          openCookiesTag = true;
        }

        // 转化cookie为bean
        if (name.equals(COOKIE_TAG) && openCookiesTag) {
          cookies.add(convertCookie(start));
        }

        // import 标签匹配
        if (name.equals(IMPORT_TAG) && openRouterTag && scannedFilePaths.contains(scannedFilePath)) {
          Attribute srcAttr = start.getAttributeByName(new QName(SRC_ATTRIBUTE));
          if (srcAttr != null && !srcAttr.getValue().isEmpty()) {
            String importFilePath = routerFilePath.replaceAll("[^/]+\\.xml$", "") + srcAttr.getValue();
            router = routerParser(pathname, context, importFilePath, scannedFilePaths, openRouterTag);
            if (router.isMatched()) {
              return router;
            }
          }
        }

        // routes 标签匹配
        if ((name.equals(ROUTES_TAG) || name.equals(ROUTE_MAP_TAG)) && openRouterTag) {
          Attribute providerAttr = start.getAttributeByName(new QName(PROVIDER_ATTRIBUTE));
          if (providerAttr != null && !providerAttr.getValue().isEmpty()) {
            defaultProvider = providerAttr.getValue();
          }

          Attribute contentTypeAttr = start.getAttributeByName(new QName(CONTENT_TYPE_ATTRIBUTE));
          if (contentTypeAttr != null && !contentTypeAttr.getValue().isEmpty()) {
            defaultContentType = contentTypeAttr.getValue();
          }
          openRoutesTag = true;
        }

        // 转化route为bean
        if (name.equals(ROUTE_TAG) && openRoutesTag) {
          route = convertRouteBean(pathname, defaultProvider, defaultContentType, start);
          // 路由匹配成功跳出循环
          if (route.getPathName() != null) {
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
        if (name.equals(COOKIES_TAG)) {
          openCookiesTag = false;
          router.setCookies(cookies);
        }

        // 关闭路由匹配
        if (name.equals(ROUTES_TAG) || name.equals(ROUTE_MAP_TAG)) {
          openRoutesTag = false;
        }

        // 结束整个路由的匹配
        if (name.equals(ROUTER_TAG)) {
          break;
        }
      }
    }

    reader.close();
    return router;
  }
}
