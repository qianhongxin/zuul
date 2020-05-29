/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package com.netflix.zuul.http;

import com.netflix.zuul.FilterProcessor;
import com.netflix.zuul.ZuulRunner;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Core Zuul servlet which intializes and orchestrates zuulFilter execution
 *
 * @author Mikey Cohen
 *         Date: 12/23/11
 *         Time: 10:44 AM
 */

/**
 * zuul1.x的启动入口是基于servlet，2.x是基于Netty的server
 */
public class ZuulServlet extends HttpServlet {

    private static final long serialVersionUID = -3374242278843351500L;
    private ZuulRunner zuulRunner;


    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        String bufferReqsStr = config.getInitParameter("buffer-requests");
        boolean bufferReqs = bufferReqsStr != null && bufferReqsStr.equals("true") ? true : false;

        zuulRunner = new ZuulRunner(bufferReqs);
    }

    /**
     * 执行zuulServlet时，需要处理
     *
     * @param servletRequest
     * @param servletResponse
     * @throws ServletException
     * @throws IOException
     */
    @Override
    public void service(javax.servlet.ServletRequest servletRequest, javax.servlet.ServletResponse servletResponse) throws ServletException, IOException {
        try {
            init((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);

            // Marks this request as having passed through the "Zuul engine", as opposed to servlets
            // explicitly bound in web.xml, for which requests will not have the same data attached
            //将此请求标记为已通过“Zuul引擎”，而不是在web.xml中显式绑定的servlet，请求将不会附加相同的数据
            RequestContext context = RequestContext.getCurrentContext();
            context.setZuulEngineRan();

            try {
                //执行前置过滤器
                preRoute();
            } catch (ZuulException e) {
                error(e);
                //执行后置过滤器
                postRoute();
                return;
            }
            try {
                //执行路由过滤器
                route();
            } catch (ZuulException e) {
                error(e);
                //执行后置过滤器
                postRoute();
                return;
            }
            try {
                //执行后置过滤器
                postRoute();
            } catch (ZuulException e) {
                error(e);
                return;
            }

        } catch (Throwable e) {
            error(new ZuulException(e, 500, "UNHANDLED_EXCEPTION_" + e.getClass().getName()));
        } finally {
            RequestContext.getCurrentContext().unset();
        }
    }

    /**
     * executes "post" ZuulFilters
     *
     * @throws ZuulException
     */
    void postRoute() throws ZuulException {
        zuulRunner.postRoute();
    }

    /**
     * executes "route" filters
     *
     * @throws ZuulException
     */
    void route() throws ZuulException {
        zuulRunner.route();
    }

    /**
     * executes "pre" filters
     *
     * @throws ZuulException
     */
    void preRoute() throws ZuulException {
        zuulRunner.preRoute();
    }

    /**
     * initializes request
     *
     * @param servletRequest
     * @param servletResponse
     */
    void init(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        zuulRunner.init(servletRequest, servletResponse);
    }

    /**
     * sets error context info and executes "error" filters
     *
     * @param e
     */
    void error(ZuulException e) {
        RequestContext.getCurrentContext().setThrowable(e);
        zuulRunner.error();
    }

    @RunWith(MockitoJUnitRunner.class)
    public static class UnitTest {

        @Mock
        HttpServletRequest servletRequest;
        @Mock
        HttpServletResponseWrapper servletResponse;
        @Mock
        FilterProcessor processor;
        @Mock
        PrintWriter writer;

        @Before
        public void before() {
            MockitoAnnotations.initMocks(this);
        }

        @Test
        public void testProcessZuulFilter() {

            ZuulServlet zuulServlet = new ZuulServlet();
            zuulServlet = spy(zuulServlet);
            RequestContext context = spy(RequestContext.getCurrentContext());


            try {
                FilterProcessor.setProcessor(processor);
                RequestContext.testSetCurrentContext(context);
                when(servletResponse.getWriter()).thenReturn(writer);

                zuulServlet.init(servletRequest, servletResponse);
                verify(zuulServlet, times(1)).init(servletRequest, servletResponse);
                assertTrue(RequestContext.getCurrentContext().getRequest() instanceof HttpServletRequestWrapper);
                assertTrue(RequestContext.getCurrentContext().getResponse() instanceof HttpServletResponseWrapper);

                zuulServlet.preRoute();
                verify(processor, times(1)).preRoute();

                zuulServlet.postRoute();
                verify(processor, times(1)).postRoute();
//                verify(context, times(1)).unset();

                zuulServlet.route();
                verify(processor, times(1)).route();
                RequestContext.testSetCurrentContext(null);

            } catch (Exception e) {
                e.printStackTrace();
            }


        }
    }

}
