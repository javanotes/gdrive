package com.docview;

import com.google.api.services.drive.Drive;
/**
 * A wrapper interface for {@linkplain Drive} instance provider. This had to be pulled up
 * since we are using different strategies for OAuth in testing mode and actual mode. Well, this
 * needed some hack into the Google java client.
 * @author Sutanu_Dalui
 *
 */
public interface ServiceProvider {
	/**
	 * Get an instance of core {@linkplain Drive} api. This method is mocked
	 * in testing scope to provide a installed app style OAuth. By default this will
	 * return a server side style OAuth using a redirecting login page.
	 * @return
	 */
	Drive getInstance();
	/**
	 * The redirect url controller will invoke this to set the auth token received. 
	 * @param code
	 */
	void setOAuthToken(String code);
	/**
	 * Get the OAuth token generated
	 * @return
	 */
	String getOAuthToken();
	/**
	 * The default page will redirect to the Google auth url as constructed with various parameters.
	 * @param redirectUrl
	 * @return
	 */
	String getOAuthUrl(String redirectUrl);
	void setTokenRedirectUri(String string);
}

