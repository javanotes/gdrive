package com.docview.provider;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.naming.AuthenticationException;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import com.docview.utils.SPIException;
import com.docview.web.Mvc;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

@Component
class GDriveConnect implements FactoryBean<Drive>{
	private static final String APPLICATION_NAME = "elementary";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    
    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE_METADATA_READONLY, DriveScopes.DRIVE);
    @Value("${default.credentials.path:classpath:credentials.json}")
    private String credentialsFile;

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     * @throws GeneralSecurityException 
     */
	private Credential getCredentials() throws IOException, GeneralSecurityException  {
		// ONLY FOR TESTING!
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        		GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, clientSecrets, SCOPES)
		        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
		        .setAccessType("offline")
		        .build();
        
        // keeping a dynamic port so that it does not collide with the Spring embedded server
		SpringJettyLocalServerReceiver receiver = new SpringJettyLocalServerReceiver.Builder()/*.setPort(8888)*/.build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }
    private void buildAuthFlow() throws IOException, GeneralSecurityException {
    	// Build flow and trigger user authorization request.
        flow = new GoogleAuthorizationCodeFlow.Builder(
        		GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, clientSecrets, SCOPES)
		        .setDataStoreFactory(new MemoryDataStoreFactory())
		        .setAccessType("offline")
		        .build();
        
        // keeping a dynamic port so that it does not collide with the Spring embedded server
        receiver = new SpringJettyLocalServerReceiver.Builder()/*.setPort(8888)*/.build();
    }
    private String redirect_url;
    /**
     * The Google OAuth authentication url. Requires a redirect url for handshake.
     * If not provided, will use the default redirect handler that will start an embedded Jetty server
     * @param redirectUrl
     * @return
     */
    public String getOAuthUrl(String redirectUrl) {
    	// https://accounts.google.com/o/oauth2/auth?
        // access_type=offline
        // &client_id=707949111482-vrqkq4bg1mo9l878j7anl5b6q75p2435.apps.googleusercontent.com
        // &redirect_uri=http://localhost:61729/Callback
        // &response_type=code
        // &scope=https://www.googleapis.com/auth/drive.metadata.readonly%20https://www.googleapis.com/auth/drive
    	
    	String redirectUri = redirectUrl;
		if (StringUtils.isEmpty(redirectUri)) {
			//only if no redirect url is provided,
			//turn on the default provider using embedded Jetty
			try {
				redirectUri = receiver.getRedirectUri();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} 
		}
		redirect_url = redirectUri;
		return flow.newAuthorizationUrl().setRedirectUri(redirectUri).build();
    }
    private GoogleAuthorizationCodeFlow flow;
    /**
     * Get the current authorization token, if already received. <b>Note:</b> This
     * will block forever, if a custom redirect url has been used via {@link #getOAuthUrl(String)}.
     * @return
     */
    public String getAuthToken() {
    	if(token != null) {
    		return token.getAccessToken();
    	}
    	return "";
    	/*try {
			return receiver.waitForCode();
		} catch (IOException e) {
			throw new FileIOException("Auth failure", e);
		}*/
    }
    
    @SuppressWarnings("unused")
	@Deprecated
	private Credential getCredentials(String user, String password, boolean refresh) throws IOException, GeneralSecurityException {
		return refresh ? createCredentialWithRefreshToken(user, password, token)
				: createCredentialWithAccessTokenOnly(token);
	}

	private Credential createCredentialWithAccessTokenOnly(TokenResponse tokenResponse) {
		return new Credential(BearerToken.authorizationHeaderAccessMethod()).setFromTokenResponse(tokenResponse);
	}

	private Credential createCredentialWithRefreshToken(String user, String password, TokenResponse tokenResponse) throws GeneralSecurityException, IOException {
		return new Credential.Builder(BearerToken.authorizationHeaderAccessMethod()).setTransport(GoogleNetHttpTransport.newTrustedTransport())
				.setJsonFactory(JSON_FACTORY).setTokenServerUrl(new GenericUrl(clientSecrets.getDetails().getTokenUri()))
				.setClientAuthentication(new BasicAuthentication(user, password)).build()
				.setFromTokenResponse(tokenResponse);
	}
	
	private volatile TokenResponse token = null;
	/**
	 * 
	 * @param authCode
	 * @return
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	private synchronized void requestAccessToken(String authCode) throws IOException, GeneralSecurityException {
		if(token != null)
			return;
		try {
			TokenResponse response = new AuthorizationCodeTokenRequest(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY,
					new GenericUrl(clientSecrets.getDetails().getTokenUri()), authCode)
							.setRedirectUri(redirect_url)
							.setClientAuthentication(new ClientParametersAuthentication(clientSecrets.getDetails().getClientId(), clientSecrets.getDetails().getClientSecret()))
							.execute();
			System.out.println("Access token: " + response.getAccessToken());
			token = response;
		} catch (TokenResponseException e) {
			if (e.getDetails() != null) {
				System.err.println("Error: " + e.getDetails().getError());
				if (e.getDetails().getErrorDescription() != null) {
					System.err.println(e.getDetails().getErrorDescription());
				}
				if (e.getDetails().getErrorUri() != null) {
					System.err.println(e.getDetails().getErrorUri());
				}
			} else {
				System.err.println(e.getMessage());
			}
			
			throw e;
		}
	}

    private VerificationCodeReceiver receiver;
    private Credential defaultCredential;
    private GoogleClientSecrets clientSecrets;
	private String authCode;
    
    public String getAuthCode() {
		return authCode;
	}
	public void setAuthCode(String authCode) {
		this.authCode = authCode;
	}
	@PreDestroy
    private void onStop() throws IOException {
    	receiver.stop();
    }
    @PostConstruct
    private void onStart() throws Exception {
    	// Load client secrets.
        java.io.File in = ResourceUtils.getFile(credentialsFile);
        clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(new FileInputStream(in)));
        buildAuthFlow();
    }
    /**
     * @deprecated ONLY FOR TESTING!
     */
	@Override
	public Drive getObject() throws Exception {
		// this implementation uses the default credential that is provided with the part of the project
		// ideally each user should be passing their google credential to generate oauth2 token
		if(defaultCredential == null)
			defaultCredential = getCredentials();
		
		 Drive service = new Drive.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, defaultCredential)
	                .setApplicationName(APPLICATION_NAME)
	                .build();
		return service;
	}
	/**
	 * New {@linkplain Drive} service api instance.
	 * @param user
	 * @param password
	 * @return
	 * @throws Exception
	 */
	public Drive getServiceProvider() {
		// this implementation uses the default credential that is provided with the part of the project
		// ideally each user should be passing their google credential to generate oauth2 token
		try {
			if(StringUtils.isEmpty(authCode))
				throw new AuthenticationException("OAuth handshake not done. Open url in browser: "+Mvc.HOST_URL);
			
			requestAccessToken(authCode);
			GoogleCredential credential = 
			        new GoogleCredential.Builder()
			            .setTransport(GoogleNetHttpTransport.newTrustedTransport())
			            .setJsonFactory(JSON_FACTORY)
			            .setClientSecrets(clientSecrets.getDetails().getClientId(), clientSecrets.getDetails().getClientSecret())
			            .build();
			credential.createScoped(SCOPES)
			.setFromTokenResponse(token);
			
			return new Drive.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
			            .setApplicationName(APPLICATION_NAME)
			            .build();
			
		} catch (AuthenticationException | GeneralSecurityException | IOException e) {
			e.printStackTrace();
			throw new SPIException(e.getMessage(), e);
		}
	}
	
	@Override
	public Class<?> getObjectType() {
		return Drive.class;
	}

	public boolean isSingleton() {
		return false;
	}
	@SuppressWarnings("unused")
	private String tokenRedirectUri;
	public void setTokenRedirectUri(String string) {
		tokenRedirectUri = string;
	}
}
