package com.origam.xslfo;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

public final class XslFoRenderer {
    private static final Logger LOGGER = Logger.getLogger(XslFoRenderer.class.getName());

    private static final byte[] WARMUP_FO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
              <fo:layout-master-set>
                <fo:simple-page-master master-name="warmup" page-width="1in" page-height="1in" margin="0.1in">
                  <fo:region-body/>
                </fo:simple-page-master>
              </fo:layout-master-set>
              <fo:page-sequence master-reference="warmup">
                <fo:flow flow-name="xsl-region-body">
                  <fo:block font-size="1pt">.</fo:block>
                </fo:flow>
              </fo:page-sequence>
            </fo:root>
            """.getBytes(StandardCharsets.UTF_8);

    private final FopFactory fopFactory;
    private final String baseSystemId;

    private XslFoRenderer(FopFactory fopFactory, String baseSystemId) {
        this.fopFactory = fopFactory;
        this.baseSystemId = baseSystemId;
    }

    public static XslFoRenderer create(AppConfig config)
            throws FOPException, SAXException, IOException {
        FopFactory fopFactory = config.fopConfigFile() == null
                ? FopFactory.newInstance(config.baseUri())
                : FopFactory.newInstance(config.fopConfigFile().toFile());
        return new XslFoRenderer(fopFactory, config.baseUri().toString());
    }

    public void warmUp() {
        render(WARMUP_FO);
        LOGGER.info("Apache FOP warmup render completed.");
    }

    public RenderResult render(byte[] xslFo) {
        return render(new ByteArrayInputStream(xslFo));
    }

    public RenderResult render(InputStream xslFo) {
        try (ByteArrayOutputStream pdf = new ByteArrayOutputStream(64 * 1024);
                BufferedOutputStream bufferedPdf = new BufferedOutputStream(pdf)) {
            FOUserAgent userAgent = fopFactory.newFOUserAgent();
            userAgent.setProducer("ORIGAM XSL-FO Server");
            userAgent.setCreator("ORIGAM");

            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, userAgent, bufferedPdf);
            Transformer transformer = newSecureTransformer();
            Source source = newSecureSaxSource(xslFo);
            Result result = new SAXResult(fop.getDefaultHandler());

            transformer.transform(source, result);
            bufferedPdf.flush();
            return new RenderResult(pdf.toByteArray(), fop.getResults().getPageCount());
        } catch (TransformerException
                | ParserConfigurationException
                | SAXException
                | IOException exception) {
            throw new RenderException(
                    "XSL-FO could not be rendered: " + rootMessage(exception),
                    exception);
        }
    }

    private static Transformer newSecureTransformer() throws TransformerConfigurationException {
        TransformerFactory factory = TransformerFactory.newInstance();
        setFeatureIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        return factory.newTransformer();
    }

    private Source newSecureSaxSource(InputStream xslFo)
            throws ParserConfigurationException, SAXException {
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        setFeatureIfSupported(parserFactory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeatureIfSupported(parserFactory,
                "http://apache.org/xml/features/disallow-doctype-decl",
                true);
        setFeatureIfSupported(parserFactory,
                "http://xml.org/sax/features/external-general-entities",
                false);
        setFeatureIfSupported(parserFactory,
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        setFeatureIfSupported(parserFactory,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);

        XMLReader reader = parserFactory.newSAXParser().getXMLReader();
        InputSource inputSource = new InputSource(xslFo);
        inputSource.setSystemId(baseSystemId);
        return new SAXSource(reader, inputSource);
    }

    private static void setFeatureIfSupported(
            TransformerFactory factory,
            String feature,
            boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (TransformerConfigurationException exception) {
            LOGGER.fine(() -> "Transformer feature not supported: " + feature);
        }
    }

    private static void setAttributeIfSupported(
            TransformerFactory factory,
            String attribute,
            String value) {
        try {
            factory.setAttribute(attribute, value);
        } catch (IllegalArgumentException exception) {
            LOGGER.fine(() -> "Transformer attribute not supported: " + attribute);
        }
    }

    private static void setFeatureIfSupported(
            SAXParserFactory factory,
            String feature,
            boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException
                | SAXNotRecognizedException
                | SAXNotSupportedException exception) {
            LOGGER.fine(() -> "SAX feature not supported: " + feature);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}
