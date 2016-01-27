/**
 * Copyright 2015 creditease Inc. All rights reserved.
 * @desc 针对模板的模拟数据绑定
 * @author aiweizhang(aiweizhang@creditease.cn)
 * @date 2015/05/05
 */

package cn.creditease.marmot.filter;

import java.io.IOException;
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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

import cn.creditease.marmot.Utils;

public class MockFilter implements Filter {
    private String mockDataDirectory = "/mock";

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");

        if(processor(req, resp)){
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig config) {
        String directory = config.getInitParameter("mockDir");
        if (directory != null && !directory.isEmpty()) {
            this.mockDataDirectory = directory;
        }
    }

    /**
     * mock数据处理, 当context中存在data字段, 则直接用data数据填充至当前请求中
     * 否则从本地读取模板的mock数据, 本地路径为以'mockDir'参数的值为根目录的同名文件(不包括后缀名), 优先级为 1. '.jsp' 2. '.json'
     * 示例如下:
     * mockDir = /mock
     * 模板文件: /views/user/info.vm
     * 数据文件: /mock/user/info.jsp 或者 /mock/user/info.json
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException
     * @throws IOException
     */
    private Boolean processor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        ServletContext context = request.getSession().getServletContext();
        String location = (String) context.getAttribute("location");
        String data = (String) context.getAttribute("data");
        String targetUrl = (String) context.getAttribute("url");

        // 绑定从provider取到的数据
        if (data != null && !data.isEmpty()) {
            try {
                requestBindData(request, data);
                // 去除Content-Length, 使用Transfer-Encoding: chunked
                response.setContentLength(-1);
                return true;
            } catch (JSONException e) {
                throw new JSONException("("+ targetUrl +") API returns an invalid JSON");
            }

        // 绑定本地数据
        } else {
            String mockPath = getMockPath(location);
            String[] tryTypes = new String[]{
                mockPath + ".jsp",
                mockPath + ".json"
            };

            for (String item : tryTypes) {
                URL resource = context.getResource(item);
                if(resource == null){
                    continue;
                }

                if(item.endsWith(".jsp")){
                    request.getRequestDispatcher(item).include(request, response);
                    return true;
                } else {
                    data = Utils.stream2string(resource.openStream());
                    try {
                        requestBindData(request, data);
                        return true;
                    } catch (JSONException e) {
                        throw new JSONException("("+ item +") file content an invalid JSON");
                    }
                }
            }
        }
        return false;
    }

    /**
     * request绑定数据, 将数据转为JSON后再设置到request的attribute中
     * @param request servlet request
     * @param stringData 字符串数据
     */
    private void requestBindData(HttpServletRequest request, String stringData){
        JSONObject data = JSON.parseObject(stringData);
        for(String key : data.keySet()){
            request.setAttribute(key, data.get(key));
        }
    }

    /**
     * 根据模板路径获取本地模拟数据文件的路径
     * @param templatePath 模板路径
     * @return 本地mock数据路径
     */
    private String getMockPath(String templatePath){
        int length = templatePath.length();
        int dotOffset = templatePath.lastIndexOf(".");
        String mockPath = templatePath.substring(0, length);

        if(dotOffset > 0){
            mockPath = templatePath.substring(0, dotOffset);
        }

        mockPath = Utils.pathNormalize(this.mockDataDirectory + "/" + mockPath);

        if(!mockPath.startsWith("/")){
            mockPath = "/" + mockPath;
        }

        return mockPath;
    }

}
