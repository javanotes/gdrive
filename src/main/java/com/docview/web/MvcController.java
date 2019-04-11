package com.docview.web;

import java.io.IOException;
import java.io.PrintWriter;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.docview.ServiceProvider;

@Controller
public class MvcController {
	@Autowired
	ServiceProvider connector;
	@Autowired
	ServerProperties env;
	@PostConstruct
	private void onStart() {
	}
	private static final String AUTH_REDIRECT = "/ocallback";
	public static final String HOST_URL = "http://localhost:@port@";
	private static final String AUTH_REDIRECT_URL = HOST_URL+AUTH_REDIRECT;
	
	public String getHostUrl() {
		return HOST_URL.replaceFirst("@port@", env.getPort()+"");
	}
	@RequestMapping(value = "/")
    public String redirectToMainPage() {
        return "redirect:" + connector.getOAuthUrl(AUTH_REDIRECT_URL.replaceFirst("@port@", env.getPort()+""));
    }
	@GetMapping(AUTH_REDIRECT)
	public void authCallback(@RequestParam String code, HttpServletResponse response) throws IOException {
		connector.setOAuthToken(code);
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("text/html");
		PrintWriter doc = response.getWriter();
		doc.println("<html>");
		doc.println("<head><title>OAuth 2.0 Authentication Token Received</title></head>");
		doc.println("<body>");
		doc.println("Received verification code. Now you may continue ..");
		doc.println("</body>");
		doc.println("</html>");
		doc.flush();
	}
}
