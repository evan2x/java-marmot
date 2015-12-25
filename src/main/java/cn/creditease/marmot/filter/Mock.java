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
    private String mockDataDirectory = "/mock";

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");

        processor(req, resp);
        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        String directory = config.getInitParameter("mockDir");
        if(directory != null && !directory.equals("")){
            this.mockDataDirectory = directory;
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
    private Boolean processor(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletContext context = request.getSession().getServletContext();
        String mockFile = (String)context.getAttribute("mockFile");

        if(mockFile == null || mockFile.equals("")){
            return false;
        }

        String mockPath = getMockPath(mockFile);
        String[] tryTypes = new String[]{mockPath + ".jsp", mockPath + ".json"};

        for(int count = 0; count < tryTypes.length; count++){
            String item = tryTypes[count];
            URL url = context.getResource(item);

            if(url == null){
                continue;
            }

            if(item.endsWith(".jsp")){
                request.getRequestDispatcher(item).include(request, response);
                return true;
            } else {
                JSONObject data = extractJSON(url);
                for(String key : data.keySet()){
                    request.setAttribute(key, data.get(key));
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 加载JSON文件
     * @param url
     * @throws IOException
     */
    private JSONObject extractJSON(URL url) throws IOException {
        BufferedReader buffer = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
        String data = "";
        String line;
        while((line = buffer.readLine()) != null){
            data += line;
        }
        buffer.close();

        return JSONObject.parseObject(data);
    }

    /**
     * 获取mockdata的路径
     * @param mockPath
     * @return
     */
    private String getMockPath(String mockPath){
        if(this.mockDataDirectory.endsWith("/")){
            if(mockPath.startsWith("/")){
                mockPath = mockPath.substring(1, mockPath.length());
            }
        } else {
            if (!mockPath.startsWith("/")) {
                mockPath = "/" + mockPath;
            }
        }

        if(!this.mockDataDirectory.startsWith("/")){
            this.mockDataDirectory = "/" + this.mockDataDirectory;
        }

        return this.mockDataDirectory + mockPath;
    }

}
