package com.docview.provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.docview.ServiceProvider;
import com.google.api.services.drive.Drive;

@Service
class DeafultServiceProvider implements ServiceProvider {
	@Autowired
	GDriveConnect connector;
	@Override
	public Drive getInstance() {
		return connector.getServiceProvider();
	}

	@Override
	public void setOAuthCode(String code) {
		connector.setAuthCode(code);
	}

	@Override
	public String getOAuthUrl(String redirectUrl) {
		return connector.getOAuthUrl(redirectUrl);
	}

}
