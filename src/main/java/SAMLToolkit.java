import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.apache.xml.security.exceptions.Base64DecodingException;
import org.apache.xml.security.utils.Base64;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.common.SAMLObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * User:    Xin Meng
 * Date:    07/05/17
 * Project: opensamlv3demo
 *
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
            return(SAMLObject) unmarshaller.unmarshall(messageElem);
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
}
