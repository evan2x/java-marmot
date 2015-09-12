/**
 * Copyright 2015 creditease Inc. All rights reserved.
 * @desc 重写URI指向文件的过滤器
 * @author aiweizhang(aiweizhang@creditease.cn)
 * @date 2015/05/05
 */

package cn.creditease.marmot.filter;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

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
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public class Rewrite implements Filter {
    /**
     * 默认的路由配置文件的路径，可配置
     */
    private String routerFile = "/router/main.xml";
    private Set<String> paths = new HashSet<String>();
    final private String ROUTER_URI_KEY = "uri";
    final private String ROUTER_TARGET_KEY = "target";
    final private String IS_REWRITE = "IS-REWRITE";

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest)request;
        HttpServletResponse res = (HttpServletResponse)response;

        // 仅处理未重写的请求
        if( req.getAttribute(IS_REWRITE) == null ){
            try {
                if(dispose(req, res)){
                    return;
                }
            } catch (XMLStreamException e) {
                e.printStackTrace();
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        String file = config.getInitParameter("routerFile");

        if(file != null && !file.equals("")){
            if(!file.startsWith("/")){
                file = "/" + file;
            }

            this.routerFile = file;
        }
    }

    /**
     * 根据路由文件查找目标文件
     * @param request
     * @param response
     * @return
     * @throws XMLStreamException
     * @throws ServletException
     * @throws IOException
     */
    private Boolean dispose(HttpServletRequest request, HttpServletResponse response) throws XMLStreamException, IOException{

        ServletContext context = request.getSession().getServletContext();

        HashMap<String, String> map;

        String uriOfMap;

        String targetOfMap;

        String uri;

        if(!this.routerFile.endsWith(".xml")){
            return false;
        }

        uri = request.getRequestURI().substring(context.getContextPath().length());
        if(uri.equals("")){
            uri = request.getServletPath();
        } else {
            String query = request.getQueryString();
            if(query != null && !query.equals("")){
                uri += "?" + query;
            }
        }

        map = searchRouter(uri, context, this.routerFile);
        uriOfMap = map.get(ROUTER_URI_KEY);
        targetOfMap = map.get(ROUTER_TARGET_KEY);

        if(uriOfMap != null && !uriOfMap.equals("") && targetOfMap != null && !targetOfMap.equals("")){
            setAttribute(context, map);
            request.setAttribute(IS_REWRITE, "yes");
            try {
                request.getRequestDispatcher(targetOfMap).forward(request, response);
            } catch (ServletException e) {
                e.printStackTrace();
            }
            return true;
        }

        return false;
    }

    /**
     * 递归搜索路由，已处理循环依赖的问题
     * @param uri
     * @param context
     * @param routerPath
     * @return
     * @throws IOException
     * @throws XMLStreamException
     */
    private HashMap<String, String> searchRouter(String uri, ServletContext context,
                                                 String routerPath) throws IOException, XMLStreamException{

        HashMap<String, String> map = new HashMap<String, String>();

        URL routerFile = context.getResource(routerPath);

        String path = routerFile.getPath();

        XMLEventReader reader = getReader(routerFile.getFile());

        XMLEvent event;

        StartElement start;

        String name;

        Attribute attr;

        String src;

        Attribute uriAttribute;

        Attribute targetAttribute;

        if( routerFile == null ){
            return map;
        }

        // 存储已加载的路由文件路径
        paths.add(path);

        while(reader.hasNext()){
            event = reader.nextEvent();
            if(event.isStartElement()){
                start = event.asStartElement();
                name = start.getName().toString();

                //检测是否为import元素并且未加载
                if(name.equals("import") && paths.contains(path)){
                    attr = start.getAttributeByName(new QName("src"));
                    src = attr.getValue();

                    if( src != null && !src.equals("") ){
                        map = searchRouter(uri, context, routerPath.replaceAll("[\\w\\-]+\\.xml$", "") + src);

                        if(!map.isEmpty()){
                            return map;
                        }
                    }
                }

                if(name.equals("route")){
                    uriAttribute = start.getAttributeByName(new QName(ROUTER_URI_KEY));
                    targetAttribute = start.getAttributeByName(new QName(ROUTER_TARGET_KEY));

                    if(uriAttribute != null && targetAttribute != null){
                        if(uri.matches(uriAttribute.getValue())){
                            map.put(ROUTER_URI_KEY, uriAttribute.getValue());
                            map.put(ROUTER_TARGET_KEY, targetAttribute.getValue());
                            break;
                        }
                    }
                }
            }
        }

        reader.close();
        return map;
    }

    /**
     * 对context
     * @param context
     * @param map
     */
    private void setAttribute(ServletContext context, HashMap<String, String> map){
        String target = map.get(ROUTER_TARGET_KEY);

        target = target.substring(0, target.lastIndexOf("."));
        context.setAttribute("mockFile", target);
    }

    /**
     * 获取XML Reader
     * @param string
     * @return
     * @throws FileNotFoundException
     * @throws XMLStreamException
     */
    private XMLEventReader getReader(String string) throws FileNotFoundException, XMLStreamException{
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLEventReader reader = factory.createXMLEventReader(new BufferedReader(new FileReader(string)));
        return reader;
    }

}
