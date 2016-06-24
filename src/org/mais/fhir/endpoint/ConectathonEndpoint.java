package org.mais.fhir.endpoint;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.ContainedDt;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Binary;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Bundle.Entry;
import ca.uhn.fhir.model.dstu2.resource.Claim;
import ca.uhn.fhir.model.dstu2.resource.Claim.Item;
import ca.uhn.fhir.model.dstu2.resource.ClaimResponse;
import ca.uhn.fhir.model.dstu2.resource.ClaimResponse.Error;
import ca.uhn.fhir.model.dstu2.resource.Organization;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.dstu2.valueset.BundleTypeEnum;
import ca.uhn.fhir.model.primitive.DateTimeDt;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.OperationOutcomeUtil;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;

@Path("/")
public class ConectathonEndpoint {	
	
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ConectathonEndpoint.class);	
	
	@Inject
	private SecurityCodeStorage securityCodeStorage;
	
	private long inicio;
	
	/*
	 * endpoint <base>/mais-fhir-conectathon/facturacion
	 * endpoint <base>/mais-fhir-conectathon/facturacion?inputFormat=json&outputFormat=xml
	 */
	@POST
	@Path("/facturacion")
	public Response processClaim(@QueryParam("inputFormat") String inputFormat, @QueryParam("outputFormat") String outputFormat, String requestBundle, @Context HttpServletRequest request) throws OAuthSystemException, OAuthProblemException {
		inicio = System.currentTimeMillis();
		logger.info("Inicio " + inicio + " milliseconds");

		String token = getRequestToken(request);		
		FhirContext ctx = FhirContext.forDstu2();
		IParser parser;
		if (ServerParams.JSON.equals(inputFormat)){
			parser = ctx.newJsonParser();
		}
		else {
			parser = ctx.newXmlParser();
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
				
				Set<ClaimResponse> claimResponses;
				try {
					claimResponses = processClaimRequest(bundle);
					result = HttpServletResponse.SC_ACCEPTED;
					Integer transactionId = securityCodeStorage.addClaimResponses(token, claimResponses);					
					operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
					OperationOutcomeUtil.addIssue(ctx, operationOutcome, "information", transactionId.toString(), null, "transactionId");	
					
				} catch (SinClaimsException e) {
					operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
					OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", "No se encontraron elementos del tipo Claim.", null, "processing");
				} catch (SinOrganizacionException e) {
					operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
					OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", "Organización incorrecta.", null, "processing");
				}
				
			}
		}
		

		
		//Si outputFormat es JSON o XML se instancia un parser para armar la respuesta. Caso contrario devuelve con inputFormat(por default XML)
		if (ServerParams.XML.equals(outputFormat)){
			parser = ctx.newXmlParser();
		} else if (ServerParams.JSON.equals(outputFormat)){
			parser = ctx.newJsonParser();
		}
		
		String resultString = parser.setPrettyPrint(true).encodeResourceToString(operationOutcome);
		
		return Response.status(result).entity(resultString).build();
	}
	
	/*
	 * endpoint <base>/mais-fhir-conectathon/debitos/fecha_rango/<fecha_desde>/<fecha_hasta>
	 */
	@GET
	@Path("/debitos/{filter}/{param1}/{param2}")
	public Response processDebitoRango(@PathParam("filter") String filter, @PathParam("param1") String param1, 
										@PathParam("param2") String param2, @Context HttpServletRequest request) throws OAuthSystemException, OAuthProblemException {
		String token = getRequestToken(request);
		String error = "El filtro indicado es incorrecto.";

		if (ServerParams.FILTRO_FECHA_RANGO.equals(filter)){
			SimpleDateFormat formatter = new SimpleDateFormat("ddMMyyyy");
			try {
				Date fechaDesde = formatter.parse(param1);
				Date fechaHasta = formatter.parse(param2);
				Set<ClaimResponse> claimResponses = securityCodeStorage.getClaimResponses(token, fechaDesde, fechaHasta);
				return Response.status(200).entity(makeDebitosResponse(claimResponses)).build();
			} catch (ParseException e) {
				error = "Formato de fecha incorrecto.";
			}
		}
		
		//Error response
		FhirContext ctx = FhirContext.forDstu2();
		IBaseOperationOutcome operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
		OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", error, null, "processing");		
		String resultString = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(operationOutcome);					
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
	public Response processDebito(@PathParam("filter") String filter, @PathParam("param1") String param1, @Context HttpServletRequest request) throws OAuthSystemException, OAuthProblemException {	
		String token = getRequestToken(request);
		String error = "El filtro indicado es incorrecto.";
		SimpleDateFormat formatter = new SimpleDateFormat("ddMMyyyy");
		
		switch (filter) {
			case ServerParams.FILTRO_FECHA_DESDE:			
				try {
					Date fechaDesde = formatter.parse(param1);
					Set<ClaimResponse> claimResponses = securityCodeStorage.getClaimResponsesDesde(token, fechaDesde);
					return Response.status(200).entity(makeDebitosResponse(claimResponses)).build();
				} catch (ParseException e) {
					error = "Formato de fecha incorrecto.";
					break;
				}		
			case ServerParams.FILTRO_FECHA_HASTA:
				try {
					Date fechaHasta = formatter.parse(param1);
					Set<ClaimResponse> claimResponses = securityCodeStorage.getClaimResponsesHasta(token, fechaHasta);
					return Response.status(200).entity(makeDebitosResponse(claimResponses)).build();
				} catch (ParseException e) {
					error = "Formato de fecha incorrecto.";
					break;
				}
			case ServerParams.FILTRO_TRANSACCION:
				return Response.status(200).entity(makeDebitosResponse(securityCodeStorage.getClaimResponsesTransaccion(token, Integer.valueOf(param1)))).build();
			case ServerParams.FILTRO_NRO_FACTURA:
				return Response.status(200).entity(makeDebitosResponse(securityCodeStorage.getClaimResponsesNroFactura(token, param1))).build();
		}
		
		//Error response
		FhirContext ctx = FhirContext.forDstu2();
		IBaseOperationOutcome operationOutcome = (IBaseOperationOutcome) ctx.getResourceDefinition("OperationOutcome").newInstance();
		OperationOutcomeUtil.addIssue(ctx, operationOutcome, "error", error, null, "processing");		
		String resultString = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(operationOutcome);					
		return Response.status(HttpServletResponse.SC_BAD_REQUEST).entity(resultString).build();
	}

	/*
	 * endpoint <base>/mais-fhir-conectathon/documento
	 */
	@POST
	@Path("/documento")
	public Response processDocument(String requestBundle, @Context HttpServletRequest request) throws OAuthSystemException, OAuthProblemException {
		String token = getRequestToken(request);		
		FhirContext ctx = FhirContext.forDstu2();
		
		IParser parser = ctx.newXmlParser();
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
			
		String resultString = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(operationOutcome);
		
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

	private String makeDebitosResponse(Set<ClaimResponse> claimResponses) throws OAuthSystemException, OAuthProblemException{
	
		FhirContext ctx = FhirContext.forDstu2();
		Bundle bundle = new Bundle().setType(BundleTypeEnum.TRANSACTION);
		int claimCount = 0;

		for (ClaimResponse claimResponse : claimResponses){
			Bundle.Entry entry = new Bundle.Entry().setResource(claimResponse).setFullUrl("http://mais.org.ar/fhir/ImplementationGuide/TransaccionDebitoMais/"+ ++claimCount);
			bundle.addEntry(entry);			
		}
		
		String resultString = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle);
		
		return resultString;
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
	
	private Set<ClaimResponse> processClaimRequest(Bundle bundle) throws SinClaimsException, SinOrganizacionException{
		Set<ClaimResponse> claimResponses = new HashSet<ClaimResponse>();
		List<Error> errors;
		boolean organizationOk = false;
		boolean debitar;
		String organizationOID = null;
		String nroFacturaOID = null;
		long cantidadClaims = 0;
		
		for(Entry entry: bundle.getEntry()) {				
			if (entry.getResource() instanceof Claim) {
				cantidadClaims++;
				errors = new ArrayList<Error>();
				debitar = false;
				ClaimResponse cR = new ClaimResponse();
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
									debitar = true;
								}
							}							
						}
					}
				}
				if (!poseeAfiliado){
					//AFILIADO NO ENVIADO EN REQUEST
					errors.add(getError(ServerParams.ERROR_TRANSACCION, ServerParams.ERROR_AFILIADO_ID.toString(), ServerParams.ERROR_AFILIADO));
					debitar = true;
				}

				//Validación de Práctica/Prestación
				boolean poseePrestacion = false;
				for (Item item : claim.getItem()){
					if (item.getService() != null && ServerParams.OID_SERVICE.equals(item.getService().getSystem())){
						poseePrestacion = true;
						if (item.getService().getCode() == null || item.getService().getCode().length() != 6){							
							//PRACTICA/PRESTACION INCORRECTA
							errors.add(getError(ServerParams.ERROR_TRANSACCION, ServerParams.ERROR_PRACTICA_ID.toString(), ServerParams.ERROR_PRACTICA));
							debitar = true;
						}
					}
				}
				if (!poseePrestacion){
					//PRESTACION NO ENVIADA EN REQUEST
					errors.add(getError(ServerParams.ERROR_TRANSACCION, ServerParams.ERROR_PRACTICA_ID.toString(), ServerParams.ERROR_PRACTICA));
					debitar = true;
				}
				
				if (debitar){
					cR.setCreated(new DateTimeDt(new Date(), TemporalPrecisionEnum.DAY, TimeZone.getDefault()));
					//Seteo el nro de factura 
					for (IdentifierDt identifier : claim.getIdentifier()){
						if (nroFacturaOID != null && nroFacturaOID.equals(identifier.getSystem())){
							IdentifierDt oidFactura = new IdentifierDt();
							oidFactura.setSystem(identifier.getSystem());
							oidFactura.setValue(identifier.getValue());
							cR.getIdentifier().add(oidFactura);
						}							
					}					
					if (!errors.isEmpty()){
						cR.setError(errors);
					}
					ResourceReferenceDt resource = new ResourceReferenceDt();
					//Referencia necesaria para que luego imprima el tag Claim como contained
					resource.setReference("#"+claim.getId().getValue());			
					cR.setRequest(resource);
					cR.getContained().getContainedResources().add(claim);
					ResourceReferenceDt organization = new ResourceReferenceDt();
					organization.setDisplay(organizationOID);
					cR.setOrganization(organization);
					claimResponses.add(cR);
				}
			}
			else if (entry.getResource() instanceof Organization){
				Organization organization = (Organization) entry.getResource();
				if (organizationOID == null){
					if (organization.getIdentifier() != null && organization.getIdentifier().get(0) != null){
						organizationOID = organization.getIdentifier().get(0).getValue();
						nroFacturaOID = organizationOID + ServerParams.OID_NRO_FACTURA_ID; 
					}				
					else {
						//En caso de que la primera organización no tenga Identifier
						organizationOID = "";
						nroFacturaOID = "";
					}
				}
				if (organization.getIdentifier() != null && organization.getIdentifier().get(0) != null &&
						ServerParams.OID_FINANCIERA.equals(organization.getIdentifier().get(0).getValue())){
					organizationOk = true;
				}
			}
		}
		long fin = System.currentTimeMillis();
		logger.info("Se procesaron " + cantidadClaims + " claims en " + (fin - inicio) + " milliseconds");
		
		if (cantidadClaims == 0){
			//No se enviaron claims en request
			throw new SinClaimsException();
		}
		if (!organizationOk){
			//No se envió organización en el request
			throw new SinOrganizacionException();
		}

		return claimResponses;
	}
	
	private class SinClaimsException extends Throwable{
		
		private static final long serialVersionUID = 1L;
		
	}
	
	private class SinOrganizacionException extends Throwable{
		
		private static final long serialVersionUID = 1L;
		
	}
	
	private Response makeXMLResponse(Bundle bundle){
		FhirContext ctx = FhirContext.forDstu2();
		String resultStringXML = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle);
		
		return Response.status(200).entity(resultStringXML).build();
	}

	private Response makeJSONResponse(Bundle bundle){
		FhirContext ctx = FhirContext.forDstu2();
		String resultStringJSON = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		logger.warn("Result String JSON "+resultStringJSON);
		return Response.status(200).entity(resultStringJSON).build();
	}
	
	@Deprecated
	@GET
	@Path("/fromFile")
	@Produces({MediaType.APPLICATION_JSON})
	public Response getFileResponse(@QueryParam("file") String file, @QueryParam("format") String format, @QueryParam("cantidad") String cantidad) throws FileNotFoundException{
		try {
//			FileReader inputFile = new FileReader("/home/martin/git/mais-conectathon/extras/MAISFacturacionRequest.xml");
			FileReader inputFile = new FileReader("/home/martin/git/mais-conectathon/extras/" + file);
			FhirContext ctx = FhirContext.forDstu2();
			Bundle bundle;
			int cant = cantidad == null || cantidad.equals("X") ? 1 : Integer.valueOf(cantidad);
			
			if (ServerParams.XML.equals(format)){
				IParser parser = ctx.newXmlParser();
				bundle = parser.parseResource(Bundle.class, inputFile);
			} else {
				IParser parser = ctx.newJsonParser();
				bundle = parser.parseResource(Bundle.class, inputFile);				
			}
			
			if ("X".equals(cantidad)){
				int claimCount = 0;
				for(Entry entry: bundle.getEntry()) {				
					if (entry.getResource() instanceof Claim) {
						entry.setFullUrl("Claim"+ (++claimCount));
					}
				}					
				return makeJSONResponse(bundle);				
			}
			else if (ServerParams.XML.equals(format)){
				return makeXMLResponse(bundle);	
			}
			else {				
				for(Entry entry: bundle.getEntry()) {				
					if (entry.getResource() instanceof Claim) {
						for (int i = 0; i < cant; i++) {					
							bundle.addEntry(entry);						
						}
						break;
					}
				}					
				return makeJSONResponse(bundle);
			}
		
		} catch (FileNotFoundException e) {
			logger.error("ERROR getFileResponse ", e);
			throw e;
		}		
		
	}
	
	@Deprecated
	@GET
	@Path("/dummy")
	public Response getDummyResponse(){
		FhirContext ctx = FhirContext.forDstu2();
		Bundle bundle = new Bundle().setType(BundleTypeEnum.SEARCH_RESULTS);

		//ClaimResponse 1
		ClaimResponse cR = new ClaimResponse();
		cR.setId("ClaimResponse/dummyResponse");
		cR.setOrganization(new ResourceReferenceDt("ORGANIZATION_ID"));
		Claim claim = new Claim();
		IdDt id = new IdDt();
		id.setValue("valor");
		claim.setId(id);
		List<IResource> theContainedResources = new ArrayList<IResource>();
		theContainedResources.add(claim);

		Patient patient = new Patient();
		patient.setActive(true);
		theContainedResources.add(patient);
		ContainedDt contained = new ContainedDt();
		contained.setContainedResources(theContainedResources);
		cR.setContained(contained);
		ResourceReferenceDt resource = new ResourceReferenceDt();
		resource.setReference("#valor");
		cR.setRequest(resource);
		//cR.getContained().setContainedResources(theContainedResources);
		//cR.getContained().getContainedResources().add(claim);
		Bundle.Entry entry = new Bundle.Entry().setFullUrl("http://mais.org.ar/fhir/ImplementationGuide/TransaccionFacturacionMais/1").setResource(cR).setFullUrl("urn:uuid:1");
		bundle.addEntry(entry);

		//ClaimResponse 2
//		ClaimResponse cR2 = new ClaimResponse();
//		cR2.setId("ClaimResponse/dummyResponse");
//		cR2.setOrganization(new ResourceReferenceDt("ORGANIZATION_ID"));
		Bundle.Entry entry2 = new Bundle.Entry().setFullUrl("http://mais.org.ar/fhir/ImplementationGuide/TransaccionFacturacionMais/2").setResource(claim).setFullUrl("urn:uuid:2");
		bundle.addEntry(entry2);
		
		String resultString = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle);
		
		return Response.status(200).entity(resultString).build();
	}
	
//	@Deprecated 
//	private String getMAISResponse(){
//		return "<?xml version='1.0' encoding='UTF-8'?> <Bundle xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:schemaLocation='http://hl7.org/fhir fhir-all-xsd/bundle.xsd' xmlns='http://hl7.org/fhir'> <meta> <profile value='http://mais.org.ar/fhir/ImplementationGuide/TransaccionDebitoMais'/> </meta> <!-- Basado en FHIR DSTU 2 --> <!-- Actualizado con esquemas de Setiembre 2015 Según los requerimientos de la reunión 9 de MAIS D01-PARA CADA ITEM DEBITADO D01a- FECHA DE DEBITO D01b- NRO. DE FACTURA ORIGINAL D01c- IDENTIFICADOR DE ITEM/SUBITEM D01d- FECHA DE PRESTACION D01e- DATOS DEL AFILIADO : NRO. AFILIADO D01f- NRO. DE ACCESSION O SERVICIO INTERNO D01g- CODIGO PRESENTADO D01h- CODIGO LIQUIDADO D01i- MOTIVO DE DEBITO Codificado D01j- MOTIVO DE DEBITO Texto Libre D01k- IMPORTE DEBITO->paymentAdjustment D01l- ERROR D02 - ORGANIZACION PRESTADORA D03 - ORGANIZACION FINANCIADORA --> <type value='transaction'> </type> <!-- Identificador de la organizacion prestadora --> <entry> <resource> <Organization> <!-- Identificador Interno dentro de la Transaccion --> <id value='F87E4DC7-BEFA-45D1-B336-09BE61B1AA3E'/> <!-- Identificador MAIS de la Organización Prestadora --> <identifier> <value value='OID_ORGANIZACION_PRESTADORA'/> </identifier> </Organization> </resource> <request> <method value='POST'/> <url value='Organization'/> </request> </entry> <entry> <resource> <Organization> <!-- Identificador Interno dentro de la Transaccion --> <id value='C81696FE-2B56-4F77-BCED-440A727D2869'/> <!-- Identificador MAIS de la Organización Financiadora --> <identifier> <value value='OID_ORGANIZACION_FINANCIADORA'/> </identifier> </Organization> </resource> <request> <method value='POST'/> <url value='Organization'/> </request> </entry> <!-- Puede haber varias repeticiones de este recurso, tantas como 'renglones' de lo que antes eran nuestros archivos de intercambio Este es el primer renglon --> <entry> <resource> <ClaimResponse> <!-- Opción 1 para Claim: recursos incluidos dentro del principal Opción 2 para Claim: si el sistema que envía el Claim guarda la referencia generada por el financiador, directamente el url de la referencia. Discutir --> <contained> <Claim> <id value='Claim1'></id> <!-- Tipo de Item --> <type value='institutional'/> <!-- Identificador Único del Renglón para el Prestador D01c --> <identifier> <system value='OID_PRESTADOR/items_factura'/> <value value='20910-2'/> </identifier> <!-- Identificador : Tipo y Numero de Factura del Prestador - D01b--> <identifier> <system value='OID_PRESTADOR/nros_factura'/> <value value='B 0000-00000000'/> </identifier> <!-- Identificador : Accession del Prestador - D01f --> <identifier> <system value='OID_PRESTADOR/accessions'/> <value value='H0493823'/> </identifier> <!-- Fecha de Prestación - D01d --> <created value='2015-10-15'></created> <!-- Referencia a datos de paciente --> <patient><reference value='Pat20425239'></reference></patient> </Claim> </contained> <!-- Datos del paciente --> <contained> <Patient> <!-- Identificador interno dentro de la transacción --> <id value='Pat20425239'/> <!-- Nro de Afiliado - D01e --> <identifier> <system value='www.prepaga.com/afiliados'/> <value value='NRO_AFILIADO'/> </identifier> </Patient> </contained> <!-- Referencia a Item de Factura Debitado --> <request> <reference value='#Claim1'></reference> </request> <!-- Fecha del Débito: D01a --> <created value='2016-02-10'></created> <!-- Referencia a Organizacion Financiadora --> <organization> <reference value='organization/C81696FE-2B56-4F77-BCED-440A727D2869'></reference> </organization> <!-- Referencia a organizacion Prestadora --> <requestOrganization> <reference value='organization/F87E4DC7-BEFA-45D1-B336-09BE61B1AA3E'></reference> </requestOrganization> <!-- Resultado del Procesamiento --> <outcome value='error'/> <!-- Mensaje de Error o Debito para el item --> <disposition value='MENSAJE DE ERROR O DEBITO'></disposition> <!-- Referencia al Codigo de Facturación --> <item> <sequenceLinkId value='1'></sequenceLinkId> <!-- Codigo del Financiador para la Prestacion D01h --> <adjudication> <code> <system value='OID_FINANCIADOR/prestaciones'/> <code value='490475'/> <display value='HEMOGRAMA'/> </code> </adjudication> </item> <!-- Solo para Errores de Procesamiento del batch de Facturacion --> <!-- Definicion de codigo de Errores en la Guia - D01l --> <error> <code> <system value='http://mais.org.ar/fhir/ValueSet/ErroresTransacciones'></system> <code value='A00'></code> <display value='FALTA NUMERO DE AUTORIZACION'></display> </code> </error> <!-- Valor del Débito en Pesos - D01k --> <paymentAdjustment> <value value='120.33'/> <unit value='ARS'></unit> </paymentAdjustment> <!-- Motivo del Debito - D01i/D01j --> <paymentAdjustmentReason> <system value='http://mais.org.ar/fhir/ValueSet/MotivosDebito'></system> <code value='A01'></code> <display value='NO CORRESPONDE AYUDANTE'></display> </paymentAdjustmentReason> </ClaimResponse> </resource> <request> <method value='POST'/> <url value='ClaimResponse'/> </request> </entry> </Bundle>";
//	}	
}