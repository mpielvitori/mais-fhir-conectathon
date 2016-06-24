package org.mais.fhir.endpoint;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.oltu.oauth2.as.issuer.MD5Generator;
import org.apache.oltu.oauth2.as.issuer.OAuthIssuer;
import org.apache.oltu.oauth2.as.issuer.OAuthIssuerImpl;
import org.apache.oltu.oauth2.as.request.OAuthTokenRequest;
import org.apache.oltu.oauth2.common.error.OAuthError;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.mais.fhir.CustomAuthResponse;
import org.mais.fhir.ErrorResponse;
import org.mais.fhir.SecurityCodeStorage;
import org.mais.fhir.ServerParams;

@Path("/auth")
public class TokenEndpoint {

	public static final String INVALID_CLIENT_DESCRIPTION = "Client authentication failed "
			                                              + "(e.g., unknown client, no client "
			                                              + "authentication included, or unsupported "
			                                              + "authentication method).";

	@Inject
	private SecurityCodeStorage securityCodeStorage;
	
	@POST
	@Consumes("application/x-www-form-urlencoded")
	@Produces({MediaType.APPLICATION_XML})
	public Response authorize(@Context HttpServletRequest request)
			throws OAuthSystemException {
		try {
			request.getParameterNames();
			request.getQueryString();
			OAuthTokenRequest oauthRequest = new OAuthTokenRequest(request);
			OAuthIssuer oauthIssuerImpl = new OAuthIssuerImpl(
					new MD5Generator());

			if (!checkClientId(oauthRequest.getClientId())) {
				return buildInvalidClientIdResponse();
			}

			if (!checkClientSecret(oauthRequest.getClientSecret())) {
				return buildInvalidClientSecretResponse();
			}

//			Validación de user, pass comentada para que se agreguen a medida que ejecutan este servicio		
//			if (oauthRequest.getParam(OAuth.OAUTH_GRANT_TYPE).equals(
//					GrantType.AUTHORIZATION_CODE.toString())) {
//				if (!checkAuthCode(oauthRequest.getParam(OAuth.OAUTH_CODE))) {
//					return buildBadAuthCodeResponse();
//				}
//			} else if (oauthRequest.getParam(OAuth.OAUTH_GRANT_TYPE).equals(
//					GrantType.PASSWORD.toString())) {
//				if (!checkUserPass(oauthRequest.getUsername(),
//						oauthRequest.getPassword())) {
//					return buildInvalidUserPassResponse();
//				}
//			} else if (oauthRequest.getParam(OAuth.OAUTH_GRANT_TYPE).equals(
//					GrantType.REFRESH_TOKEN.toString())) {
//				// not supported in this implementation
//				buildInvalidUserPassResponse();
//			}

			final String accessToken = oauthIssuerImpl.accessToken();

			securityCodeStorage.addToken(accessToken);

//			OAuthResponse response = OAuthASResponse
//					.tokenResponse(HttpServletResponse.SC_OK)
//					.setAccessToken(accessToken).setExpiresIn("3600")
//					.buildJSONMessage();
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(new Date());
			calendar.add(Calendar.YEAR, 1);
			
			return Response.status(HttpServletResponse.SC_OK)
					.entity(new CustomAuthResponse.Builder().
								accessToken(accessToken).tokenType("Bearer").
								tokenValidUntil(new SimpleDateFormat("dd/MM/YYYY").format(calendar.getTime())).
									build()).
										build();

		} catch (OAuthProblemException e) {
			return Response.status(HttpServletResponse.SC_BAD_REQUEST)
					.entity(new ErrorResponse.Builder().error(e.getCause()!=null ? e.getCause().toString() : e.getError()).
							errorDescription(e.getDescription()).build()).build();			
//			OAuthResponse res = OAuthASResponse
//					.errorResponse(HttpServletResponse.SC_BAD_REQUEST).error(e)
//					.buildJSONMessage();
//			return Response.status(res.getResponseStatus())
//					.entity(res.getBody()).build();
		}
	}
	
	@GET
	@Path("/{token}")
	public Response exists(@PathParam("token") String token) {
		if (securityCodeStorage.isValidToken(token)) {
			return Response.status(Status.OK).build();
		} else {
			return Response.status(Status.NOT_FOUND).build();
		}
	}

	private Response buildInvalidClientIdResponse() throws OAuthSystemException {
		return Response.status(HttpServletResponse.SC_BAD_REQUEST)
				.entity(new ErrorResponse.Builder().error(OAuthError.TokenResponse.INVALID_CLIENT).errorDescription(INVALID_CLIENT_DESCRIPTION).build()).build();		
		
//		OAuthResponse response = OAuthASResponse
//				.errorResponse(HttpServletResponse.SC_BAD_REQUEST)
//				.setError(OAuthError.TokenResponse.INVALID_CLIENT)
//				.setErrorDescription(INVALID_CLIENT_DESCRIPTION)
//				.buildJSONMessage();
//		return Response.status(response.getResponseStatus())
//				.entity(response.getBody()).build();
	}

	private Response buildInvalidClientSecretResponse()
			throws OAuthSystemException {
		return Response.status(HttpServletResponse.SC_UNAUTHORIZED)
				.entity(new ErrorResponse.Builder().error(OAuthError.TokenResponse.UNAUTHORIZED_CLIENT).errorDescription(INVALID_CLIENT_DESCRIPTION).build()).build();
		
//		OAuthResponse response = OAuthASResponse
//				.errorResponse(HttpServletResponse.SC_UNAUTHORIZED)
//				.setError(OAuthError.TokenResponse.UNAUTHORIZED_CLIENT)
//				.setErrorDescription(INVALID_CLIENT_DESCRIPTION)
//				.buildJSONMessage();
//		return Response.status(response.getResponseStatus())
//				.entity(response.getBody()).build();
	}

//	private Response buildBadAuthCodeResponse() throws OAuthSystemException {
//		OAuthResponse response = OAuthASResponse
//				.errorResponse(HttpServletResponse.SC_BAD_REQUEST)
//				.setError(OAuthError.TokenResponse.INVALID_GRANT)
//				.setErrorDescription("invalid authorization code")
//				.buildJSONMessage();
//		return Response.status(response.getResponseStatus())
//				.entity(response.getBody()).build();
//	}
//
//	private Response buildInvalidUserPassResponse() throws OAuthSystemException {
//		OAuthResponse response = OAuthASResponse
//				.errorResponse(HttpServletResponse.SC_BAD_REQUEST)
//				.setError(OAuthError.TokenResponse.INVALID_GRANT)
//				.setErrorDescription("invalid username or password")
//				.buildJSONMessage();
//		return Response.status(response.getResponseStatus())
//				.entity(response.getBody()).build();
//	}

	private boolean checkClientId(String clientId) {
		return ServerParams.CLIENT_ID.equals(clientId);
	}

	private boolean checkClientSecret(String secret) {
		return ServerParams.CLIENT_SECRET.equals(secret);
	}

//	private boolean checkAuthCode(String authCode) {
//		return securityCodeStorage.isValidAuthCode(authCode);
//	}

}