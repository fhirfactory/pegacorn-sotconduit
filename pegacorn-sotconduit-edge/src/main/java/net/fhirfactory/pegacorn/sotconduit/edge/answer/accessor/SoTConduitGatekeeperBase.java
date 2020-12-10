package net.fhirfactory.pegacorn.sotconduit.edge.answer.accessor;

import ca.uhn.fhir.parser.IParser;
import net.fhirfactory.pegacorn.common.model.FDN;
import net.fhirfactory.pegacorn.common.model.RDN;
import net.fhirfactory.pegacorn.datasets.fhir.r4.base.entities.resource.SecurityLabelFactory;
import net.fhirfactory.pegacorn.datasets.fhir.r4.internal.topics.FHIRElementTopicIDBuilder;
import net.fhirfactory.pegacorn.deployment.topology.manager.DeploymentTopologyIM;
import net.fhirfactory.pegacorn.ladon.model.virtualdb.businesskey.VirtualDBKeyManagement;
import net.fhirfactory.pegacorn.ladon.model.virtualdb.operations.VirtualDBActionTypeEnum;
import net.fhirfactory.pegacorn.petasos.audit.model.PetasosParcelAuditTrailEntry;
import net.fhirfactory.pegacorn.petasos.model.processingplant.DefaultWorkshopSetEnum;
import net.fhirfactory.pegacorn.petasos.model.processingplant.ProcessingPlantServicesInterface;
import net.fhirfactory.pegacorn.petasos.model.topology.NodeElement;
import net.fhirfactory.pegacorn.petasos.model.topology.NodeElementFunctionToken;
import net.fhirfactory.pegacorn.petasos.model.topology.NodeElementIdentifier;
import net.fhirfactory.pegacorn.petasos.model.topology.NodeElementTypeEnum;
import net.fhirfactory.pegacorn.petasos.model.wup.WUPIdentifier;
import net.fhirfactory.pegacorn.petasos.model.wup.WUPJobCard;
import net.fhirfactory.pegacorn.sotconduit.audit.SoTConduitActionEnum;
import net.fhirfactory.pegacorn.sotconduit.audit.SoTConduitAuditEntryManager;
import net.fhirfactory.pegacorn.util.FHIRContextUtility;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.Serializable;
import java.sql.Date;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class SoTConduitGatekeeperBase {

    private NodeElementFunctionToken accessorFunctionToken;
    private WUPIdentifier accessorIdentifier;
    private String accessorName;
    private WUPJobCard accessorJobCard;
    private NodeElement node;
    private String version;
    private boolean isInitialised;

    private IParser parserR4;

    public SoTConduitGatekeeperBase() {
        isInitialised = false;
        this.accessorName = specifyAccessorResourceTypeName();
        this.version = specifyAccessorResourceTypeVersion();
    }

    abstract protected String specifyAccessorResourceTypeName();
    abstract protected String specifyAccessorResourceTypeVersion();
    abstract protected Logger getLogger();
    abstract protected List<Identifier> resolveIdentifierList(Resource resource);
    abstract protected void addResourceSecurityLabels(Resource resource);
    abstract public Bundle findResourceViaIdentifier(Identifier identifier);

    protected String getResourceTypeName(){return(specifyAccessorResourceTypeName());}
    protected String getResourceTypeVersion(){return(specifyAccessorResourceTypeVersion());}

    @Inject
    private DeploymentTopologyIM topologyProxy;

    @Inject
    private FHIRElementTopicIDBuilder topicIDBuilder;

    @Inject
    private ProcessingPlantServicesInterface processingPlant;

    @Inject
    private SoTConduitAuditEntryManager auditEntryManager;

    @Inject
    private VirtualDBKeyManagement virtualDBKeyManagement;

    @Inject
    private FHIRContextUtility fhirContextUtility;

    @Inject
    private SecurityLabelFactory securityLabelFactory;

    @PostConstruct
    protected void initialise() {
        getLogger().debug(".initialise(): Entry");
        if (!isInitialised) {
            getLogger().trace(".initialise(): AccessBase is NOT initialised");
            this.parserR4 = fhirContextUtility.getJsonParser();
            this.isInitialised = true;
            processingPlant.initialisePlant();
            this.node = specifyNode();
            this.accessorFunctionToken = this.node.getNodeFunctionToken();
            this.accessorIdentifier = new WUPIdentifier(this.node.getNodeInstanceID());
        }
    }

    public void initialiseServices() {
        initialise();
    }


    protected PetasosParcelAuditTrailEntry beginSearchTransaction(Map<Property, Serializable> parameterSet, SoTConduitActionEnum action){
        String searchSummary = "Search Criteria(";
        Set<Property> propertySet = parameterSet.keySet();
        if(propertySet.isEmpty()){
            searchSummary = searchSummary + "empty)";
        } else {
            int remainingElements = propertySet.size();
            for(Property property: propertySet){
                searchSummary = searchSummary + property.getName() + "-->" + parameterSet.get(property).toString();
                if(remainingElements > 1){
                    searchSummary = searchSummary + ",";
                }
            }
            searchSummary = searchSummary + ")";
        }
        PetasosParcelAuditTrailEntry parcelEntry = auditEntryManager.beginTransaction(searchSummary, getResourceTypeName(), null, action, this.accessorIdentifier, this.version );
        return(parcelEntry);
    }

    protected PetasosParcelAuditTrailEntry beginTransaction(IdType id, Resource fhirResource, SoTConduitActionEnum action){
        String resourceKey = id.asStringValue();
        PetasosParcelAuditTrailEntry parcelEntry = auditEntryManager.beginTransaction(resourceKey, getResourceTypeName(),  fhirResource, action, this.accessorIdentifier, this.version );
        return(parcelEntry);
    }

    protected PetasosParcelAuditTrailEntry beginTransaction(Identifier resourceIdentifier, Resource fhirResource, SoTConduitActionEnum action){
        String resourceKey = virtualDBKeyManagement.generatePrintableInformationFromIdentifier(resourceIdentifier);
        PetasosParcelAuditTrailEntry parcelEntry = auditEntryManager.beginTransaction(resourceKey, getResourceTypeName(), fhirResource, action, this.accessorIdentifier, this.version );
        return(parcelEntry);
    }

    protected void endTransaction(Identifier resourceIdentifier, Resource fhirResource, SoTConduitActionEnum action, boolean success, PetasosParcelAuditTrailEntry startingTransaction){
        String resourceKey = resourceIdentifier.toString();
        auditEntryManager.endTransaction(resourceKey, getResourceTypeName(),fhirResource,action,success,startingTransaction,this.accessorIdentifier, this.version);
    }

    protected void endSearchTransaction(Bundle resultSet, int returnedResourceCount, SoTConduitActionEnum action, boolean success, PetasosParcelAuditTrailEntry startingTransaction){
        String searchAnswerCount = buildSearchResultString(resultSet);
        auditEntryManager.endTransaction(searchAnswerCount, getResourceTypeName(), null,action,success,startingTransaction,this.accessorIdentifier, this.version);
    }

    protected void endTransaction(IdType id, Resource fhirResource, SoTConduitActionEnum action, boolean success, PetasosParcelAuditTrailEntry startingTransaction){
        String resourceKey = id.asStringValue();
        auditEntryManager.endTransaction(resourceKey, getResourceTypeName(), fhirResource,action,success,startingTransaction,this.accessorIdentifier, this.version);
    }

    /**
     * This function builds the Deployment Topology node (a WUP) for the
     * Accessor.
     * <p>
     * It uses the Name (specifyAccessorName()) defined in the subclass as part
     * of the Identifier and then registers with the Topology Services.
     *
     * @return The NodeElement representing the WUP which this code-set is
     * fulfilling.
     */
    private NodeElement specifyNode() {
        getLogger().debug(".specifyNode(): Entry");
        NodeElementIdentifier nodeInstance = processingPlant.getProcessingPlantNodeId();
        getLogger().info(".specifyNode(): retrieved ProcessingPlant Identifier --> {}", nodeInstance);
        if (nodeInstance == null) {
            getLogger().error(".specifyNode(): Oh No!");
        }
        FDN workshopFDN = new FDN(nodeInstance);
        workshopFDN.appendRDN(new RDN(NodeElementTypeEnum.WORKSHOP.getNodeElementType(),  DefaultWorkshopSetEnum.EDGE_WORKSHOP.getWorkshop()));
        NodeElementIdentifier workshopId = new NodeElementIdentifier(workshopFDN.getToken());
        getLogger().trace(".specifyNode(): Retrieving Workshop Node");
        NodeElement workshopNode = topologyProxy.getNode(workshopId);
        getLogger().trace(".specifyNode(): workshop node (NodeElement) --> {}", workshopNode);
        FDN accessorInstanceFDN = new FDN(workshopFDN);
        accessorInstanceFDN.appendRDN(new RDN(NodeElementTypeEnum.WUP.getNodeElementType(), "Accessor-" + specifyAccessorResourceTypeName()));
        NodeElementIdentifier accessorInstanceIdentifier = new NodeElementIdentifier(accessorInstanceFDN.getToken());
        getLogger().trace(".specifyNode(): Now construct the Work Unit Processing Node");
        NodeElement accessor = new NodeElement();
        getLogger().trace(".specifyNode(): Constructing WUP Node, Setting Version Number");
        accessor.setVersion(this.version);
        getLogger().trace(".specifyNode(): Constructing WUP Node, Setting Node Instance");
        accessor.setNodeInstanceID(workshopId);
        getLogger().trace(".specifyNode(): Constructing WUP Node, Setting Concurrency Mode");
        accessor.setConcurrencyMode(workshopNode.getConcurrencyMode());
        getLogger().trace(".specifyNode(): Constructing WUP Node, Setting Resillience Mode");
        accessor.setResilienceMode(workshopNode.getResilienceMode());
        getLogger().trace(".specifyNode(): Constructing WUP Node, Setting inPlace Status");
        accessor.setInstanceInPlace(true);
        getLogger().trace(".specifyNode(): Constructing WUP Node, Setting Containing Element Identifier");
        accessor.setContainingElementID(workshopNode.getNodeInstanceID());
        getLogger().trace(".specifyNode(): Now registering the Node");
        topologyProxy.registerNode(accessor);
        getLogger().info(".specifyNode(): Exit, accessorInstanceIdentifier (NodeElementIdentifier) --> {}", accessorInstanceIdentifier);
        return (accessor);
    }
    private String buildSearchResultString(Bundle searchResult){
        if(searchResult == null) {
            return("Search Failed");
        }
        int resultCount = searchResult.getTotal();
        if(resultCount == 0){
            return("Search Succeeded: Result Count = 0");
        }
        String resultString = "Search Succeeded: Result Count = " + resultCount + ": Entries --> ";
        for(Bundle.BundleEntryComponent currentBundleEntry: searchResult.getEntry()){
            Resource currentResource = currentBundleEntry.getResource();
            if(currentResource.hasId()){
                resultString = resultString + currentResource.getId();
            } else {
                resultString = resultString + "[Resource Has No Id]";
            }
            if(resultCount > 1) {
                resultString = resultString + ", ";
            }
            resultCount -= 1;
        }
        return(resultString);
    }

    public NodeElementFunctionToken getAccessorFunctionToken() {
        return accessorFunctionToken;
    }

    public WUPIdentifier getAccessorIdentifier() {
        return accessorIdentifier;
    }

    public String getAccessorName() {
        return accessorName;
    }

    public WUPJobCard getAccessorJobCard() {
        return accessorJobCard;
    }

    public NodeElement getNode() {
        return node;
    }

    public String getVersion() {
        return version;
    }

    public boolean isInitialised() {
        return isInitialised;
    }

    public IParser getParserR4() {
        return parserR4;
    }

    protected SecurityLabelFactory getSecurityLabelFactory(){
        return(securityLabelFactory);
    }

    protected Bundle wrapIntoBundle(Resource resource) {
        Bundle.BundleEntryComponent bundleEntry = new Bundle.BundleEntryComponent();
        bundleEntry.setResource(resource);
        Bundle.BundleEntrySearchComponent searchComponent = new Bundle.BundleEntrySearchComponent();
        searchComponent.setMode(Bundle.SearchEntryMode.MATCH);
        searchComponent.setScore(1);
        bundleEntry.setSearch(searchComponent);
        //
        // Now let's build the Bundle!!!!
        //
        Bundle fhirBundle = new Bundle();
        fhirBundle.addEntry(bundleEntry);
        fhirBundle.setType(Bundle.BundleType.SEARCHSET);
        fhirBundle.setTimestamp(Date.from(Instant.now()));
        return(fhirBundle);
    }
}
