package org.mais.fhir;

import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;

import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.ClaimResponse;

public class ServerParams {

	//Content types & encodings
	public static final String XML = "xml";
	public static final String JSON = "json";
	public static final String APPLICATION_XML_FHIR = "application/xml+fhir";
	public static final String APPLICATION_JSON_FHIR = "application/json+fhir";
	
	public static final String CLIENT_ID = "oauth2_client_id";
	public static final String CLIENT_SECRET = "oauth2_client_secret";
	
	public static final String OAUTH_SERVER_URL = "http://localhost:8080/mais-fhir-conectathon/";
	public static final String RESOURCE_SERVER_NAME = "mais";
	
	//TODO pasar a Organization
	//new ResourceReferenceDt("ORGANIZATION_ID");
	public static final String OID_FINANCIADOR = "2.16.840.1.113883.2.10.24.2.2.6";
	public static final String OID_AFILIADO = "2.16.840.1.113883.2.10.24.2.2.6.7";
	public static final String OID_DOCUMENTO_AFILIADO = "2.16.840.1.113883.2.10.24.4.1";
	public static final String OID_SERVICE = "2.16.840.1.113883.2.10.24.2.2.6.12"; // PRESTACION / PRACTICA
	//public static final String OID_NRO_FACTURA = "2.16.840.1.113883.2.10.24.2.1.1.10";
	public static final String OID_NRO_FACTURA_ID = "10";
	
	public static final String FACTURACION_REQUEST_PROFILE = "http://mais.org.ar/fhir/ImplementationGuide/TransaccionFacturacionMais";	
	public static final String FULLURL_DEBITOS = "http://mais.org.ar/fhir/ImplementationGuide/TransaccionDebitoMais";
	public static final String FULLURL_FINANCIADOR = "http://mais.org.ar/fhir/TransaccionMais/Financiador";
	public static final String FULLURL_PRESTADOR = "http://mais.org.ar/fhir/TransaccionMais/Prestador";
	public static final String FULLURL_ESTADO = "http://mais.org.ar/fhir/ValueSet/EstadoTramites";			
	public static final String ERROR_TRANSACCION = "http://mais.org.ar/fhir/ValueSet/ErroresTransacciones";
	public static final String ERROR_TRANSACCION_ID = "A00";
	
	public static final String ERROR_PRACTICA = "Práctica no convenida u homologada";
	public static final Integer ERROR_PRACTICA_ID = 32;
	public static final String ERROR_AFILIADO = "No es afiliado o paciente dado de baja";
	public static final Integer ERROR_AFILIADO_ID = 40;
	
	// Códigos de estado
	public enum StatusEnum {
		PROCESADO(0), PENDIENTE(1), INEXISTENTE(2);
		
		private int value;
		
		private StatusEnum(int value){
			this.value = value;
		}	
 
		public int getValue() {
			return this.value;
		}

		@Override
		public String toString() {
			return String.valueOf(this.value);
		}	
		
	}

	//Filtros
	public static final String FILTRO_FECHA_RANGO = "fecha_rango";	
	public static final String FILTRO_NRO_FACTURA = "nro_factura";
	public static final String FILTRO_TRANSACCION = "transaccion";
	public static final String FILTRO_FECHA_DESDE = "fecha_desde";
	public static final String FILTRO_FECHA_HASTA = "fecha_hasta";
	
	//Valores DUMMY
	public static final String AFILIADO_OK = "60795606402";
	public static final String AFILIADO_BAJA = "61765432101";	
	public static final String PRACTICA_OK = "490475";
	
	public static boolean isXML(String contentType, String accept){
		return MediaType.APPLICATION_XML.equals(contentType) || APPLICATION_XML_FHIR.equals(contentType) || MediaType.APPLICATION_XML.equals(accept) || APPLICATION_XML_FHIR.equals(accept); 
	}

}
