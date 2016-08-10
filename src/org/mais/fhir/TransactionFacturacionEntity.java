package org.mais.fhir;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import ca.uhn.fhir.model.dstu2.resource.ClaimResponse;
import ca.uhn.fhir.model.dstu2.resource.Organization;

public class TransactionFacturacionEntity {

	private Set<ClaimResponse> claimResponses;
	private Organization prestador;
	private Organization financiera;
	private Integer transactionId;
	private ServerParams.StatusEnum status;
	private static AtomicInteger counter = new AtomicInteger(0);	 
	
    public TransactionFacturacionEntity() {}
   
    private TransactionFacturacionEntity(Builder builder) {
    	this.claimResponses = builder.claimResponses;
    	this.status = builder.status;
    	this.prestador = builder.prestador;
    	this.financiera = builder.financiera;
    	if (!builder.transientInsance)
    		this.transactionId = nextId();    	
	}
    
	public static int nextId() {
        return counter.incrementAndGet();     
    } 	

	public Set<ClaimResponse> getClaimResponses() {
		return claimResponses;
	}

	public ServerParams.StatusEnum getStatus() {
		return status;
	}
	
	public Organization getPrestador() {
		return prestador;
	}

	public Organization getFinanciera() {
		return financiera;
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
        private ServerParams.StatusEnum status;
        private Organization prestador;
        private Organization financiera;
        private boolean transientInsance = false;
        
        public Builder() {}

		public Builder claimResponses(Set<ClaimResponse> claimResponses) {
            this.claimResponses = claimResponses;
            return this;
        }

		public Builder status(ServerParams.StatusEnum status) {
            this.status = status;
            return this;
        }
				
		public Builder prestador(Organization prestador) {
            this.prestador = prestador;
            return this;
        }

		public Builder financiera(Organization financiera) {
            this.financiera = financiera;
            return this;
        }

		public Builder transientInsance(boolean transientInsance) {
            this.transientInsance = transientInsance;
            return this;
        }
		
        public TransactionFacturacionEntity build() {
            return new TransactionFacturacionEntity(this);
        }
	}
}
