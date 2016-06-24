package org.mais.fhir;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import ca.uhn.fhir.model.dstu2.resource.ClaimResponse;

public class TransactionFacturacionEntity {

	private Set<ClaimResponse> claimResponses;
	private Integer transactionId;
	private static AtomicInteger counter = new AtomicInteger(0);	 
	
    public TransactionFacturacionEntity() {}
   
    private TransactionFacturacionEntity(Builder builder) {
    	this.claimResponses = builder.claimResponses;
    	this.transactionId = nextId();
	}
    
	public static int nextId() {
        return counter.incrementAndGet();     
    } 	

	public Set<ClaimResponse> getClaimResponses() {
		return claimResponses;
	}
	public void setClaimResponses(Set<ClaimResponse> claimResponses) {
		this.claimResponses = claimResponses;
	}
	public Integer getTransactionId() {
		return transactionId;
	}
	public void setTransactionId(Integer transactionId) {
		this.transactionId = transactionId;
	}

	public static class Builder {
        private Set<ClaimResponse> claimResponses;        
        
        public Builder() {}

		public Builder claimResponses(Set<ClaimResponse> claimResponses) {
            this.claimResponses = claimResponses;
            return this;
        }
		
        public TransactionFacturacionEntity build() {
            return new TransactionFacturacionEntity(this);
        }
	}
}
