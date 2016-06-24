package org.mais.fhir;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class CustomAuthResponse {	
	
	private String accessToken;    
    private String tokenValidUntil;  
    private String tokenType;
	
    public CustomAuthResponse(){}
    
    private CustomAuthResponse(Builder builder) {
    	this.accessToken = builder.accessToken;
    	this.tokenValidUntil = builder.tokenValidUntil;
    	this.tokenType = builder.tokenType;
	}

    @XmlElement(name="access_token")
	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}	

	@XmlElement(name="token_valid_until")
	public String getTokenValidUntil() {
		return tokenValidUntil;
	}

	public void setTokenValidUntil(String tokenValidUntil) {
		this.tokenValidUntil = tokenValidUntil;
	}

	@XmlElement(name="token_type")
	public String getTokenType() {
		return tokenType;
	}

	public void setTokenType(String tokenType) {
		this.tokenType = tokenType;
	}   
	
	public static class Builder {
        private String accessToken;
        private String tokenValidUntil;          
        private String tokenType;
        
        public Builder() {}

		public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }		 

        public Builder tokenValidUntil(String tokenValidUntil) {
            this.tokenValidUntil = tokenValidUntil;
            return this;
        }	
        
        public Builder tokenType(String tokenType) {
            this.tokenType = tokenType;
            return this;
        }	
        
        public CustomAuthResponse build() {
            return new CustomAuthResponse(this);
        }
	}


}