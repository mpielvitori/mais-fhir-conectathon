package org.mais.fhir.endpoint;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

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

import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.ParameterStyle;
import org.apache.oltu.oauth2.rs.request.OAuthAccessResourceRequest;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.mais.fhir.CustomAuthResponse;
import org.mais.fhir.SecurityCodeStorage;
import org.mais.fhir.ServerParams;
import org.mais.fhir.TransactionFacturacionEntity;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Binary;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Bundle.Entry;
import ca.uhn.fhir.model.dstu2.resource.Bundle.EntryResponse;
import ca.uhn.fhir.model.dstu2.resource.Claim;
import ca.uhn.fhir.model.dstu2.resource.Claim.Item;
import ca.uhn.fhir.model.dstu2.resource.ClaimResponse;
import ca.uhn.fhir.model.dstu2.resource.ClaimResponse.Error;
import ca.uhn.fhir.model.dstu2.resource.OperationOutcome;
import ca.uhn.fhir.model.dstu2.resource.Organization;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.dstu2.valueset.BundleTypeEnum;
import ca.uhn.fhir.model.primitive.DateTimeDt;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.OperationOutcomeUtil;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Path("/")
@Api(value = "Facturación y Débitos MAIS")
public class ConectathonEndpoint {	
	
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ConectathonEndpoint.class);	
	
	@Inject
	private SecurityCodeStorage securityCodeStorage;
	
	/*
	 * endpoint <base>/mais-fhir-conectathon/facturacion
	 * endpoint <base>/mais-fhir-conectathon/facturacion?inputFormat=json&outputFormat=xml
	 */
	@POST
	@Consumes({MediaType.APPLICATION_JSON,MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON,MediaType.APPLICATION_XML})
	@Path("/facturacion")
	@ApiImplicitParams({
	    @ApiImplicitParam(name = "Authorization", value = "Access token obtenido en servicio /auth", required = true, dataType = "string", paramType = "header", example="Bearer token_value")
//	    @ApiImplicitParam(name = "Accept", value = "Encoding", required = false, dataType = "string", paramType = "header")
	})
    @ApiOperation(value = "Registración de facturas")
    @ApiResponses(value = { 
            @ApiResponse(code = 200, message = "Transacción exitosa (OperationOutcome)", reference="OperationOutcome"),
            @ApiResponse(code = 401, message = "Error de autenticación (OperationOutcome)", reference="OperationOutcome"),
            @ApiResponse(code = 400, message = "Bundle inválido (OperationOutcome)", reference="OperationOutcome") })
	//TODO Mapear OperationOutcome
//    @ApiResponses(value = { 
//        @ApiResponse(code = 200, message = "successful operation", response = CustomAuthResponse.class),        
//        @ApiResponse(code = 400, message = "Invalid Order", response = CustomAuthResponse.class) })	
	public Response processClaim(@ApiParam(value = "Bundle con Claims" ) String requestBundle, @Context HttpServletRequest request) throws OAuthSystemException, OAuthProblemException {

		String token = getRequestToken(request);		
		FhirContext ctx = FhirContext.forDstu2();
		IParser parser;
		
		if (ServerParams.isXML(request.getContentType(), request.getHeader("Accept"))){
			parser = ctx.newXmlParser();
		}		
		else {
			parser = ctx.newJsonParser();
		}

		Bundle bundle = parser.parseResource(Bundle.class, requestBundle);
		
		ValidationResult validationResult = createFhirValidator(ctx).validateWithResult(bundle);
		IBaseOperationOutcome operationOutcome = validationResult.toOperationOutcome();
		int result = HttpServletResponse.SC_BAD_REQUEST;
		boolean validBundle = false;
		
		if (validationResult.isSuccessful()){			
			//validar profile correspondiente	
			try {
				for (IdDt profile : (ArrayList<IdDt>) bundle.getResourceMetadata().get(ResourceMetadataKeyEnum.PROFILES)){
					if (profile.equals(ServerParams.FACTURACION_REQUEST_PROFILE)){
						validBundle = true;
						break;
					}
				}
			}
			catch(Throwable e){
				logger.error("Error obteniendo profiles del Bundle ", e);
			}
			if (!validBundle){
				operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
				OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", "No se encontró un perfil válido para el request.", null, "processing");				
			}
			else {
				
				try {
					result = HttpServletResponse.SC_OK;
					Integer transactionId = processClaimRequest(bundle, token);					
					operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
					OperationOutcomeUtil.addIssue(ctx, operationOutcome, "information", "0", null, "Trámite Generado");	
					((OperationOutcome) operationOutcome).setId(transactionId.toString());
					
				} catch (SinClaimsException e) {
					operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
					OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", "No se encontraron elementos del tipo Claim.", null, "processing");
				} catch (SinOrganizacionException e) {
					operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
					OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", "Organización incorrecta.", null, "processing");
				}
				
			}
		}
		
		String resultString = parser.setPrettyPrint(true).encodeResourceToString(operationOutcome);
		
		return Response.status(result).entity(resultString).build();
	}
	
	/*
	 * endpoint <base>/mais-fhir-conectathon/debitos/fecha_rango/<fecha_desde>/<fecha_hasta>
	 */
	@GET
	@Path("/debitos/{filter}/{param1}/{param2}")
	@Produces({MediaType.APPLICATION_JSON,MediaType.APPLICATION_XML})
	@ApiImplicitParams({
	    @ApiImplicitParam(name = "Authorization", value = "Access token obtenido en servicio /auth", required = true, dataType = "string", paramType = "header", example="Bearer token_value")
//	    @ApiImplicitParam(name = "Accept", value = "Encoding", required = false, dataType = "string", paramType = "header")
	})
    @ApiOperation(value = "Consulta de Débitos por rango de fecha")
    @ApiResponses(value = { 
            @ApiResponse(code = 200, message = "Consulta exitosa (Bundle)", reference="Bundle"),
            @ApiResponse(code = 401, message = "Error de autenticación (OperationOutcome)", reference="OperationOutcome"),
            @ApiResponse(code = 400, message = "Request inválido (OperationOutcome)", reference="OperationOutcome") })
	public Response processDebitoRango( @ApiParam(value="Filtro", required=true, defaultValue=ServerParams.FILTRO_FECHA_RANGO) @PathParam("filter") String filter, 
										@ApiParam(value="Fecha desde", required=true, example="01012016") @PathParam("param1") String param1, 
										@ApiParam(value="Fecha hasta", required=true, example="31122016") @PathParam("param2") String param2, @Context HttpServletRequest request) throws OAuthSystemException, OAuthProblemException {
		String token = getRequestToken(request);
		String error = "El filtro indicado es incorrecto.";

		if (ServerParams.FILTRO_FECHA_RANGO.equals(filter)){
			SimpleDateFormat formatter = new SimpleDateFormat("ddMMyyyy");
			try {
				Date fechaDesde = formatter.parse(param1);
				Date fechaHasta = formatter.parse(param2);
				return Response.status(200).entity(makeDebitosResponse(request, securityCodeStorage.getClaimResponses(token, fechaDesde, fechaHasta), token)).build();
			} catch (ParseException e) {
				error = "Formato de fecha incorrecto.";
			}
		}
		
		//Error response
		FhirContext ctx = FhirContext.forDstu2();
		IBaseOperationOutcome operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
		OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", error, null, "processing");
		
		String resultString;
		if (ServerParams.isXML(request.getContentType(), request.getHeader("Accept")))
			resultString = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(operationOutcome);
		else	
			resultString = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(operationOutcome);
		
		return Response.status(HttpServletResponse.SC_BAD_REQUEST).entity(resultString).build();
		
	}

	/*
	 * endpoint <base>/mais-fhir-conectathon/debitos/nro_factura/<nro>
	 * endpoint <base>/mais-fhir-conectathon/debitos/transaccion/<transaccionId>
	 * endpoint <base>/mais-fhir-conectathon/debitos/fecha_desde/<fecha_desde>
	 * endpoint <base>/mais-fhir-conectathon/debitos/fecha_hasta/<fecha_hasta>
	 */
	@GET
	@Path("/debitos/{filter}/{param1}")
	@Produces({MediaType.APPLICATION_JSON,MediaType.APPLICATION_XML})
	@ApiImplicitParams({
	    @ApiImplicitParam(name = "Authorization", value = "Access token obtenido en servicio /auth", required = true, dataType = "string", paramType = "header", example="Bearer token_value")
//	    @ApiImplicitParam(name = "Accept", value = "Encoding", required = false, dataType = "string", paramType = "header")
	})
    @ApiOperation(value = "Consulta de Débitos por Transacción o por Número de Factura")
    @ApiResponses(value = { 
            @ApiResponse(code = 200, message = "Consulta exitosa (Bundle)", reference="Bundle"),
            @ApiResponse(code = 401, message = "Error de autenticación (OperationOutcome)", reference="OperationOutcome"),
            @ApiResponse(code = 400, message = "Request inválido (OperationOutcome)", reference="OperationOutcome") })	
	public Response processDebito(	@ApiParam(value="Filtro", required=true, allowableValues=ServerParams.FILTRO_TRANSACCION+","+ServerParams.FILTRO_NRO_FACTURA) @PathParam("filter") String filter, 
									@ApiParam(value="ID Transacción / Número de factura", required=true) @PathParam("param1") String param1, @Context HttpServletRequest request) throws OAuthSystemException, OAuthProblemException {	
		String token = getRequestToken(request);
		String error = "El filtro indicado es incorrecto.";
		SimpleDateFormat formatter = new SimpleDateFormat("ddMMyyyy");
		
		switch (filter) {
//			case ServerParams.FILTRO_FECHA_DESDE:			
//				try {
//					Date fechaDesde = formatter.parse(param1);
//					Set<ClaimResponse> claimResponses = securityCodeStorage.getClaimResponsesDesde(token, fechaDesde);
//					return Response.status(200).entity(makeDebitosResponse(request, claimResponses, token)).build();
//				} catch (ParseException e) {
//					error = "Formato de fecha incorrecto.";
//					break;
//				}		
//			case ServerParams.FILTRO_FECHA_HASTA:
//				try {
//					Date fechaHasta = formatter.parse(param1);
//					Set<ClaimResponse> claimResponses = securityCodeStorage.getClaimResponsesHasta(token, fechaHasta);
//					return Response.status(200).entity(makeDebitosResponse(request, claimResponses, token)).build();
//				} catch (ParseException e) {
//					error = "Formato de fecha incorrecto.";
//					break;
//				}
			case ServerParams.FILTRO_TRANSACCION:
				return Response.status(200).entity(makeDebitosResponse(request, securityCodeStorage.getClaimResponsesTransaccion(token, Integer.valueOf(param1)), token)).build();
			case ServerParams.FILTRO_NRO_FACTURA:
				return Response.status(200).entity(makeDebitosResponse(request, securityCodeStorage.getClaimResponsesNroFactura(token, param1), token)).build();
		}
		
		//Error response
		FhirContext ctx = FhirContext.forDstu2();
		IBaseOperationOutcome operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
		OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", error, null, "processing");		
		
		String resultString;
		if (ServerParams.isXML(request.getContentType(), request.getHeader("Accept")))
			resultString = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(operationOutcome);
		else	
			resultString = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(operationOutcome);
		
		return Response.status(HttpServletResponse.SC_BAD_REQUEST).entity(resultString).build();
	}

	/*
	 * endpoint <base>/mais-fhir-conectathon/documento
	 */
	@POST
	@Path("/documento")
	@Consumes({MediaType.APPLICATION_JSON,MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON,MediaType.APPLICATION_XML})
	@ApiImplicitParams({
	    @ApiImplicitParam(name = "Authorization", value = "Access token obtenido en servicio /auth", required = true, dataType = "string", paramType = "header", example="Bearer token_value")
//	    @ApiImplicitParam(name = "Accept", value = "Encoding", required = false, dataType = "string", paramType = "header")
	})
    @ApiOperation(value = "Registración de documentos")
    @ApiResponses(value = { 
            @ApiResponse(code = 200, message = "Transacción exitosa (OperationOutcome)", reference="OperationOutcome"),
            @ApiResponse(code = 401, message = "Error de autenticación (OperationOutcome)", reference="OperationOutcome"),
            @ApiResponse(code = 400, message = "Bundle inválido (OperationOutcome)", reference="OperationOutcome") })
	public Response processDocument(String requestBundle, @Context HttpServletRequest request) throws OAuthSystemException, OAuthProblemException {
		String token = getRequestToken(request);		
		FhirContext ctx = FhirContext.forDstu2();
		boolean isXML = false;
		
		IParser parser;
		if (ServerParams.isXML(request.getContentType(), request.getHeader("Accept"))){
			parser = ctx.newXmlParser();
			isXML = true;
		}		
		else {
			parser = ctx.newJsonParser();
		}
		
		Bundle bundle = parser.parseResource(Bundle.class, requestBundle);
		
		ValidationResult validationResult = createFhirValidator(ctx).validateWithResult(bundle);
		IBaseOperationOutcome operationOutcome = validationResult.toOperationOutcome();
		int result = HttpServletResponse.SC_BAD_REQUEST;
		
		if (validationResult.isSuccessful()){
			boolean sinDocumentos = true;
			for(Entry entry: bundle.getEntry()) {				
				if (entry.getResource() instanceof Binary) {
					sinDocumentos = false;
				}
			}
			if (sinDocumentos){
				operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
				OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", "No se encontraron elementos del tipo Binary.", null, "processing");
			}
			else {
				Integer documentId = securityCodeStorage.addDocument(token);					
				operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
				OperationOutcomeUtil.addIssue(ctx, operationOutcome, "information", documentId.toString(), null, "documentId");
				result = HttpServletResponse.SC_ACCEPTED;
			}
		}
		
		String resultString;
		
		if (isXML)
			resultString = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(operationOutcome);
		else
			resultString = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(operationOutcome);
		
		return Response.status(result).entity(resultString).build();
	}
	
	
	private FhirValidator createFhirValidator(FhirContext ctx) {
		FhirValidator val = ctx.newValidator();
		val.setValidateAgainstStandardSchema(true);
		val.setValidateAgainstStandardSchematron(true);
		return val;
	}
	
	private String getRequestToken(HttpServletRequest request) throws OAuthSystemException, OAuthProblemException{
		OAuthAccessResourceRequest oauthRequest = new OAuthAccessResourceRequest((HttpServletRequest) request, ParameterStyle.HEADER);		
		return oauthRequest.getAccessToken();
	}

	private String makeDebitosResponse(HttpServletRequest request, Set<TransactionFacturacionEntity> transactions, String token) throws OAuthSystemException, OAuthProblemException{
	
		FhirContext ctx = FhirContext.forDstu2();
		Bundle bundle = new Bundle().setType(BundleTypeEnum.TRANSACTION);
		int claimCount = 0;
		
		//TODO modificar tomando organizaciones de transacciones 
		Organization financiador = securityCodeStorage.getFinanciera(token);
		Organization prestador = securityCodeStorage.getPrestador(token);
		if (prestador != null){
			Bundle.Entry entry = new Bundle.Entry().setResource(prestador).setFullUrl(ServerParams.FULLURL_PRESTADOR);
			bundle.addEntry(entry);
		}
		if (financiador != null){
			Bundle.Entry entry = new Bundle.Entry().setResource(financiador).setFullUrl(ServerParams.FULLURL_FINANCIADOR);
			bundle.addEntry(entry);
		}
		
		//Seteo status PROCESADO en caso de que no haya transacciones con estado PENDIENTE e INEXISTENTE en caso de que no haya transacciones
		Bundle.Entry entryStatus = new Bundle.Entry();
		entryStatus.setFullUrl(ServerParams.FULLURL_ESTADO);
		EntryResponse status = new EntryResponse();
		if (transactions.isEmpty())
			status.setStatus(ServerParams.StatusEnum.INEXISTENTE.toString());
		else {	
			for (TransactionFacturacionEntity transaccion : transactions){
				status.setStatus(transaccion.getStatus().toString());
				if (ServerParams.StatusEnum.PENDIENTE.equals(transaccion.getStatus())){
					//Si tiene transacciones pendientes seteo estado de consulta pendiente
					break;
				}
			}
		}			
		entryStatus.setResponse(status);		
		bundle.addEntry(entryStatus);
		
		for (TransactionFacturacionEntity transaccion : transactions){
			for (ClaimResponse claimResponse : transaccion.getClaimResponses()){
				//Si no es estado procesado o si tiene errores se muestra. 
				if (!ServerParams.StatusEnum.PROCESADO.equals(transaccion.getStatus()) || !claimResponse.getError().isEmpty()){
					Bundle.Entry entry = new Bundle.Entry().setResource(claimResponse).setFullUrl(ServerParams.FULLURL_DEBITOS + "/"+ ++claimCount);
					bundle.addEntry(entry);			
				}
			}
		}
		
		if (ServerParams.isXML(request.getContentType(), request.getHeader("Accept")))
			return ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle);
		else
			return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
	}
	
	private Error getError(String system, String code, String display){	
		Error error = new Error();
		CodingDt coding = new CodingDt();
		coding.setSystem(system);
		coding.setCode(code);
		coding.setDisplay(display);		
		error.setCode(coding);	
		
		return error;
	}
	
	private Integer processClaimRequest(Bundle bundle, String token) throws SinClaimsException, SinOrganizacionException{
		Set<ClaimResponse> claimResponses = new HashSet<ClaimResponse>();
		Organization prestador = null;
		Organization financiador = null;
		List<Error> errors;
		String nroFacturaOID = null;
		long cantidadClaims = 0;
		
		for(Entry entry: bundle.getEntry()) {				
			if (entry.getResource() instanceof Claim) {
				cantidadClaims++;
				errors = new ArrayList<Error>();
				Claim claim = (Claim)entry.getResource();	

				//Validación de Paciente
				boolean poseeAfiliado = false;
				for (IResource resource : claim.getContained().getContainedResources()){
					if (resource instanceof Patient){
						Patient p = (Patient) resource;
						for (IdentifierDt identifier : p.getIdentifier()){
							// IC de Afiliado
							if (ServerParams.OID_AFILIADO.equals(identifier.getSystem())){							
								poseeAfiliado = true;
								if (identifier.getValue() == null || identifier.getValue().length() != 11){
									//AFILIADO INEXISTENTE
									errors.add(getError(ServerParams.ERROR_TRANSACCION, ServerParams.ERROR_AFILIADO_ID.toString(), ServerParams.ERROR_AFILIADO));
								}
							}							
						}
					}
				}
				if (!poseeAfiliado){
					//AFILIADO NO ENVIADO EN REQUEST
					errors.add(getError(ServerParams.ERROR_TRANSACCION, ServerParams.ERROR_AFILIADO_ID.toString(), ServerParams.ERROR_AFILIADO));
				}

				//Validación de Práctica/Prestación
				boolean poseePrestacion = false;
				for (Item item : claim.getItem()){
					if (item.getService() != null && ServerParams.OID_SERVICE.equals(item.getService().getSystem())){
						poseePrestacion = true;
						if (item.getService().getCode() == null || item.getService().getCode().length() != 6){							
							//PRACTICA/PRESTACION INCORRECTA
							errors.add(getError(ServerParams.ERROR_TRANSACCION, ServerParams.ERROR_PRACTICA_ID.toString(), ServerParams.ERROR_PRACTICA));
						}
					}
				}
				if (!poseePrestacion){
					//PRESTACION NO ENVIADA EN REQUEST
					errors.add(getError(ServerParams.ERROR_TRANSACCION, ServerParams.ERROR_PRACTICA_ID.toString(), ServerParams.ERROR_PRACTICA));
				}
			
				//Creo un ClaimResponse para el débito				
				ClaimResponse cR = new ClaimResponse();
		
				ResourceReferenceDt resource = new ResourceReferenceDt();
				//Referencia necesaria para que luego imprima el tag Claim como contained
				resource.setReference("#"+claim.getId().getValue());			
				cR.setRequest(resource);
				cR.getContained().getContainedResources().add(claim);
			
				if (!errors.isEmpty()){
					cR.setError(errors);
				}
				//Seteo fecha de creación
				cR.setCreated(new DateTimeDt(new Date(), TemporalPrecisionEnum.DAY, TimeZone.getDefault()));
				
				claimResponses.add(cR);
				
				


			}
			else if (entry.getResource() instanceof Organization){
				Organization organization = (Organization) entry.getResource();
				if (ServerParams.FULLURL_PRESTADOR.equals(entry.getFullUrl())){
					//El primer identifier debe ser el OID MAIS de la Organizacion Prestadora
					if (organization.getIdentifier() != null && organization.getIdentifier().get(0) != null){
						nroFacturaOID = organization.getIdentifier().get(0).getValue() + "." + ServerParams.OID_NRO_FACTURA_ID; 
						prestador = organization;
					}
				}
				if (ServerParams.FULLURL_FINANCIADOR.equals(entry.getFullUrl())){
					//El primer identifier debe ser el OID MAIS de la Organizacion Financiadora
					if(organization.getIdentifier() != null && organization.getIdentifier().get(0) != null &&
						ServerParams.OID_FINANCIADOR.equals(organization.getIdentifier().get(0).getValue())){
						financiador = organization;
					}
				}	
			}
		}
		
		if (cantidadClaims == 0){
			//No se enviaron claims en request
			throw new SinClaimsException();
		}
		if (prestador == null || financiador == null){
			//No se envió organización en el request
			throw new SinOrganizacionException();
		}
		
		return securityCodeStorage.addClaimResponses(token, claimResponses, prestador, financiador, ServerParams.StatusEnum.PROCESADO);
	}
	
	private class SinClaimsException extends Throwable{
		
		private static final long serialVersionUID = 1L;
		
	}
	
	private class SinOrganizacionException extends Throwable{
		
		private static final long serialVersionUID = 1L;
		
	}
	
	@Deprecated //Dummy service
	@POST
	@Path("/facturaciondummypendiente")
	@Consumes({MediaType.APPLICATION_JSON,MediaType.APPLICATION_XML})
	@Produces({MediaType.APPLICATION_JSON,MediaType.APPLICATION_XML})
	@ApiImplicitParams({
	    @ApiImplicitParam(name = "Authorization", value = "Access token obtenido en servicio /auth", required = true, dataType = "string", paramType = "header", example="Bearer token_value")
//	    @ApiImplicitParam(name = "Accept", value = "Encoding", required = false, dataType = "string", paramType = "header")
	})
    @ApiOperation(value = "Registración de facturas pendientes(DUMMY)")
    @ApiResponses(value = { 
            @ApiResponse(code = 200, message = "Transacción exitosa (OperationOutcome)", reference="OperationOutcome"),
            @ApiResponse(code = 401, message = "Error de autenticación (OperationOutcome)", reference="OperationOutcome"),
            @ApiResponse(code = 400, message = "Bundle inválido (OperationOutcome)", reference="OperationOutcome") })
	public Response processClaimPendiente(String requestBundle, @Context HttpServletRequest request) throws OAuthSystemException, OAuthProblemException {

		String token = getRequestToken(request);		
		FhirContext ctx = FhirContext.forDstu2();
		IParser parser;
		
		if (ServerParams.isXML(request.getContentType(), request.getHeader("Accept"))){
			parser = ctx.newXmlParser();
		}		
		else {
			parser = ctx.newJsonParser();
		}

		Bundle bundle = parser.parseResource(Bundle.class, requestBundle);
		
		ValidationResult validationResult = createFhirValidator(ctx).validateWithResult(bundle);
		IBaseOperationOutcome operationOutcome = validationResult.toOperationOutcome();
		int result = HttpServletResponse.SC_BAD_REQUEST;
		boolean validBundle = false;
		
		if (validationResult.isSuccessful()){			
			//validar profile correspondiente	
			try {
				for (IdDt profile : (ArrayList<IdDt>) bundle.getResourceMetadata().get(ResourceMetadataKeyEnum.PROFILES)){
					if (profile.equals(ServerParams.FACTURACION_REQUEST_PROFILE)){
						validBundle = true;
						break;
					}
				}
			}
			catch(Throwable e){
				logger.error("Error obteniendo profiles del Bundle ", e);
			}
			if (!validBundle){
				operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
				OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", "No se encontró un perfil válido para el request.", null, "processing");				
			}
			else {
				
				try {
					result = HttpServletResponse.SC_OK;
					Integer transactionId = processClaimRequestPendiente(bundle, token);					
					operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
					OperationOutcomeUtil.addIssue(ctx, operationOutcome, "information", "0", null, "Trámite Generado");	
					((OperationOutcome) operationOutcome).setId(transactionId.toString());
					
				} catch (SinClaimsException e) {
					operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
					OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", "No se encontraron elementos del tipo Claim.", null, "processing");
				} catch (SinOrganizacionException e) {
					operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
					OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", "Organización incorrecta.", null, "processing");
				}
				
			}
		}
		
		String resultString = parser.setPrettyPrint(true).encodeResourceToString(operationOutcome);
		
		return Response.status(result).entity(resultString).build();
	}
	
	@Deprecated //Dummy
	private Integer processClaimRequestPendiente(Bundle bundle, String token) throws SinClaimsException, SinOrganizacionException{
		Set<ClaimResponse> claimResponses = new HashSet<ClaimResponse>();
		Organization prestador = null;
		Organization financiador = null;
		String nroFacturaOID = null;
		long cantidadClaims = 0;
		
		for(Entry entry: bundle.getEntry()) {				
			if (entry.getResource() instanceof Claim) {
				cantidadClaims++;
				Claim claim = (Claim)entry.getResource();				

					ClaimResponse cR = new ClaimResponse();
					
					ResourceReferenceDt resource = new ResourceReferenceDt();
					resource.setReference("#"+claim.getId().getValue());			
					cR.setRequest(resource);
					cR.getContained().getContainedResources().add(claim);					
					cR.setCreated(new DateTimeDt(new Date(), TemporalPrecisionEnum.DAY, TimeZone.getDefault()));
					claimResponses.add(cR);
					
			}
			else if (entry.getResource() instanceof Organization){
				Organization organization = (Organization) entry.getResource();
				if (ServerParams.FULLURL_PRESTADOR.equals(entry.getFullUrl())){
					//El primer identifier debe ser el OID MAIS de la Organizacion Prestadora
					if (organization.getIdentifier() != null && organization.getIdentifier().get(0) != null){
						nroFacturaOID = organization.getIdentifier().get(0).getValue() + "." + ServerParams.OID_NRO_FACTURA_ID; 
						prestador = organization;
					}
				}
				if (ServerParams.FULLURL_FINANCIADOR.equals(entry.getFullUrl())){
					//El primer identifier debe ser el OID MAIS de la Organizacion Financiadora
					if(organization.getIdentifier() != null && organization.getIdentifier().get(0) != null &&
						ServerParams.OID_FINANCIADOR.equals(organization.getIdentifier().get(0).getValue())){
						financiador = organization;
					}
				}	
			}
		}
		
		if (cantidadClaims == 0){
			//No se enviaron claims en request
			throw new SinClaimsException();
		}
		if (prestador == null || financiador == null){
			//No se envió organización en el request
			throw new SinOrganizacionException();
		}
		
		return securityCodeStorage.addClaimResponses(token, claimResponses, prestador, financiador, ServerParams.StatusEnum.PENDIENTE);
	}

}