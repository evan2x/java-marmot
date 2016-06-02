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
    Route route;
    ServletContext context = request.getSession().getServletContext();
    String uri = util.uniqueBySerialSlash(request.getRequestURI());

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

        if (name.equals(RouterConstants.ROUTER_TAG)) {
          Attribute defaultProviderAttr = start.getAttributeByName(new QName(RouterConstants.PROVIDER_ATTRIBUTE));
          if (defaultProviderAttr != null && !defaultProviderAttr.getValue().isEmpty()) {
            defaultProvider = defaultProviderAttr.getValue();
          }
          openRouterTag = true;
        }

        // 开启cookies匹配
        if (name.equals(RouterConstants.COOKIES_TAG) && openRouterTag) {
          openCookiesTag = true;
        }

        // 转化cookie为bean
        if (name.equals(RouterConstants.COOKIE_TAG) && openCookiesTag) {
          cookies.add(convertCookie(start));
        }

        // import 标签匹配
        if (name.equals(RouterConstants.IMPORT_TAG) && openRouterTag && scannedFilePaths.contains(scannedFilePath)) {
          Attribute srcAttr = start.getAttributeByName(new QName(RouterConstants.SRC_ATTRIBUTE));
          if (srcAttr != null && !srcAttr.getValue().isEmpty()) {
            String importFilePath = routerFilePath.replaceAll("[^/]+\\.xml$", "") + srcAttr.getValue();
            router = parse(request, importFilePath, scannedFilePaths, openRouterTag);
            if (router.isMatched()) {
              return router;
            }
          }
        }

        // routes 标签匹配
        if ((name.equals(RouterConstants.ROUTES_TAG) || name.equals(RouterConstants.ROUTE_MAP_TAG)) && openRouterTag) {
          Attribute providerAttr = start.getAttributeByName(new QName(RouterConstants.PROVIDER_ATTRIBUTE));
          if (providerAttr != null && !providerAttr.getValue().isEmpty()) {
            defaultProvider = providerAttr.getValue();
          }

          Attribute contentTypeAttr = start.getAttributeByName(new QName(RouterConstants.CONTENT_TYPE_ATTRIBUTE));
          if (contentTypeAttr != null && !contentTypeAttr.getValue().isEmpty()) {
            defaultContentType = contentTypeAttr.getValue();
          }
          openRoutesTag = true;
        }

        // 转化route为bean
        if (name.equals(RouterConstants.ROUTE_TAG) && openRoutesTag) {
          route = convertRoute(uri, defaultProvider, defaultContentType, start);
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
  private static Cookie convertCookie (StartElement element) {
    Cookie cookie = null;

    Attribute nameAttr = element.getAttributeByName(new QName(RouterConstants.COOKIE_NAME_ATTRIBUTE));
    Attribute valueAttr = element.getAttributeByName(new QName(RouterConstants.COOKIE_VALUE_ATTRIBUTE));

    if (nameAttr != null && valueAttr != null && !nameAttr.getValue().isEmpty() && !valueAttr.getValue().isEmpty()) {
      cookie = new Cookie(nameAttr.getValue(), valueAttr.getValue());

      Attribute pathAttr = element.getAttributeByName(new QName(RouterConstants.COOKIE_PATH_ATTRIBUTE));
      if (pathAttr != null && !pathAttr.getValue().isEmpty()) {
        cookie.setPath(pathAttr.getValue());
      }

      Attribute domainAttr = element.getAttributeByName(new QName(RouterConstants.COOKIE_DOMAIN_ATTRIBUTE));
      if (domainAttr != null && !domainAttr.getValue().isEmpty()) {
        cookie.setDomain(domainAttr.getValue());
      }

      Attribute maxAgeAttr = element.getAttributeByName(new QName(RouterConstants.COOKIE_MAX_AGE_ATTRIBUTE));
      if (maxAgeAttr != null && !maxAgeAttr.getValue().isEmpty()) {
        cookie.setMaxAge(Integer.parseInt(maxAgeAttr.getValue()));
      }

      Attribute secureAttr = element.getAttributeByName(new QName(RouterConstants.COOKIE_SECURE_ATTRIBUTE));
      if (secureAttr != null && !secureAttr.getValue().isEmpty()) {
        cookie.setSecure(secureAttr.getValue().equals("true"));
      }

      Attribute httpOnlyAttr = element.getAttributeByName(new QName(RouterConstants.COOKIE_HTTP_ONLY_ATTRIBUTE));
      if (httpOnlyAttr != null && !httpOnlyAttr.getValue().isEmpty()) {
        cookie.setHttpOnly(httpOnlyAttr.getValue().equals("true"));
      }
    }

    return cookie;
  }

  /**
   * 将当前匹配到的结果转化为RouteBean
   * @param uri 当前请求的url路径
   * @param defaultProvider 路由父级标签的provider
   * @param element 匹配到的路由节点
   * @return RouteBean
   */
  private static Route convertRoute (String uri, String defaultProvider, String defaultContentType, StartElement element) {
    Route route = new Route();

    Attribute ruleAttr = element.getAttributeByName(new QName(RouterConstants.RULE_ATTRIBUTE));
    Attribute locationAttr = element.getAttributeByName(new QName(RouterConstants.LOCATION_ATTRIBUTE));
    Attribute providerAttr = element.getAttributeByName(new QName(RouterConstants.PROVIDER_ATTRIBUTE));
    Attribute redirectAttr = element.getAttributeByName(new QName(RouterConstants.REDIRECT_ATTRIBUTE));
    Attribute contentTypeAttr = element.getAttributeByName(new QName(RouterConstants.CONTENT_TYPE_ATTRIBUTE));

    /** @deprecated */
    Attribute uriAttr = element.getAttributeByName(new QName(RouterConstants.URI_ATTRIBUTE));
    /** @deprecated */
    Attribute targetAttr = element.getAttributeByName(new QName(RouterConstants.TARGET_ATTRIBUTE));

    if (ruleAttr != null || uriAttr != null) {

      String rule = "";
      if (ruleAttr != null && !ruleAttr.getValue().isEmpty()) {
        rule = util.trimSlash(ruleAttr.getValue());
      } else if (uriAttr != null && !uriAttr.getValue().isEmpty()) {
        rule = util.trimSlash(uriAttr.getValue());
      }

      uri = util.trimSlash(uri);
      // 路由匹配成功
      if (uri.matches(rule)) {
        route.setPathName(uri);
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
}
