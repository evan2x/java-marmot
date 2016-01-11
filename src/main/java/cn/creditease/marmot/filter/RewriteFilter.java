/**
 * Copyright 2015 creditease Inc. All rights reserved.
 * @desc 重写URL
 * @author aiweizhang(aiweizhang@creditease.cn)
 * @date 2015/05/05
 */

package cn.creditease.marmot.filter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
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

import cn.creditease.marmot.bean.ResponseBean;
import cn.creditease.marmot.bean.RouteBean;
import cn.creditease.marmot.Utils;

public class RewriteFilter implements Filter {

    private String routerEntry = "/router/main.xml";

    private static final String SRC_ATTRIBUTE = "src";
    private static final String RULE_ATTRIBUTE = "rule";
    private static final String LOCATION_ATTRIBUTE = "location";
    private static final String PROVIDER_ATTRIBUTE = "provider";
    private static final String RENDER_TEMPLATE_ATTRIBUTE = "render-template";
    // deprecated
    private static final String URI_ATTRIBUTE = "uri";
    // deprecated
    private static final String TARGET_ATTRIBUTE = "target";

    private static final String IMPORT_TAG = "import";
    private static final String ROUTE_MAP_TAG = "route-map";
    private static final String ROUTE_TAG = "route";

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest)request;
        HttpServletResponse resp = (HttpServletResponse)response;
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");

        try {
            if(!processor(req, resp)){
                chain.doFilter(request, response);
            }
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        String routerFilePath = config.getInitParameter("routerFile");
        if(routerFilePath != null && !routerFilePath.equals("")){
            if(!routerFilePath.startsWith("/")){
                routerFilePath = "/" + routerFilePath;
            }
            this.routerEntry = routerFilePath;
        }
    }

    /**
     * 路由的处理器
     * @param request servlet request
     * @param response servlet response
     * @return 处理结果, true为已处理, false未处理
     * @throws XMLStreamException
     * @throws ServletException
     * @throws IOException
     */
    private Boolean processor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException, XMLStreamException {
        if(!this.routerEntry.endsWith(".xml")){
            return false;
        }

        ServletContext context = request.getSession().getServletContext();
        String uri = request.getRequestURI().replaceAll("(/)+\\1", "$1");
        HashSet<String> scannedPaths = new HashSet<>();
        RouteBean route = routeCrawler(uri, context, this.routerEntry, scannedPaths);

        String pathName = route.getPathName();
        String provider = route.getProvider();
        String location = route.getLocation();
        String renderTemplate = route.getRenderTemplate();

        // 有效的路由
        if(pathName != null && !pathName.equals("")){
            // 当provider属性存在时, 优先使用provider中的地址发起请求
            if(provider != null && !provider.equals("")){
                ResponseBean result = Utils.providerResource(provider, request, response);
                // 当使用了本地渲染模板时, 模拟数据转交给mock filter处理
                if(renderTemplate != null && !renderTemplate.equals("")){
                    context.setAttribute("location", location);
                    context.setAttribute("data", result.getData());
                    context.setAttribute("url", result.getUrl());
                    request.getRequestDispatcher(renderTemplate).forward(request, response);
                    return true;
                } else {
                    String data = result.getData();

                    if(data == null){
                        return false;
                    }

                    response.getWriter().write(data);
                    return true;
                }
            // 将请求直接转发到指定的location
            } else if(location != null && !location.equals("")) {
                context.setAttribute("location", location);
                request.getRequestDispatcher(location).forward(request, response);
                return true;
            }
        }

        return false;
    }

    /**
     * 路由爬行器
     * @param uri 请求的uri路径
     * @param context servlet的上下文
     * @param routerPath 路由入口文件路径
     * @return 返回一个RouteBean, 包含了路由匹配结果
     * @throws MalformedURLException
     * @throws XMLStreamException
     */
    private RouteBean routeCrawler(String uri, ServletContext context, String routerPath, HashSet<String> scannedPaths) throws MalformedURLException, XMLStreamException {
        RouteBean route = new RouteBean();
        if(uri.equals("/")){
            route.setPathName("/");
            route.setLocation("/index.vm");
            return route;
        }

        URL routerFile = context.getResource(routerPath);

        if( routerFile == null ){
            return route;
        }

        String scannedPath = routerFile.getPath();
        // 存储已扫描过的路由文件路径
        scannedPaths.add(scannedPath);

        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLEventReader reader = factory.createXMLEventReader(context.getResourceAsStream(routerPath));
        boolean openRouteMatch = false;
        String parentProvider = "";

        while(reader.hasNext()){
            XMLEvent event = reader.nextEvent();
            if(event.isStartElement()){
                StartElement start = event.asStartElement();
                QName name = start.getName();

                if(name.toString().equals(IMPORT_TAG) && scannedPaths.contains(scannedPath)){
                    Attribute src = start.getAttributeByName(new QName(SRC_ATTRIBUTE));
                    if(src != null && !src.getValue().equals("")){
                        String importRouterPath = routerPath.replaceAll("[^/]+\\.xml$", "") + src.getValue();
                        route = routeCrawler(uri, context, importRouterPath, scannedPaths);
                        if(route.getPathName() != null){
                            return route;
                        }
                    }
                }

                if(name.toString().equals(ROUTE_MAP_TAG)){
                    // 记录route-map的provider属性
                    Attribute provider = start.getAttributeByName(new QName(PROVIDER_ATTRIBUTE));
                    if(provider != null){
                        parentProvider = provider.getValue();
                    } else {
                        parentProvider = "";
                    }
                    // 开启路由匹配
                    openRouteMatch = true;
                }

                if(openRouteMatch && name.toString().equals(ROUTE_TAG)){
                    route = parseRoute(start, uri, parentProvider);
                    if(route.getPathName() != null){
                        break;
                    }
                }
            }

            if(event.isEndElement()){
                EndElement end = event.asEndElement();
                QName name = end.getName();
                if(name.toString().equals(ROUTE_MAP_TAG)){
                    // 关闭路由匹配
                    openRouteMatch = false;
                }
            }
        }

        reader.close();
        return route;
    }

    /**
     * 解析单条路由规则
     * @param node 匹配到的路由节点
     * @param uri 当前请求的url路径
     * @param parentProvider 路由父级标签的provider
     * @return 返回一个RouteBean, 包含了当前单条路由规则的匹配结果
     */
    private RouteBean parseRoute(StartElement node, String uri, String parentProvider){
        RouteBean route = new RouteBean();

        Attribute ruleAttr = node.getAttributeByName(new QName(RULE_ATTRIBUTE));
        Attribute locationAttr = node.getAttributeByName(new QName(LOCATION_ATTRIBUTE));
        Attribute providerAttr = node.getAttributeByName(new QName(PROVIDER_ATTRIBUTE));
        Attribute renderTemplateAttr = node.getAttributeByName(new QName(RENDER_TEMPLATE_ATTRIBUTE));
        // deprecated
        Attribute oldRuleAttr = node.getAttributeByName(new QName(URI_ATTRIBUTE));
        // deprecated
        Attribute oldLocationAttr = node.getAttributeByName(new QName(TARGET_ATTRIBUTE));

        if(ruleAttr != null || oldRuleAttr != null){

            String rule = "";
            if(ruleAttr != null && !ruleAttr.getValue().equals("")){
                rule = Utils.slashTrim(ruleAttr.getValue());
            } else if(oldRuleAttr != null && !oldRuleAttr.getValue().equals("")) {
                rule = Utils.slashTrim(oldRuleAttr.getValue());
            }

            uri = Utils.slashTrim(uri);
            // 路由匹配成功
            if(uri.matches(rule)){

                route.setPathName(uri);
                route.setPathRule(rule);

                // 优先使用自身的provider属性,不存在的话使用route-map的provider
                if(providerAttr != null && !providerAttr.getValue().equals("")){
                    route.setProvider(providerAttr.getValue());
                } else {
                    route.setProvider(parentProvider);
                }

                if(locationAttr != null && !locationAttr.getValue().equals("")){
                    route.setLocation(locationAttr.getValue());
                } else if(oldLocationAttr != null && !oldLocationAttr.getValue().equals("")) {
                    route.setLocation(oldLocationAttr.getValue());
                }

                if (renderTemplateAttr != null && !renderTemplateAttr.getValue().equals("")) {
                    route.setRenderTemplate(renderTemplateAttr.getValue());
                }
            }
        }

        return route;
    }
}
