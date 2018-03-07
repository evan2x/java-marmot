
package com.evan2x.marmot.filter;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLStreamException;

import com.evan2x.marmot.RouterParser;
import com.evan2x.marmot.object.RemoteData;
import com.evan2x.marmot.object.Route;
import com.evan2x.marmot.object.Router;
import com.evan2x.marmot.util;

public class RewriteFilter implements Filter {

  private String routerEntry = "/router/main.xml";

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

    Set<String> scannedPaths = new HashSet<>();
    Router router = RouterParser.parse(request, this.routerEntry, scannedPaths, false);
    Route route = router.getRoute();

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
        RemoteData remoteData = util.requestRemoteData(provider, cookies, request, response);
        List<String> contentTypes = new ArrayList<String>(){{
          add("text/html");
          add("text/htm");
        }};

        // 只有指定了路由的content-type为text/html或text/htm, 才会用远程数据渲染本地模板
        if (contentType != null && !contentType.isEmpty() && contentTypes.contains(contentType.toLowerCase())) {
          request.setAttribute("location", location);
          request.setAttribute("url", remoteData.getUrl());
          request.setAttribute("data", remoteData.getData());
          request.getRequestDispatcher(location).forward(request, response);
          return true;

          // 其他情况一律直接返回远程数据
        } else {
          byte[] data = remoteData.getData();
          OutputStream out = response.getOutputStream();

          out.write(data);
          out.flush();
          out.close();

          return true;
        }

        // 将请求直接转发到指定的location
      } else if (location != null && !location.isEmpty()) {
        request.setAttribute("location", location);
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
}
