package swagger;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.mais.fhir.endpoint.ConectathonEndpoint;

import io.swagger.jaxrs.config.BeanConfig;

public class MAISApplication extends Application {
    HashSet<Object> singletons = new HashSet<Object>();

    public MAISApplication() {
        BeanConfig beanConfig = new BeanConfig();
        beanConfig.setVersion("1.0.2");
        beanConfig.setTitle("Documentación de API MAIS - PoC");
//        beanConfig.setSchemes(new String[]{"http"});
        beanConfig.setHost("host:port");
        beanConfig.setBasePath("mais-fhir-conectathon");
//        beanConfig.setFilterClass("swagger.ApiAuthorizationFilterImpl");
        beanConfig.setResourcePackage("org.mais.fhir.endpoint");
        beanConfig.setScan(true);
    }

    @Override
    public Set<Class<?>> getClasses() {
        HashSet<Class<?>> set = new HashSet<Class<?>>();

        set.add(ConectathonEndpoint.class);

        set.add(io.swagger.jaxrs.listing.ApiListingResource.class);
        set.add(io.swagger.jaxrs.listing.SwaggerSerializers.class);

        return set;
    }

    @Override
    public Set<Object> getSingletons() {
        return singletons;
    }
}
