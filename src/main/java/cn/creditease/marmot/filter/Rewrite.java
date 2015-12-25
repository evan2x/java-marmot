/**
 * Copyright 2015 creditease Inc. All rights reserved.
 * @desc 重写URL指向文件的过滤器
 * @author aiweizhang(aiweizhang@creditease.cn)
 * @date 2015/05/05
 */

package cn.creditease.marmot.filter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
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

public class Rewrite implements Filter {
    /**
     * 默认的路由配置文件的路径，可配置
     */
    private String routerEntry = "/router/main.xml";
    private HashSet<String> scannedPaths = new HashSet<String>();

    final private String ROUTE_SOURCE_KEY = "uri";
    final private String ROUTE_TARGET_KEY = "target";
    final private String REWRITE_KEY = "X-REWRITE";

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest)request;
        HttpServletResponse resp = (HttpServletResponse)response;
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        // 仅处理未重写的请求
        if( req.getAttribute(REWRITE_KEY) == null ){
            try {
                if(processor(req, resp)){
                    return;
                }
            } catch (XMLStreamException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        chain.doFilter(request, response);
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
     * @param request
     * @param response
     * @return
     * @throws XMLStreamException
     * @throws ServletException
     * @throws IOException
     */
    private Boolean processor(HttpServletRequest request, HttpServletResponse response) throws XMLStreamException, IOException, ServletException, URISyntaxException {
        if(!this.routerEntry.endsWith(".xml")){
            return false;
        }

        ServletContext context = request.getSession().getServletContext();
        String pathname = request.getRequestURI().replaceAll("(/)+\\1", "$1");
        HashMap<String, String> routeResult = routeCrawler(pathname, context, this.routerEntry);
        String src = routeResult.get(ROUTE_SOURCE_KEY);
        String target = routeResult.get(ROUTE_TARGET_KEY);

        if(src != null && !src.equals("") && target != null && !target.equals("")){
            context.setAttribute("mockFile", getMockDataPath(routeResult));
            request.setAttribute(REWRITE_KEY, true);
            request.getRequestDispatcher(target).forward(request, response);
            return true;
        }

        return false;
    }

    /**
     * 路由爬行器
     * @param context
     * @param routerPath
     * @return
     * @throws IOException
     * @throws XMLStreamException
     */
    private HashMap<String, String> routeCrawler(String pathname, ServletContext context,
                                                 String routerPath) throws IOException, XMLStreamException{


        HashMap<String, String> routeResult = new HashMap<>();
        URL routerFile = context.getResource(routerPath);

        if(pathname.equals("/")){
            routeResult.put(ROUTE_SOURCE_KEY, "/");
            routeResult.put(ROUTE_TARGET_KEY, "/index.vm");
            return routeResult;
        }

        if( routerFile == null ){
            return routeResult;
        }

        String scannedPath = routerFile.getPath();
        // 存储已加载的路由文件路径
        scannedPaths.add(scannedPath);

        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLEventReader reader = factory.createXMLEventReader(context.getResourceAsStream(routerPath));
        boolean openRouteMatch = false;

        while(reader.hasNext()){
            XMLEvent event = reader.nextEvent();
            if(event.isStartElement()){
                StartElement start = event.asStartElement();
                QName name = start.getName();

                if(name.toString().equals("import") && scannedPaths.contains(scannedPath)){
                    Attribute src = start.getAttributeByName(new QName("src"));
                    if(src != null && !src.getValue().equals("")){
                        routeResult = routeCrawler(pathname, context, routerPath.replaceAll("[^/]+\\.xml$", "") + src.getValue());
                        if(!routeResult.isEmpty()){
                            return routeResult;
                        }
                    }
                }

                if(name.toString().equals("route-map")){
                    openRouteMatch = true;
                }

                if(openRouteMatch && name.toString().equals("route")){
                    Attribute source = start.getAttributeByName(new QName(ROUTE_SOURCE_KEY));
                    Attribute target = start.getAttributeByName(new QName(ROUTE_TARGET_KEY));
                    if(source != null && target != null){
                        String uri = pathnameNormalize(source.getValue());
                        pathname = pathnameNormalize(pathname);
                        // 路由匹配成功
                        if(pathname.matches(uri)){
                            String result = target.getValue();
                            if(!result.startsWith("/")){
                                result = "/" + result;
                            }

                            routeResult.put(ROUTE_SOURCE_KEY, uri);
                            routeResult.put(ROUTE_TARGET_KEY, result);
                            break;
                        }
                    }
                }
            }

            if(event.isEndElement()){
                EndElement end = event.asEndElement();
                QName name = end.getName();
                if(name.toString().equals("route-map")){
                    openRouteMatch = false;
                }
            }
        }

        reader.close();
        return routeResult;
    }

    /**
     * 提取Mock数据文件路径
     * @param map
     */
    private String getMockDataPath(HashMap<String, String> map){
        String target = map.get(ROUTE_TARGET_KEY);
        int length = target.length();
        if(target.lastIndexOf(".") > 0){
            length = target.lastIndexOf(".");
        }

        return target.substring(0, length);
    }

    /**
     * 规范化路径，用于匹配路由时的前置处理
     * @param path
     * @return
     */
    private String pathnameNormalize(String path){
        if(path.startsWith("/")){
            path = path.substring(1, path.length());
        }

        if(path.endsWith("/")){
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }


}
