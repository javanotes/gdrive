package com.docview;

import com.google.api.services.drive.Drive;

public interface ServiceProvider {
	Drive getInstance();
	void setOAuthCode(String code);
	String getOAuthUrl(String redirectUrl);
}

