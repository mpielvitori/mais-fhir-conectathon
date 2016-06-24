package org.mais.fhir;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ErrorResponse {	
	
	private String error;    
    private String errorDescription;  
	
    public ErrorResponse(){}
    
    private ErrorResponse(Builder builder) {
    	this.error = builder.error;
    	this.errorDescription = builder.errorDescription;
	}

    @XmlElement(name="error")
	public String getError() {
		return error;
	}

	@XmlElement(name="error_description")
	public String getErrorDescription() {
		return errorDescription;
	}
	
	public void setError(String error) {
		this.error = error;
	}

	public void setErrorDescription(String errorDescription) {
		this.errorDescription = errorDescription;
	}

	public static class Builder {
        private String error;
        private String errorDescription;          
        
        public Builder() {}

		public Builder error(String error) {
            this.error = error;
            return this;
        }		 

        public Builder errorDescription(String errorDescription) {
            this.errorDescription = errorDescription;
            return this;
        }	
        
        public ErrorResponse build() {
            return new ErrorResponse(this);
        }
	}


}