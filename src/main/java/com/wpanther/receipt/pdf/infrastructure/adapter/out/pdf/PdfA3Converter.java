package com.wpanther.receipt.pdf.infrastructure.adapter.out.pdf;

import com.wpanther.receipt.pdf.domain.exception.ReceiptPdfGenerationException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.xml.XmpSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

/**
 * Converts PDF documents to PDF/A-3 format and embeds XML attachments.
 *
 * PDF/A-3 is an ISO standard for long-term archiving of electronic documents
 * that allows embedding of arbitrary file formats (including XML).
 *
 * <p>The ICC color profile is loaded once at construction and reused for every
 * conversion call, avoiding repeated classpath lookups.
 */
@Component
@Slf4j
public class PdfA3Converter {

    private static final String ICC_PROFILE_PATH = "icc/sRGB.icc";
    private static final String MIME_TYPE_XML = "application/xml";
    private static final String AFRelationship_SOURCE = "Source";

    private final String iccProfilePath;
    private final Timer conversionTimer;

    public PdfA3Converter(@Value("${app.pdf.icc-profile-path:icc/sRGB.icc}") String iccProfilePath,
                          MeterRegistry meterRegistry) {
        this.iccProfilePath = iccProfilePath;
        this.conversionTimer = meterRegistry.timer("pdf.conversion.pdfa3");
        // Verify ICC profile is available at startup
        loadIccProfile();
    }

    /**
     * Convert PDF to PDF/A-3b format with embedded XML.
     *
     * @param pdfBytes      The source PDF bytes
     * @param xmlContent    The XML content to embed
     * @param xmlFilename   The filename for the embedded XML (e.g., "receipt.xml")
     * @param receiptNumber The receipt number for metadata
     * @return PDF/A-3 compliant PDF bytes with embedded XML
     * @throws ReceiptPdfGenerationException if conversion fails
     */
    public byte[] convertToPdfA3(byte[] pdfBytes, String xmlContent, String xmlFilename, String receiptNumber)
            throws ReceiptPdfGenerationException {

        log.debug("Converting PDF to PDF/A-3 with embedded XML: {}", xmlFilename);

        long t0 = System.nanoTime();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {

            // Add PDF/A-3 identification metadata
            addPdfAMetadata(document, receiptNumber);

            // Add ICC color profile (required for PDF/A)
            addColorProfile(document);

            // Embed the XML file
            embedXmlFile(document, xmlContent, xmlFilename);

            // Write the result
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.save(output);
                byte[] result = output.toByteArray();
                log.info("Converted to PDF/A-3: {} bytes (XML embedded: {})", result.length, xmlFilename);
                return result;
            }

        } catch (ReceiptPdfGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to convert PDF to PDF/A-3", e);
            throw new ReceiptPdfGenerationException("PDF/A-3 conversion failed: " + e.getMessage(), e);
        } finally {
            conversionTimer.record(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Add PDF/A-3b identification metadata using XMP.
     */
    private void addPdfAMetadata(PDDocument document, String receiptNumber) throws Exception {
        XMPMetadata xmp = XMPMetadata.createXMPMetadata();

        // PDF/A identification
        PDFAIdentificationSchema pdfaId = xmp.createAndAddPDFAIdentificationSchema();
        pdfaId.setPart(3);  // PDF/A-3
        pdfaId.setConformance("B");  // Level B (basic)

        // Dublin Core metadata
        DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
        dc.setTitle("Thai e-Tax Receipt: " + receiptNumber);
        dc.setDescription("Electronic receipt with embedded XML source");
        dc.addCreator("Receipt PDF Generation Service");
        dc.setFormat("application/pdf");

        // XMP Basic metadata
        XMPBasicSchema xmpBasic = xmp.createAndAddXMPBasicSchema();
        Calendar now = GregorianCalendar.from(LocalDateTime.now().atZone(ZoneId.systemDefault()));
        xmpBasic.setCreateDate(now);
        xmpBasic.setModifyDate(now);
        xmpBasic.setCreatorTool("Thai e-Tax Receipt System");

        // Serialize XMP to document
        XmpSerializer serializer = new XmpSerializer();
        ByteArrayOutputStream xmpOutput = new ByteArrayOutputStream();
        serializer.serialize(xmp, xmpOutput, true);

        PDMetadata metadata = new PDMetadata(document);
        metadata.importXMPMetadata(xmpOutput.toByteArray());

        PDDocumentCatalog catalog = document.getDocumentCatalog();
        catalog.setMetadata(metadata);
    }

    /**
     * Add sRGB ICC color profile (required for PDF/A compliance).
     * Uses the profile loaded once at construction time.
     */
    private void addColorProfile(PDDocument document) throws Exception {
        PDDocumentCatalog catalog = document.getDocumentCatalog();

        // Check if output intent already exists
        if (catalog.getOutputIntents() != null && !catalog.getOutputIntents().isEmpty()) {
            log.debug("Output intent already exists, skipping ICC profile");
            return;
        }

        // Load ICC profile using try-with-resources
        try (InputStream iccStream = loadIccProfile()) {
            PDOutputIntent outputIntent = new PDOutputIntent(document, iccStream);
            outputIntent.setInfo("sRGB IEC61966-2.1");
            outputIntent.setOutputCondition("sRGB");
            outputIntent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
            outputIntent.setRegistryName("http://www.color.org");
            catalog.addOutputIntent(outputIntent);
            log.debug("Added sRGB ICC color profile");
        }
    }

    /**
     * Load ICC color profile from classpath.
     * Throws {@link IllegalStateException} if the profile is missing or unreadable so that
     * the service fails fast rather than silently producing non-PDF/A-compliant documents.
     *
     * @return InputStream containing the ICC profile data
     */
    private InputStream loadIccProfile() {
        ClassPathResource iccResource = new ClassPathResource(iccProfilePath);
        if (iccResource.exists()) {
            try {
                InputStream is = iccResource.getInputStream();
                log.info("Loaded ICC profile: {} (cached for reuse)", iccProfilePath);
                return is;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to read ICC profile from " + iccProfilePath + ": " + e.getMessage(), e);
            }
        }
        throw new IllegalStateException(
                "ICC profile not found on classpath: " + iccProfilePath
                + " — add sRGB.icc to src/main/resources/icc/ (PDF/A-3 compliance requires it)");
    }

    /**
     * Embed XML file as an attachment in the PDF.
     */
    private void embedXmlFile(PDDocument document, String xmlContent, String xmlFilename) throws Exception {
        PDDocumentCatalog catalog = document.getDocumentCatalog();

        // Create embedded file
        byte[] xmlBytes = xmlContent.getBytes(StandardCharsets.UTF_8);
        PDEmbeddedFile embeddedFile = new PDEmbeddedFile(document, new ByteArrayInputStream(xmlBytes));
        embeddedFile.setSubtype(MIME_TYPE_XML);
        embeddedFile.setSize(xmlBytes.length);
        embeddedFile.setCreationDate(new GregorianCalendar());
        embeddedFile.setModDate(new GregorianCalendar());

        // Create file specification
        PDComplexFileSpecification fileSpec = new PDComplexFileSpecification();
        fileSpec.setFile(xmlFilename);
        fileSpec.setFileUnicode(xmlFilename);
        fileSpec.setEmbeddedFile(embeddedFile);
        fileSpec.setEmbeddedFileUnicode(embeddedFile);

        // Set AFRelationship to "Source" (PDF/A-3 requirement for source data)
        fileSpec.getCOSObject().setName(COSName.getPDFName("AFRelationship"), AFRelationship_SOURCE);

        // Add to document's embedded files
        PDEmbeddedFilesNameTreeNode embeddedFilesTree = new PDEmbeddedFilesNameTreeNode();
        embeddedFilesTree.setNames(Collections.singletonMap(xmlFilename, fileSpec));

        // Get or create name dictionary
        PDDocumentNameDictionary nameDictionary = catalog.getNames();
        if (nameDictionary == null) {
            nameDictionary = new PDDocumentNameDictionary(catalog);
            catalog.setNames(nameDictionary);
        }
        nameDictionary.setEmbeddedFiles(embeddedFilesTree);

        // Add to AF array (Associated Files - PDF/A-3 requirement)
        catalog.getCOSObject().setItem(COSName.getPDFName("AF"), fileSpec);

        log.debug("Embedded XML file: {} ({} bytes)", xmlFilename, xmlBytes.length);
    }
}
