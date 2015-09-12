/**
 * Copyright 2015 creditease Inc. All rights reserved.
 * @desc 加载模拟数据
 * @author aiweizhang(aiweizhang@creditease.cn)
 * @date 2015/05/05
 */
package cn.creditease.marmot.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.fastjson.JSONObject;

public class Mock implements Filter {
    private String mockDir = "/mock";

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        dispose(req, res);
        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        String dir = config.getInitParameter("mockDir");

        if(dir != null && !dir.equals("")){
            this.mockDir = dir;
        }
    }

    /**
     * mock数据处理
     * @param request
     * @param response
     * @return
     * @throws ServletException
     * @throws IOException
     */
    private Boolean dispose(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        ServletContext context = request.getSession().getServletContext();

        String mockFile = (String) context.getAttribute("mockFile");

        String mockpath;

        ArrayList<String> mockList = new ArrayList<String>();

        URL url;

        if(mockFile == null){
            return false;
        }

        mockpath = getMockPath(mockFile);

        mockList.add(mockpath + ".json");
        mockList.add(mockpath + ".jsp");

        for(String mockItem : mockList){
            url = context.getResource(mockItem);
            if(url != null){
                if(mockItem.endsWith(".jsp")){
                    request.getRequestDispatcher(mockItem).include(request, response);
                    return true;
                } else {
                    includeJSON(request, url);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 加载JSON文件
     * @param request
     * @param url
     * @throws IOException
     */
    private void includeJSON(HttpServletRequest request, URL url) throws IOException {

        BufferedReader buffer = new BufferedReader(new InputStreamReader(url.openStream()));

        String data = "";

        String line;

        JSONObject json;

        while((line = buffer.readLine()) != null){
            data += line;
        }
        buffer.close();

        json = JSONObject.parseObject(data);

        for(String key : json.keySet()){
            request.setAttribute(key, json.get(key));
        }
    }

    /**
     * 获取mockdata的路径
     * @param mockPath
     * @return
     */
    private String getMockPath(String mockPath){
        if(this.mockDir.endsWith("/")){
            if(mockPath.startsWith("/")){
                mockPath = mockPath.substring(1, mockPath.length());
            }
        } else {
            if (!mockPath.startsWith("/")) {
                mockPath = "/" + mockPath;
            }
        }

        if(!this.mockDir.startsWith("/")){
            this.mockDir = "/" + this.mockDir;
        }

        return this.mockDir + mockPath;
    }

}
