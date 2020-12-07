package net.fhirfactory.pegacorn.sotconduit.edge.answer;

import ca.uhn.fhir.rest.server.RestfulServer;
import net.fhirfactory.pegacorn.deployment.properties.SystemWideProperties;
import org.slf4j.Logger;

import javax.inject.Inject;

public abstract class SoTConduitEdgeAnswerServlet extends RestfulServer {


    private static final long serialVersionUID = 1L;

    @Inject
    SystemWideProperties systemWideProperties;


}
