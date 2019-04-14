package com.docview.web;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import com.docview.ServiceProvider;

@Configuration
class Filters {
	@Bean
	SimpleAuthFilter authFilter() {
		return new SimpleAuthFilter();
	}
	@Bean
	public FilterRegistrationBean<SimpleAuthFilter> filterBean(){
	    FilterRegistrationBean<SimpleAuthFilter> registrationBean 
	      = new FilterRegistrationBean<>();
	         
	    registrationBean.setFilter(authFilter());
	    registrationBean.addUrlPatterns("/api/*");
	         
	    return registrationBean;    
	}
	
	static class SimpleAuthFilter implements Filter {
		@Override
		public void init(FilterConfig filterConfig) throws ServletException {
		}
		@Autowired
		ServiceProvider provider;
		@Override
		public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
				throws IOException, ServletException {
			 HttpServletRequest request = (HttpServletRequest) req;
	         HttpServletResponse response = (HttpServletResponse) res;
	         
	         String authHdr = request.getHeader(HttpHeaders.AUTHORIZATION);
	         if(StringUtils.hasText(authHdr) && authHdr.startsWith("Bearer")) {
	        	 String token = authHdr.substring("Bearer".length()).trim();
	        	 if(token.equals(provider.getOAuthToken())) {
	        		 chain.doFilter(request, response);
	        		 return;
	        	 }
	         }
	         
	         response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "OAuth token not found/mismatch");
		}

		@Override
		public void destroy() {
		}

	}

}
