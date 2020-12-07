/*
 * Copyright (c) 2020 Mark A. Hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.fhirfactory.pegacorn.sotconduit.audit;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import net.fhirfactory.pegacorn.common.model.FDN;
import net.fhirfactory.pegacorn.common.model.RDN;
import net.fhirfactory.pegacorn.datasets.fhir.r4.internal.topics.FHIRElementTopicIDBuilder;
import net.fhirfactory.pegacorn.petasos.audit.model.PetasosParcelAuditTrailEntry;
import net.fhirfactory.pegacorn.petasos.core.sta.brokers.PetasosSTAServicesAuditOnlyBroker;
import net.fhirfactory.pegacorn.petasos.model.topics.TopicToken;
import net.fhirfactory.pegacorn.petasos.model.topics.TopicTypeEnum;
import net.fhirfactory.pegacorn.petasos.model.uow.UoW;
import net.fhirfactory.pegacorn.petasos.model.uow.UoWPayload;
import net.fhirfactory.pegacorn.petasos.model.uow.UoWProcessingOutcomeEnum;
import net.fhirfactory.pegacorn.petasos.model.wup.WUPIdentifier;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class SoTConduitAuditEntryManager {
    private static final Logger LOG = LoggerFactory.getLogger(SoTConduitAuditEntryManager.class);

    private static final String FHIR_VERSION = "4.0.1";

    @Inject
    private net.fhirfactory.pegacorn.util.FHIRContextUtility FHIRContextUtility;

    private IParser parserR4;

    @Inject
    private FHIRElementTopicIDBuilder topicIDBuilder;

    @Inject
    private PetasosSTAServicesAuditOnlyBroker servicesBroker;

    @PostConstruct
    protected void initialise() {
        LOG.debug(".initialise(): Entry");
        FhirContext newContext = FhirContext.forR4();
        this.parserR4 = newContext.newJsonParser();
        LOG.debug(".initialise(): Exit");
    }

    public PetasosParcelAuditTrailEntry beginTransaction(String auditStringPayload, String resourceType, Resource fhirResource, SoTConduitActionEnum action, WUPIdentifier wupInstance, String version) {
        LOG.debug(".beginTransaction(): Entry, auditEntryString --> {}, fhriResource --> {}, action --> {}", auditStringPayload, fhirResource, action);
        LOG.trace(".beginTransaction(): Create the UoW for accessor utilisation");
        UoWPayload payload = new UoWPayload();
        boolean encodingFailure = false;
        String errorString = "";
        String auditTrailPayload = "";
        switch (action) {
            case REVIEW:
                LOG.trace(".endTransaction(): Review/Get --> Logging the request");
                auditTrailPayload = "Action: Get --> ";
                break;
            case CREATE:
                LOG.trace(".endTransaction(): Create --> Logging the request");
                auditTrailPayload = "Action: Create --> ";
                break;
            case DELETE:
                LOG.trace(".endTransaction(): Delete --> Logging the request");
                auditTrailPayload = "Action: Delete --> ";
                break;
            case UPDATE:
                LOG.trace(".endTransaction(): Update --> Logging the request");
                auditTrailPayload = "Action: Update --> ";
                break;
        }
        if(fhirResource != null) {
            LOG.trace(".beginTransaction(): Converting FHIR element into a (JSON) String");
            String resourceAsString = null;
            try {
                LOG.trace(".beginTransaction(): Using IParser --> {}", parserR4);
                resourceAsString = parserR4.encodeResourceToString(fhirResource);
                LOG.trace(".beginTransaction(): Add JSON String (encoded FHIR element) to the UoWPayload");
                String fullPayloadString = auditTrailPayload + resourceAsString;
                payload.setPayload(fullPayloadString);
                LOG.trace(".beginTransaction(): Construct a TopicToken to describe the payload & add it to the Payload");
                TopicToken payloadToken = topicIDBuilder.createTopicToken(resourceType, FHIR_VERSION);
                payload.setPayloadTopicID(payloadToken);
            } catch (Exception Ex) {
                LOG.error(".beginTransaction(): Failed to Encode --> {}", Ex.toString());
                errorString = Ex.toString();
                encodingFailure = true;
            }
        } else {
            String fullPayloadString = auditTrailPayload + auditStringPayload;
            payload.setPayload(fullPayloadString);
            LOG.trace(".beginTransaction(): Construct a TopicToken to describe the payload & add it to the Payload");
            TopicToken payloadToken = topicIDBuilder.createTopicToken(resourceType, FHIR_VERSION);
            payload.setPayloadTopicID(payloadToken);
        }
        UoW theUoW;
        if (encodingFailure) {
            LOG.trace(".beginTransaction(): Failed to encode incoming content....");
            payload.setPayload("Error encoding content --> " + errorString);
            FDN payloadTopicFDN = new FDN();
            payloadTopicFDN.appendRDN(new RDN(TopicTypeEnum.DATASET_DEFINER.getTopicType(), "AETHER"));
            payloadTopicFDN.appendRDN(new RDN(TopicTypeEnum.DATASET_CATEGORY.getTopicType(), "DataTypes"));
            payloadTopicFDN.appendRDN(new RDN(TopicTypeEnum.DATASET_SUBCATEGORY.getTopicType(), "Error"));
            payloadTopicFDN.appendRDN(new RDN(TopicTypeEnum.DATASET_RESOURCE.getTopicType(), "JSONConversionErrorMessage"));
            TopicToken newToken = new TopicToken();
            newToken.setIdentifier(payloadTopicFDN.getToken());
            newToken.setVersion("1.0.0");
            payload.setPayloadTopicID(newToken);
            LOG.trace(".beginTransaction(): Create the UoW with the fhirResource/TopicToken as the Ingres Payload");
            theUoW = new UoW(payload);
            theUoW.setProcessingOutcome(UoWProcessingOutcomeEnum.UOW_OUTCOME_FAILED);
        } else {
            LOG.trace(".beginTransaction(): Instantiate the UoW with the fhirResource/TopicToken as the Ingres Payload");
            theUoW = new UoW(payload);
            theUoW.setProcessingOutcome(UoWProcessingOutcomeEnum.UOW_OUTCOME_NOTSTARTED);
        }
        PetasosParcelAuditTrailEntry currentTransaction = servicesBroker.transactionAuditEntry(wupInstance, action.toString(), theUoW, null);
        LOG.debug(".beginTransaction(): Exit --> Registration aftermath: currentTransaction (PetasosParcelAuditTrailEntry) --> {}", currentTransaction);
        return (currentTransaction);
    }

    public void endTransaction(String auditEntryString, String resourceType, Resource fhirResource, SoTConduitActionEnum action, boolean success, PetasosParcelAuditTrailEntry startingTransaction, WUPIdentifier wupInstance, String version) {
        LOG.debug(".endTransaction(): Entry");
        UoW updatedUoW = startingTransaction.getActualUoW();
        String auditTrailPayload = null;
        if (success) {
            switch (action) {
                case REVIEW:
                    LOG.trace(".endTransaction(): Successful Review/Get --> Logging the outcome");
                    auditTrailPayload = "Action: Get, Result --> ";
                    break;
                case UPDATE:
                    LOG.trace(".endTransaction(): Successful Update --> Logging the outcome");
                    auditTrailPayload = "Action: Update, Result --> ";
                    break;
                case CREATE:
                    LOG.trace(".endTransaction(): Successful Create --> Logging the outcome");
                    auditTrailPayload = "Action: Create, Result --> ";
                    break;
                case DELETE:
                    LOG.trace(".endTransaction(): Successful Delete --> Logging the outcome");
                    auditTrailPayload = "Action: Delete, Result --> ";
                    break;
                case SEARCH:
                    LOG.trace(".endTransaction(): Successful Delete --> Logging the outcome");
                    auditTrailPayload = "Action: Search, Result --> ";
                    break;
            }
            if(fhirResource != null) {
                LOG.trace(".endTransaction(): fhirResource.type --> {}", fhirResource.getResourceType());
                if(parserR4 == null) {LOG.error("Warning Will Robinson!!!!");}
                auditTrailPayload = auditTrailPayload  + parserR4.encodeResourceToString(fhirResource);
            } else {
                auditTrailPayload = auditTrailPayload + auditEntryString;
            }
            UoWPayload newPayload = new UoWPayload();
            newPayload.setPayload(auditTrailPayload);
            TopicToken payloadToken = topicIDBuilder.createTopicToken(resourceType, version);
            newPayload.setPayloadTopicID(payloadToken);
            updatedUoW.getEgressContent().addPayloadElement(newPayload);
            updatedUoW.setProcessingOutcome(UoWProcessingOutcomeEnum.UOW_OUTCOME_SUCCESS);
            LOG.trace(".endTransaction(): Calling the Audit Trail Generator ");
            PetasosParcelAuditTrailEntry currentTransaction = servicesBroker.transactionAuditEntry(wupInstance, action.toString(), updatedUoW, startingTransaction);
        } else {
            updatedUoW.setProcessingOutcome(UoWProcessingOutcomeEnum.UOW_OUTCOME_FAILED);
            LOG.trace(".endTransaction(): Calling the Audit Trail Generator ");
            PetasosParcelAuditTrailEntry currentTransaction = servicesBroker.transactionAuditEntry(wupInstance, action.toString(), updatedUoW, startingTransaction);
        }
        LOG.debug(".endTransaction(): exit, my work is done!");
    }
}
