package com.wpanther.receipt.pdf.infrastructure.adapter.out.pdf;

import com.wpanther.receipt.pdf.domain.exception.ReceiptPdfGenerationException;
import com.wpanther.receipt.pdf.domain.service.ReceiptPdfGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

@Service
@Slf4j
public class ReceiptPdfGenerationServiceImpl implements ReceiptPdfGenerationService {

    private static final String RSM_NS =
        "urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2";
    private static final String RAM_NS =
        "urn:etda:uncefact:data:standard:Receipt_ReusableAggregateBusinessInformationEntity:2";
    private static final String GRAND_TOTAL_XPATH =
        "/rsm:Receipt_CrossIndustryInvoice" +
        "/rsm:SupplyChainTradeTransaction" +
        "/ram:ApplicableHeaderTradeSettlement" +
        "/ram:SpecifiedTradeSettlementHeaderMonetarySummation" +
        "/ram:GrandTotalAmount";

    private static final NamespaceContext NS_CONTEXT = new NamespaceContext() {
        @Override
        public String getNamespaceURI(String prefix) {
            return switch (prefix) {
                case "rsm" -> RSM_NS;
                case "ram" -> RAM_NS;
                default    -> XMLConstants.NULL_NS_URI;
            };
        }
        @Override public String getPrefix(String ns) { return null; }
        @Override public Iterator<String> getPrefixes(String ns) { return Collections.emptyIterator(); }
    };

    private final FopReceiptPdfGenerator fopPdfGenerator;
    private final PdfA3Converter pdfA3Converter;

    public ReceiptPdfGenerationServiceImpl(FopReceiptPdfGenerator fopPdfGenerator,
                                            PdfA3Converter pdfA3Converter) {
        this.fopPdfGenerator = fopPdfGenerator;
        this.pdfA3Converter  = pdfA3Converter;
    }

    @Override
    public byte[] generatePdf(String receiptNumber, String signedXml)
            throws ReceiptPdfGenerationException {

        log.info("Starting PDF generation for receipt: {}", receiptNumber);

        if (signedXml == null || signedXml.isBlank()) {
            throw new ReceiptPdfGenerationException(
                "signedXml is null or blank for receipt: " + receiptNumber);
        }

        try {
            BigDecimal grandTotal  = extractGrandTotal(signedXml, receiptNumber);
            String amountInWords   = ThaiAmountWordsConverter.toWords(grandTotal);
            log.debug("Grand total {} -> amountInWords: {}", grandTotal, amountInWords);

            Map<String, Object> params = Map.of("amountInWords", amountInWords);
            byte[] basePdf = fopPdfGenerator.generatePdf(signedXml, params);
            log.debug("Generated base PDF: {} bytes", basePdf.length);

            String xmlFilename = "receipt-" + receiptNumber + ".xml";
            byte[] pdfA3 = pdfA3Converter.convertToPdfA3(basePdf, signedXml, xmlFilename, receiptNumber);
            log.info("Generated PDF/A-3 for receipt {}: {} bytes", receiptNumber, pdfA3.length);
            return pdfA3;

        } catch (ReceiptPdfGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during PDF generation for receipt: {}", receiptNumber, e);
            throw new ReceiptPdfGenerationException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private BigDecimal extractGrandTotal(String signedXml, String receiptNumber)
            throws ReceiptPdfGenerationException {
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(NS_CONTEXT);
            String value = (String) xpath.evaluate(
                GRAND_TOTAL_XPATH,
                new InputSource(new StringReader(signedXml)),
                XPathConstants.STRING);
            if (value == null || value.isBlank()) {
                throw new ReceiptPdfGenerationException(
                    "GrandTotalAmount not found in signed XML for receipt: " + receiptNumber);
            }
            return new BigDecimal(value.trim());
        } catch (XPathExpressionException e) {
            throw new ReceiptPdfGenerationException(
                "Failed to extract GrandTotalAmount from signed XML: " + e.getMessage(), e);
        } catch (NumberFormatException e) {
            throw new ReceiptPdfGenerationException(
                "Invalid GrandTotalAmount in signed XML for receipt " + receiptNumber + ": " + e.getMessage(), e);
        }
    }
}
