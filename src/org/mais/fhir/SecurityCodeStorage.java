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
import ca.uhn.fhir.model.dstu2.resource.Organization;

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

	public Integer addClaimResponses(String token, Set<ClaimResponse> claims, Organization prestador, Organization financiera, ServerParams.StatusEnum status){
		TransactionFacturacionEntity transaction = new TransactionFacturacionEntity.Builder().claimResponses(claims).prestador(prestador).financiera(financiera).status(status).build();
		
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
	
	public Set<TransactionFacturacionEntity> getClaimResponses(String token, Date fechaDesde, Date fechaHasta){
		Set<TransactionFacturacionEntity> transactions = new HashSet<TransactionFacturacionEntity>();
		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
			//Si es pendiente no tiene fecha de creación y no lo agrego porque no tengo contra que comparar
//			if (ServerParams.StatusEnum.PENDIENTE.getValue() == transactionClaim.getStatus().getValue()){
//				continue;
//			}	
			boolean agrego = false;
			//Creo un transaction sólo con los ClaimResponse de las factura que coincidan con el rango buscado
			Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
			for (ClaimResponse claimResponse : transactionClaim.getClaimResponses()){
				if (fechaDesde.compareTo(claimResponse.getCreated()) <= 0 && fechaHasta.compareTo(claimResponse.getCreated()) >= 0){				
					claims.add(claimResponse);
					agrego = true;
				}
			}
			//Compruebo si agregó un claim para el rango consultado
			if (agrego){
				TransactionFacturacionEntity transactionFactura = new TransactionFacturacionEntity.Builder().claimResponses(claims).prestador(transactionClaim.getPrestador()).financiera(transactionClaim.getFinanciera()).status(transactionClaim.getStatus()).transientInsance(true).build();
				transactionFactura.setTransactionId(transactionClaim.getTransactionId());
				transactions.add(transactionFactura);
			}
		}		
		return transactions;
	}
	
//	public Set<ClaimResponse> getClaimResponsesDesde(String token, Date fechaDesde){
//		Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
//		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
//			for (ClaimResponse claimResponse : transactionClaim.getClaimResponses()){
//				if (fechaDesde.compareTo(claimResponse.getCreated()) <= 0){				
//					claims.add(claimResponse);					
//				}
//			}			
//		}		
//		return claims;
//	}
//	
//	public Set<ClaimResponse> getClaimResponsesHasta(String token, Date fechaHasta){
//		Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
//		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
//			for (ClaimResponse claimResponse : transactionClaim.getClaimResponses()){
//				if (fechaHasta.compareTo(claimResponse.getCreated()) >= 0){				
//					claims.add(claimResponse);					
//				}
//			}			
//		}		
//		return claims;
//	}

	public Set<TransactionFacturacionEntity> getClaimResponsesTransaccion(String token, Integer transaccionId){
		Set<TransactionFacturacionEntity> transactions = new HashSet<TransactionFacturacionEntity>();
		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
			if (transactionClaim.getTransactionId().intValue() == transaccionId)
				transactions.add(transactionClaim);
		}		
		return transactions;
	}
	
	public Set<TransactionFacturacionEntity> getClaimResponsesNroFactura(String token, String nroFactura){
		Set<TransactionFacturacionEntity> transactions = new HashSet<TransactionFacturacionEntity>();
		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
			for (ClaimResponse claimResponse : transactionClaim.getClaimResponses()){
				String nroFacturaOID = transactionClaim.getPrestador().getIdentifier().get(0).getValue() + "." + ServerParams.OID_NRO_FACTURA_ID;
				//Nro de factura dentro de Claim
				for (IResource resource : claimResponse.getContained().getContainedResources()){
					if (resource instanceof Claim){
						Claim claim = (Claim) resource;
						for (IdentifierDt identifier : claim.getIdentifier()){
							if (nroFacturaOID.equals(identifier.getSystem())){
								if (nroFactura != null && nroFactura.equals(identifier.getValue())){
									//Creo un transaction sólo con el ClaimResponse de la factura buscada
									Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
									claims.add(claimResponse);
									TransactionFacturacionEntity transactionFactura = new TransactionFacturacionEntity.Builder().claimResponses(claims).prestador(transactionClaim.getPrestador()).financiera(transactionClaim.getFinanciera()).status(transactionClaim.getStatus()).transientInsance(true).build();
									transactionFactura.setTransactionId(transactionClaim.getTransactionId());
									transactions.add(transactionFactura);
									break;
								}
							}							
						}
					}
				}	
				//Nro de factura dentro de Claim Response(para esto hay que crear un IdentifierDt al agregar los ClaimResponse)				
//				for (IdentifierDt identifier : claimResponse.getIdentifier()){
//					if (nroFacturaOID.equals(identifier.getSystem())){
//						if (nroFactura.equals(identifier.getValue())){
//							//Creo un transaction sólo con el ClaimResponse de la factura buscada
//							Set<ClaimResponse> claims = new HashSet<ClaimResponse>();
//							claims.add(claimResponse);
//							TransactionFacturacionEntity transactionFactura = new TransactionFacturacionEntity.Builder().claimResponses(claims).prestador(transactionClaim.getPrestador()).financiera(transactionClaim.getFinanciera()).status(transactionClaim.getStatus()).build();
//							transactions.add(transactionFactura);
//							break;
//						}
//					}							
//				}
			}			
		}		
		return transactions;
	}

	//Tomo Organización de primer request enviado con el token
	//Reemplazar con valor seteado al autenticar Prestador
	@Deprecated
	public Organization getPrestador(String token){
		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
			return transactionClaim.getPrestador();
		}
		return null;
	}

	//Tomo Organización de primer request enviado con el token
	@Deprecated
	public Organization getFinanciera(String token){
		for (TransactionFacturacionEntity transactionClaim : userTransactions.get(token)){
			return transactionClaim.getFinanciera();
		}
		return null;
	}


}