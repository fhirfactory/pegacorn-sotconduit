package net.fhirfactory.pegacorn.sotconduit.edge.answer.proxies;

import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import net.fhirfactory.pegacorn.sotconduit.processingplant.SoTConduitProcessingPlantBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

public abstract class ResourceSoTProxyBase implements IResourceProvider {
    boolean isInitialised;

    public ResourceSoTProxyBase(){
        isInitialised = false;
    }

    @Inject
    private SoTConduitProcessingPlantBase processingPlant;

    @PostConstruct
    private void initialisePatientProxy(){
        if(!this.isInitialised()) {
            getLogger().info("LadonEdgeProxyBase::initialiseProxy(): Entry, Initialising Services");
            getProcessingPlant().initialisePlant();
            this.setInitialised(true);
            getLogger().debug("LadonEdgeProxyBase::initialiseProxy(): Exit");
        }
    }

    abstract protected Logger getLogger();
    abstract protected ResourceType specifyResourceType();

    public boolean isInitialised() {
        return isInitialised;
    }
    public void setInitialised(boolean initialised) {
        isInitialised = initialised;
    }

    protected SoTConduitProcessingPlantBase getProcessingPlant(){
        return(processingPlant);
    }

    //
    // Main Proxy Methods
    //
    abstract public Resource getResource(IdType id);
    abstract public Bundle getResourceViaIdentifier(@RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam identifierParam);
}
