import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.security.SecureRandomIdentifierGenerationStrategy;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;
import net.shibboleth.utilities.java.support.xml.ParserPool;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.xml.security.exceptions.Base64DecodingException;
import org.apache.xml.security.utils.Base64;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncoder;
import org.opensaml.messaging.handler.MessageHandlerException;
import org.opensaml.profile.action.AbstractProfileAction;
import org.opensaml.profile.action.MessageEncoderFactory;
import org.opensaml.profile.action.impl.DecodeMessage;
import org.opensaml.profile.action.impl.EncodeMessage;
import org.opensaml.profile.context.ProfileRequestContext;
import org.opensaml.saml.common.SAMLException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.SAMLObjectBuilder;
import org.opensaml.saml.common.binding.SAMLBindingSupport;
import org.opensaml.saml.common.binding.impl.SAMLOutboundDestinationHandler;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.profile.impl.AddAudienceRestrictionToAssertions;
import org.opensaml.saml.common.profile.impl.AddInResponseToToResponse;
import org.opensaml.saml.common.profile.impl.AddNotBeforeConditionToAssertions;
import org.opensaml.saml.common.profile.impl.AddNotOnOrAfterConditionToAssertions;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPPostDecoder;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPPostEncoder;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.Endpoint;
import org.opensaml.saml.saml2.profile.AbstractSAML2NameIDGenerator;
import org.opensaml.saml.saml2.profile.SAML2ActionSupport;
import org.opensaml.saml.saml2.profile.impl.AddNameIDToSubjects;
import org.opensaml.saml.saml2.profile.impl.AddStatusResponseShell;
import org.opensaml.saml.saml2.profile.impl.AddSubjectConfirmationToSubjects;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;


/**
 * User:    Xin Meng
 * Date:    07/05/17
 * Project: opensamlv3demo
 */
public class SAMLToolkit {

    public static SAMLObject convertBase64ToSaml(String base64Str) {
        byte[] decodedBytes = new byte[0];
        try {
            decodedBytes = Base64.decode(base64Str);
        } catch (Base64DecodingException e) {
            e.printStackTrace();
            return null;
        }

        InputStream is = new ByteArrayInputStream(decodedBytes);
        //is = new InflaterInputStream(is, new Inflater(true));
        try {

            InitializationService.initialize();
            Document messageDoc;
            BasicParserPool basicParserPool = new BasicParserPool();
            basicParserPool.initialize();
            messageDoc = basicParserPool.parse(is);
            Element messageElem = messageDoc.getDocumentElement();
            Unmarshaller unmarshaller = XMLObjectProviderRegistrySupport.getUnmarshallerFactory().getUnmarshaller(messageElem);

            assert unmarshaller != null;
            return (SAMLObject) unmarshaller.unmarshall(messageElem);
        } catch (InitializationException e) {
            e.printStackTrace();
            return null;
        } catch (XMLParserException e) {
            e.printStackTrace();
            return null;
        } catch (UnmarshallingException e) {
            e.printStackTrace();
            return null;
        } catch (ComponentInitializationException e) {
            e.printStackTrace();
            return null;
        }
    }
    private static VelocityEngine velocityEngine;

    private static final String NAME_QUALIFIER = "https://idp.example.org";

    private static class BasicSAML2NameIDGenerator extends AbstractSAML2NameIDGenerator {

        private final String identifier;
        public BasicSAML2NameIDGenerator(@Nonnull final String id) {
            setId("test");
            setDefaultIdPNameQualifierLookupStrategy(new Function<ProfileRequestContext,String>() {
                @Override
                public String apply(ProfileRequestContext input) {
                    return NAME_QUALIFIER;
                }
            });
            identifier = id;
        }

        /** {@inheritDoc} */
        @Override
        protected String getIdentifier(ProfileRequestContext profileRequestContext) throws SAMLException {
            return identifier;
        }
    }

    //final response with SAMLResponse
    //private static HttpServletResponse httpServletResponse;

    public static HttpServletResponse getResponseBySamlRequest(HttpServletRequest httpServletRequest) {

        //get SAML Request by the httpServletRequest

        //we try to use the opensaml v3 new feature to decode the saml request
        try {
            InitializationService.initialize();
        } catch (InitializationException e) {
            e.printStackTrace();
        }
        HTTPPostDecoder messageDecoder = new HTTPPostDecoder();
        ParserPool parserPool = XMLObjectProviderRegistrySupport.getParserPool();
        if (parserPool == null) {
            // TODO: Add error page redirect.
            return null;
        }
        messageDecoder.setHttpServletRequest(httpServletRequest);
        messageDecoder.setParserPool(parserPool);
        try {
            messageDecoder.initialize();
        } catch (ComponentInitializationException e) {
            e.printStackTrace();
        }

        // create DecodeMessage ProfileAction to perform decode the request
        AbstractProfileAction<HttpServletRequest, HttpServletResponse> decodeMessage =
                new DecodeMessage(messageDecoder);
        // DecodeMessage decodeMessage = new DecodeMessage(messageDecoder);
        ProfileRequestContext<HttpServletRequest, HttpServletResponse> profileRequestContext1 =
                new ProfileRequestContext<>();

        MessageContext<HttpServletRequest> inbound = new MessageContext<>();
        MessageContext<HttpServletResponse> outbound = new MessageContext<>();
        inbound.setMessage(httpServletRequest);
        //outbound.setMessage(httpServletResponse);
        profileRequestContext1.setInboundMessageContext(inbound);
        //profileRequestContext1.setOutboundMessageContext(outbound);
        decodeMessage.execute(profileRequestContext1);

        //create encode HttpServletResponse
        try {
            MessageContext<HttpServletRequest> inboundContext =
                    profileRequestContext1.getInboundMessageContext();
            //Start to use the opensaml v3 to generate the Http Response
            ProfileRequestContext profileRequestContext =
                    new ProfileRequestContext<>();
            final MessageContext context = new MessageContext();
            profileRequestContext.setInboundMessageContext(inboundContext);
            profileRequestContext.setOutboundMessageContext(context);


            //Construct the SAMLResponse by Profile Action

            //Step 1: create SAMLResponse with Issuer
            AddStatusResponseShell addStatusResponseShell = new AddStatusResponseShell();
            addStatusResponseShell.setIssuerLookupStrategy(new Function<ProfileRequestContext,String>() {
                public String apply(ProfileRequestContext input) {
                    return "MX-response-issuer";
                }
            });
            addStatusResponseShell.setMessageType(org.opensaml.saml.saml2.core.Response.DEFAULT_ELEMENT_NAME);
            addStatusResponseShell.initialize();

            addStatusResponseShell.execute(profileRequestContext);

            //Step 2: add InResponse to SAMLResponse
            AddInResponseToToResponse addInResponseToToResponse = new AddInResponseToToResponse();
            addInResponseToToResponse.execute(profileRequestContext);

            //Step 3: add destination to the SAMLResponse
            XMLObjectBuilderFactory builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
            SAMLObjectBuilder<Endpoint> endpointBuilder = (SAMLObjectBuilder<Endpoint>) builderFactory
                    .getBuilder(AssertionConsumerService.DEFAULT_ELEMENT_NAME);
            Endpoint samlEndpoint = endpointBuilder.buildObject();
            samlEndpoint.setLocation("MX-sp_sso_url");
            samlEndpoint.setResponseLocation("MX-sp_sso_url");
            SAMLBindingSupport.setRelayState((MessageContext<SAMLObject>)
                    profileRequestContext.getOutboundMessageContext(),"MX-relay-state");

            profileRequestContext.getOutboundMessageContext()
                    .getSubcontext(SAMLPeerEntityContext.class, true)
                    .getSubcontext(SAMLEndpointContext.class, true).setEndpoint(samlEndpoint);
            SAMLOutboundDestinationHandler handler = new SAMLOutboundDestinationHandler();
            handler.invoke(profileRequestContext.getOutboundMessageContext());

            //Add assertion
            SAML2ActionSupport.addAssertionToResponse(addInResponseToToResponse,
                    (Response) profileRequestContext.getOutboundMessageContext().getMessage(),
                    new SecureRandomIdentifierGenerationStrategy(), "MX_saml-issuer");

            //AddNameIDToSubjects
            AddNameIDToSubjects addNameIDToSubjects = new AddNameIDToSubjects();
            BasicSAML2NameIDGenerator basicSAML2NameIDGenerator = new BasicSAML2NameIDGenerator("MX_EMail");
            basicSAML2NameIDGenerator.setFormat(NameID.EMAIL);
            addNameIDToSubjects.setNameIDGenerator(basicSAML2NameIDGenerator);

            addNameIDToSubjects.initialize();
            addNameIDToSubjects.execute(profileRequestContext);

            //AddSubjectConfirmationToSubjects
            AddSubjectConfirmationToSubjects addSubjectConfirmationToSubjects = new AddSubjectConfirmationToSubjects();
            addSubjectConfirmationToSubjects.setMethod(SubjectConfirmation.METHOD_BEARER);
            addSubjectConfirmationToSubjects.initialize();
            addSubjectConfirmationToSubjects.execute(profileRequestContext);


            //AddNotBeforeConditionToAssertions
            //AddNotOnOrAfterConditionToAssertions
            AddNotBeforeConditionToAssertions addNotBeforeConditionToAssertions = new AddNotBeforeConditionToAssertions();
            addNotBeforeConditionToAssertions.initialize();
            addNotBeforeConditionToAssertions.execute(profileRequestContext);

            //AddNotOnOrAfterConditionToAssertions
            AddNotOnOrAfterConditionToAssertions addNotOnOrAfterConditionToAssertions = new AddNotOnOrAfterConditionToAssertions();
            addNotOnOrAfterConditionToAssertions.initialize();
            addNotOnOrAfterConditionToAssertions.execute(profileRequestContext);

            //AddAudienceRestrictionToAssertions
            AddAudienceRestrictionToAssertions addAudienceRestrictionToAssertions = new AddAudienceRestrictionToAssertions();

            addAudienceRestrictionToAssertions.setAudienceRestrictionsLookupStrategy(new Function<ProfileRequestContext,Collection<String>>() {
                public Collection<String> apply(ProfileRequestContext input) {
                    return ImmutableList.of("MX_authnRequest_issuer");
                }
            });
            addAudienceRestrictionToAssertions.initialize();
            addAudienceRestrictionToAssertions.execute(profileRequestContext);

            //final encode and get the response
            //initial a velocityEngine for encoder
            velocityEngine = new VelocityEngine();
            velocityEngine.setProperty(RuntimeConstants.INPUT_ENCODING, "UTF-8");
            velocityEngine.setProperty(RuntimeConstants.OUTPUT_ENCODING, "UTF-8");
            velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
            velocityEngine.setProperty("classpath.resource.loader.class",
                    "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
            velocityEngine.init();
            final MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
            MessageEncoderFactory httpEncoderFactory = new MessageEncoderFactory() {
                @Nullable
                @Override
                public MessageEncoder getMessageEncoder(@Nonnull ProfileRequestContext profileRequestContext) {
                    HTTPPostEncoder httpPostEncoder = new HTTPPostEncoder();
                    //set velocityEngine
                    httpPostEncoder.setVelocityEngine(velocityEngine);
                    //set response
                    httpPostEncoder.setHttpServletResponse(httpServletResponse);
                    return httpPostEncoder;
                }
            };

            EncodeMessage action = new EncodeMessage();
            action.setMessageEncoderFactory(httpEncoderFactory);
            action.initialize();
            action.execute(profileRequestContext);
            return httpServletResponse;
        } catch (ComponentInitializationException e) {
            e.printStackTrace();
            return null;
        } catch (MessageHandlerException e) {
            e.printStackTrace();
            return null;
        }

    }
}
