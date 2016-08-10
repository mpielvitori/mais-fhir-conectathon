package org.mais.fhir.endpoint;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
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
import org.apache.oltu.oauth2.common.message.OAuthResponse;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.mais.fhir.CustomAuthResponse;
import org.mais.fhir.ErrorResponse;
import org.mais.fhir.SecurityCodeStorage;
import org.mais.fhir.ServerParams;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.OperationOutcomeUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Path("/auth")
@Api(value = "OAuth2")
public class TokenEndpoint {

	public static final String INVALID_CLIENT_DESCRIPTION = "Client authentication failed "
			                                              + "(e.g., unknown client, no client "
			                                              + "authentication included, or unsupported "
			                                              + "authentication method).";

	@Inject
	private SecurityCodeStorage securityCodeStorage;
	
	@POST
	@Consumes("application/x-www-form-urlencoded")
	@Produces({MediaType.APPLICATION_JSON,MediaType.APPLICATION_XML})
	@ApiImplicitParams({
	    @ApiImplicitParam(name = "Authorization", value = "Tipo de autorización", required = true, dataType = "string", paramType = "header", defaultValue="Bearer"),
//	    @ApiImplicitParam(name = "Accept", value = "Encoding", required = false, dataType = "string", paramType = "header", defaultValue="application/xml"),
	    @ApiImplicitParam(name = "Content-Type", value = "Encoding", required = true, dataType = "string", paramType = "header", defaultValue="application/x-www-form-urlencoded"),	    
	    @ApiImplicitParam(name = "grant_type", value = "Tipo de acceso", required = true, dataType = "string", paramType = "query", defaultValue="password"),
	    @ApiImplicitParam(name = "username", value = "Usuario", required = true, dataType = "string", paramType = "query", example="usuario_prestador"),
	    @ApiImplicitParam(name = "password", value = "Contraseña", required = true, dataType = "string", paramType = "query", example="passwd_prestador"),
	    @ApiImplicitParam(name = "client_id", value = "ID de cliente", required = true, dataType = "string", paramType = "query", example="oauth2_client_id"),
	    @ApiImplicitParam(name = "client_secret", value = "Clave secreta de cliente", required = true, dataType = "string", paramType = "query", example="oauth2_client_secret")	    
	})
    @ApiOperation(value = "Generación de token")
    @ApiResponses(value = { 
            @ApiResponse(code = 200, message = "Transacción exitosa (OAuthResponse)", response=OAuthResponse.class),
            @ApiResponse(code = 401, message = "Error de autenticación (OperationOutcome)", reference="OperationOutcome"),
            @ApiResponse(code = 400, message = "Request inválido (OperationOutcome)", reference="OperationOutcome") })	
	public Response authorize(@Context HttpServletRequest request)
			throws OAuthSystemException {
		try {
			request.getParameterNames();
			request.getQueryString();
			OAuthTokenRequest oauthRequest = new OAuthTokenRequest(request);
			OAuthIssuer oauthIssuerImpl = new OAuthIssuerImpl(
					new MD5Generator());

			if (!checkClientId(oauthRequest.getClientId())) {

				return makeAuthenticationError(request, OAuthError.TokenResponse.INVALID_CLIENT, INVALID_CLIENT_DESCRIPTION, HttpServletResponse.SC_UNAUTHORIZED);
		
			}

			if (!checkClientSecret(oauthRequest.getClientSecret())) {
				return makeAuthenticationError(request, OAuthError.TokenResponse.UNAUTHORIZED_CLIENT, INVALID_CLIENT_DESCRIPTION, HttpServletResponse.SC_UNAUTHORIZED);
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

			//TODO setear Datos del Prestador asociado al token
			securityCodeStorage.addToken(accessToken);

//			OAuthResponse response = OAuthASResponse
//					.tokenResponse(HttpServletResponse.SC_OK)
//					.setAccessToken(accessToken).setExpiresIn("3600")
//					.buildJSONMessage();
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(new Date());
			calendar.add(Calendar.YEAR, 1);
	
			if (ServerParams.isXML(request.getContentType(), request.getHeader("Accept"))){
				return Response.status(HttpServletResponse.SC_OK).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML)
						.entity(new CustomAuthResponse.Builder().
									accessToken(accessToken).tokenType("Bearer").
									tokenValidUntil(new SimpleDateFormat("dd/MM/YYYY").format(calendar.getTime())).
										build()).
											build();					
			}
			else {
				return Response.status(HttpServletResponse.SC_OK).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
						.entity(new CustomAuthResponse.Builder().
									accessToken(accessToken).tokenType("Bearer").
									tokenValidUntil(new SimpleDateFormat("dd/MM/YYYY").format(calendar.getTime())).
										build()).
											build();				
			}	


		} catch (OAuthProblemException e) {
//			return Response.status(HttpServletResponse.SC_BAD_REQUEST)
//					.entity(new ErrorResponse.Builder().error(e.getCause()!=null ? e.getCause().toString() : e.getError()).
//							errorDescription(e.getDescription()).build()).build();
			return makeAuthenticationError(request, String.valueOf(HttpServletResponse.SC_BAD_REQUEST), OAuthError.TokenResponse.INVALID_REQUEST, HttpServletResponse.SC_BAD_REQUEST);
//			OAuthResponse res = OAuthASResponse
//					.errorResponse(HttpServletResponse.SC_BAD_REQUEST).error(e)
//					.buildJSONMessage();
//			return Response.status(res.getResponseStatus())
//					.entity(res.getBody()).build();
		}
	}
	
	@GET
	@Path("/{token}")
	@ApiOperation(value = "Validación de token(Uso interno)")
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
	
	private Response makeAuthenticationError(ServletRequest request, String errorCode, String errorDetails, int errorStatus){
		
		HttpServletRequest req = (HttpServletRequest) request;
		FhirContext ctx = FhirContext.forDstu2();
		IParser parser;
		
		if (ServerParams.isXML(req.getContentType(), req.getHeader("Accept"))){
			parser = ctx.newXmlParser();
//			res.setContentType(ServerParams.APPLICATION_XML_FHIR);
		}
		else {
			parser = ctx.newJsonParser();
//			res.setContentType(ServerParams.APPLICATION_JSON_FHIR);
		}		
		
		IBaseOperationOutcome operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
		OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", errorDetails, null, errorCode);	
		String resultString = parser.setPrettyPrint(true).encodeResourceToString(operationOutcome);		
	
		
		return Response.status(errorStatus)
				.entity(resultString).build();
		
	}
}