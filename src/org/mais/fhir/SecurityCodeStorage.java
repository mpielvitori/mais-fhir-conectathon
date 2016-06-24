package org.mais.fhir;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.context.ApplicationScoped;

import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu2.resource.Claim;
import ca.uhn.fhir.model.dstu2.resource.ClaimResponse;

/**
 * Simula un storage con scope app
 */
@ApplicationScoped
public class SecurityCodeStorage {	

	public SecurityCodeStorage() {
		super();
	}

//	private Set<String> tokens = new HashSet<String>();
//	private Set<String> authCodes = new HashSet<String>();
	private Map<String, Set<TransactionFacturacionEntity>> userTransactions = new HashMap<String, Set<TransactionFacturacionEntity>>();
	private Map<String, Set<Integer>> userDocuments = new HashMap<String, Set<Integer>>();
	private static AtomicInteger documentCounter = new AtomicInteger(0);
	
	public static int nextDocumentId() {
        return documentCounter.incrementAndGet();     
    } 
	
	public void addToken(String token) {
//		tokens.add(token);
		userTransactions.put(token, new HashSet<TransactionFacturacionEntity>());
	}

//	public void addAuthCode(String token) {
//		authCodes.add(token);
//	}

	public boolean isValidToken(String token) {
		return userTransactions.keySet().contains(token);
	}

//	public boolean isValidAuthCode(String authCode) {
//		return authCodes.contains(authCode);
//	}

	public Integer addClaimResponses(String token, Set<ClaimResponse> claims){
		TransactionFacturacionEntity transaction = new TransactionFacturacionEntity.Builder().claimResponses(claims).build();
		
		userTransactions.get(token).add(transaction);
		
		return transaction.getTransactionId();
	}
	
	public Integer addDocument(String token){		
		Integer documentId = nextDocumentId();		
		if (userDocuments.get(token) == null){
			userDocuments.put(token, new HashSet<Integer>());
		}
		userDocuments.get(token).add(documentId);
		
		return documentId;
	}
	
	public Set<ClaimResponse> getClaimResponses(String token){
		Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
			claims.addAll(transactionClaim.getClaimResponses());
		}		
		return claims;
	}
	
	public Set<ClaimResponse> getAllClaimResponses(){
		Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
		//Itero todos los tokens
		for (String token : userTransactions.keySet()){
			//Itero todas las transacciones por cada token
			for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
				claims.addAll(transactionClaim.getClaimResponses());
			}		
		}
		
		return claims;
	}	
	
	public Set<ClaimResponse> getClaimResponses(String token, Date fechaDesde, Date fechaHasta){
		Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
			for (ClaimResponse claimResponse : transactionClaim.getClaimResponses()){
				if (fechaDesde.compareTo(claimResponse.getCreated()) <= 0 && fechaHasta.compareTo(claimResponse.getCreated()) >= 0){				
					claims.add(claimResponse);					
				}
			}			
		}		
		return claims;
	}

	public Set<ClaimResponse> getClaimResponsesDesde(String token, Date fechaDesde){
		Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
			for (ClaimResponse claimResponse : transactionClaim.getClaimResponses()){
				if (fechaDesde.compareTo(claimResponse.getCreated()) <= 0){				
					claims.add(claimResponse);					
				}
			}			
		}		
		return claims;
	}
	
	public Set<ClaimResponse> getClaimResponsesHasta(String token, Date fechaHasta){
		Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
			for (ClaimResponse claimResponse : transactionClaim.getClaimResponses()){
				if (fechaHasta.compareTo(claimResponse.getCreated()) >= 0){				
					claims.add(claimResponse);					
				}
			}			
		}		
		return claims;
	}

	public Set<ClaimResponse> getClaimResponsesTransaccion(String token, Integer transaccionId){
		Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
			if (transactionClaim.getTransactionId().intValue() == transaccionId)
				claims.addAll(transactionClaim.getClaimResponses());
		}		
		return claims;
	}

	public Set<ClaimResponse> getClaimResponsesNroFactura(String token, String nroFactura){
		Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
			for (ClaimResponse claimResponse : transactionClaim.getClaimResponses()){
				String nroFacturaOID = claimResponse.getOrganization().getDisplay().getValue() + ServerParams.OID_NRO_FACTURA_ID;
				for (IdentifierDt identifier : claimResponse.getIdentifier()){
					if (nroFacturaOID.equals(identifier.getSystem())){
						if (nroFactura.equals(identifier.getValue())){
							claims.add(claimResponse);
							break;
						}
					}							
				}
			}			
		}		
		return claims;
	}
	
	/* NRO_FACTURA EN CLAIM
	 * public Set<ClaimResponse> getClaimResponsesNroFactura(String token, String nroFactura){
		Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
			for (ClaimResponse claimResponse : transactionClaim.getClaimResponses()){				
				for (IResource resource : claimResponse.getContained().getContainedResources()){
					if (resource instanceof Claim){
						Claim claim = (Claim) resource;
						for (IdentifierDt identifier : claim.getIdentifier()){
							if (ServerParams.OID_NRO_FACTURA.equals(identifier.getSystem())){
								if (nroFactura.equals(identifier.getValue())){
									claims.add(claimResponse);
									break;
								}
							}							
						}
					}
				}	
			}			
		}		
		return claims;
	}*/

}