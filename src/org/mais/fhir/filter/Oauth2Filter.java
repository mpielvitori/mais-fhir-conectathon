package org.mais.fhir.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.ParameterStyle;
import org.apache.oltu.oauth2.rs.request.OAuthAccessResourceRequest;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.mais.fhir.ServerParams;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.OperationOutcomeUtil;

@WebFilter(urlPatterns={"/debitos/*","/facturacion/*","/pendiente/*","/documento/*"})
public class Oauth2Filter implements Filter {
	
    @Override
	public void destroy() {	}

    @Override
	public void init(FilterConfig fConfig) throws ServletException { }
	
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		
		HttpServletResponse res = (HttpServletResponse) response;
		
		try {
		
			OAuthAccessResourceRequest oauthRequest = new OAuthAccessResourceRequest((HttpServletRequest) request, ParameterStyle.HEADER);
			String accessToken = oauthRequest.getAccessToken();
			
			if ( !isValidToken(accessToken) ) {				
				makeAuthenticationError(request, res);
			}
			else		
				chain.doFilter(request, response);
		
		} catch (OAuthSystemException | OAuthProblemException e) {			
			makeAuthenticationError(request, res);					
		}
		
	}
	
	private boolean isValidToken(String token) {
		boolean validToken = false;
		
		String restUrl = ServerParams.OAUTH_SERVER_URL + "auth/" + token;			
		ResteasyClient client = new ResteasyClientBuilder().build();
		Client c = ClientBuilder.newClient();
		ResteasyWebTarget target = client.target(restUrl);
		/* Con Jersey						
		 * URL restUrl = new URL(OAUTH_SERVER_URL + "token/" + token);
		 * WebTarget target = c.target(restUrl.toURI()); 
		*/
		
		Response response = target.request().get();
		
		validToken = Status.OK.getStatusCode() == response.getStatus();

		return validToken;
	}
	
	private void makeAuthenticationError(ServletRequest request, HttpServletResponse res){
		
		HttpServletRequest req = (HttpServletRequest) request;
		FhirContext ctx = FhirContext.forDstu2();
		IParser parser;
		
		if (ServerParams.isXML(req.getContentType(), req.getHeader("Accept"))){
			parser = ctx.newXmlParser();
			res.setContentType(ServerParams.APPLICATION_XML_FHIR);
		}
		else {
			parser = ctx.newJsonParser();
			res.setContentType(ServerParams.APPLICATION_JSON_FHIR);
		}		
		
		IBaseOperationOutcome operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
		OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", OAuth.HeaderType.WWW_AUTHENTICATE, null, String.valueOf(HttpServletResponse.SC_UNAUTHORIZED));	
		String resultString = parser.setPrettyPrint(true).encodeResourceToString(operationOutcome);		
		
		res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		
		try {
			res.getOutputStream().println(resultString);
		} catch (IOException e) {
			org.slf4j.LoggerFactory.getLogger(getClass().getName()).error("Error trying to handle oauth problem ", e);
		}	
		
//		OAuthResponse oauthResponse = OAuthRSResponse
//        											.errorResponse(HttpServletResponse.SC_UNAUTHORIZED)
//        											.setRealm(ServerParams.RESOURCE_SERVER_NAME)
//        											.setError(OAuthError.ResourceResponse.INVALID_TOKEN)
//      	  											.buildHeaderMessage();
//
//		res.addHeader(OAuth.HeaderType.WWW_AUTHENTICATE, oauthResponse.getHeader(OAuth.HeaderType.WWW_AUTHENTICATE));
//		res.setStatus(oauthResponse.getResponseStatus());
//		res.sendError(oauthResponse.getResponseStatus());
		
	}
}
