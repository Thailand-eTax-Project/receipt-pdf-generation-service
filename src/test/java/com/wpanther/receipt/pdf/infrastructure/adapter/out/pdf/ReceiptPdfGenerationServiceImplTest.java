package com.wpanther.receipt.pdf.infrastructure.adapter.out.pdf;

import com.wpanther.receipt.pdf.domain.exception.ReceiptPdfGenerationException;
import com.wpanther.receipt.pdf.domain.service.ReceiptPdfGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReceiptPdfGenerationServiceImpl Unit Tests")
class ReceiptPdfGenerationServiceImplTest {

    @Mock private FopReceiptPdfGenerator fopPdfGenerator;
    @Mock private PdfA3Converter pdfA3Converter;

    private ReceiptPdfGenerationServiceImpl service;

    private static final String DOC_NUMBER = "RCPT-2024-001";

    // Minimal signed XML with a GrandTotalAmount of 1070 (Receipt namespaces)
    private static final String SIGNED_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<rsm:Receipt_CrossIndustryInvoice " +
        "    xmlns:ram=\"urn:etda:uncefact:data:standard:Receipt_ReusableAggregateBusinessInformationEntity:2\"" +
        "    xmlns:rsm=\"urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2\">" +
        "  <rsm:ExchangedDocument><ram:ID>RCPT-2024-001</ram:ID><ram:IssueDateTime>2024-01-15T00:00:00.0</ram:IssueDateTime></rsm:ExchangedDocument>" +
        "  <rsm:SupplyChainTradeTransaction>" +
        "    <ram:ApplicableHeaderTradeAgreement>" +
        "      <ram:SellerTradeParty><ram:Name>Seller</ram:Name><ram:SpecifiedTaxRegistration><ram:ID>1111111111111</ram:ID></ram:SpecifiedTaxRegistration></ram:SellerTradeParty>" +
        "      <ram:BuyerTradeParty><ram:Name>Buyer</ram:Name><ram:SpecifiedTaxRegistration><ram:ID>2222222222222</ram:ID></ram:SpecifiedTaxRegistration></ram:BuyerTradeParty>" +
        "    </ram:ApplicableHeaderTradeAgreement>" +
        "    <ram:ApplicableHeaderTradeDelivery/>" +
        "    <ram:ApplicableHeaderTradeSettlement>" +
        "      <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>" +
        "      <ram:ApplicableTradeTax><ram:TypeCode>VAT</ram:TypeCode><ram:CalculatedRate>7</ram:CalculatedRate></ram:ApplicableTradeTax>" +
        "      <ram:SpecifiedTradeSettlementHeaderMonetarySummation>" +
        "        <ram:LineTotalAmount>1000</ram:LineTotalAmount>" +
        "        <ram:AllowanceTotalAmount>0</ram:AllowanceTotalAmount>" +
        "        <ram:TaxBasisTotalAmount>1000</ram:TaxBasisTotalAmount>" +
        "        <ram:TaxTotalAmount>70</ram:TaxTotalAmount>" +
        "        <ram:GrandTotalAmount>1070</ram:GrandTotalAmount>" +
        "      </ram:SpecifiedTradeSettlementHeaderMonetarySummation>" +
        "    </ram:ApplicableHeaderTradeSettlement>" +
        "    <ram:IncludedSupplyChainTradeLineItem>" +
        "      <ram:AssociatedDocumentLineDocument><ram:LineID>1</ram:LineID></ram:AssociatedDocumentLineDocument>" +
        "      <ram:SpecifiedTradeProduct><ram:Name>Item</ram:Name></ram:SpecifiedTradeProduct>" +
        "      <ram:SpecifiedLineTradeAgreement><ram:GrossPriceProductTradePrice><ram:ChargeAmount>1000</ram:ChargeAmount></ram:GrossPriceProductTradePrice></ram:SpecifiedLineTradeAgreement>" +
        "      <ram:SpecifiedLineTradeDelivery><ram:BilledQuantity unitCode=\"EA\">1</ram:BilledQuantity></ram:SpecifiedLineTradeDelivery>" +
        "      <ram:SpecifiedLineTradeSettlement><ram:ApplicableTradeTax><ram:TypeCode>VAT</ram:TypeCode></ram:ApplicableTradeTax>" +
        "        <ram:SpecifiedTradeSettlementLineMonetarySummation><ram:NetLineTotalAmount>1000</ram:NetLineTotalAmount></ram:SpecifiedTradeSettlementLineMonetarySummation>" +
        "      </ram:SpecifiedLineTradeSettlement>" +
        "    </ram:IncludedSupplyChainTradeLineItem>" +
        "  </rsm:SupplyChainTradeTransaction>" +
        "</rsm:Receipt_CrossIndustryInvoice>";

    private static final String SIGNED_XML_NO_GRAND_TOTAL =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<rsm:Receipt_CrossIndustryInvoice " +
        "    xmlns:ram=\"urn:etda:uncefact:data:standard:Receipt_ReusableAggregateBusinessInformationEntity:2\"" +
        "    xmlns:rsm=\"urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2\">" +
        "  <rsm:ExchangedDocument><ram:ID>X</ram:ID></rsm:ExchangedDocument>" +
        "  <rsm:SupplyChainTradeTransaction>" +
        "    <ram:ApplicableHeaderTradeSettlement>" +
        "      <ram:SpecifiedTradeSettlementHeaderMonetarySummation/>" +
        "    </ram:ApplicableHeaderTradeSettlement>" +
        "  </rsm:SupplyChainTradeTransaction>" +
        "</rsm:Receipt_CrossIndustryInvoice>";

    @BeforeEach
    void setUp() {
        service = new ReceiptPdfGenerationServiceImpl(fopPdfGenerator, pdfA3Converter);
    }

    @Test
    @DisplayName("generatePdf() throws when signedXml is null")
    void generatePdf_nullSignedXml_throws() {
        assertThatThrownBy(() -> service.generatePdf(DOC_NUMBER, null))
                .isInstanceOf(ReceiptPdfGenerationException.class)
                .hasMessageContaining("signedXml is null or blank");
        verifyNoInteractions(fopPdfGenerator, pdfA3Converter);
    }

    @Test
    @DisplayName("generatePdf() throws when signedXml is blank")
    void generatePdf_blankSignedXml_throws() {
        assertThatThrownBy(() -> service.generatePdf(DOC_NUMBER, "   "))
                .isInstanceOf(ReceiptPdfGenerationException.class)
                .hasMessageContaining("signedXml is null or blank");
        verifyNoInteractions(fopPdfGenerator, pdfA3Converter);
    }

    @Test
    @DisplayName("generatePdf() throws when GrandTotalAmount is missing")
    void generatePdf_missingGrandTotal_throws() {
        assertThatThrownBy(() -> service.generatePdf(DOC_NUMBER, SIGNED_XML_NO_GRAND_TOTAL))
                .isInstanceOf(ReceiptPdfGenerationException.class)
                .hasMessageContaining("GrandTotalAmount");
        verifyNoInteractions(fopPdfGenerator, pdfA3Converter);
    }

    @Test
    @DisplayName("generatePdf() extracts grand total and passes amountInWords param to FOP")
    void generatePdf_success_passesAmountInWordsToFop() throws Exception {
        byte[] basePdf = new byte[2000];
        byte[] pdfA3   = new byte[3000];
        when(fopPdfGenerator.generatePdf(eq(SIGNED_XML), any())).thenReturn(basePdf);
        when(pdfA3Converter.convertToPdfA3(eq(basePdf), eq(SIGNED_XML), anyString(), eq(DOC_NUMBER)))
                .thenReturn(pdfA3);

        byte[] result = service.generatePdf(DOC_NUMBER, SIGNED_XML);

        assertThat(result).isSameAs(pdfA3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fopPdfGenerator).generatePdf(eq(SIGNED_XML), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue()).containsKey("amountInWords");
        // 1070.00 = หนึ่งพันเจ็ดสิบบาทถ้วน
        assertThat(paramsCaptor.getValue().get("amountInWords"))
                .isEqualTo("หนึ่งพันเจ็ดสิบบาทถ้วน");
    }

    @Test
    @DisplayName("generatePdf() passes signed XML unmodified to PdfA3Converter for embedding")
    void generatePdf_success_embedsSignedXmlUnmodified() throws Exception {
        when(fopPdfGenerator.generatePdf(any(), any())).thenReturn(new byte[100]);
        when(pdfA3Converter.convertToPdfA3(any(), any(), any(), any())).thenReturn(new byte[200]);

        service.generatePdf(DOC_NUMBER, SIGNED_XML);

        verify(pdfA3Converter).convertToPdfA3(any(), eq(SIGNED_XML),
                eq("receipt-" + DOC_NUMBER + ".xml"), eq(DOC_NUMBER));
    }

    @Test
    @DisplayName("generatePdf() wraps ReceiptPdfGenerationException from FOP")
    void generatePdf_fopFails_wrapsException() throws Exception {
        when(fopPdfGenerator.generatePdf(any(), any()))
                .thenThrow(new ReceiptPdfGenerationException("XSL failed"));

        assertThatThrownBy(() -> service.generatePdf(DOC_NUMBER, SIGNED_XML))
                .isInstanceOf(ReceiptPdfGenerationException.class)
                .hasMessageContaining("XSL failed");
        verifyNoInteractions(pdfA3Converter);
    }

    @Test
    @DisplayName("generatePdf() wraps ReceiptPdfGenerationException from PdfA3Converter")
    void generatePdf_pdfA3Fails_wrapsException() throws Exception {
        when(fopPdfGenerator.generatePdf(any(), any())).thenReturn(new byte[100]);
        when(pdfA3Converter.convertToPdfA3(any(), any(), any(), any()))
                .thenThrow(new ReceiptPdfGenerationException("ICC missing"));

        assertThatThrownBy(() -> service.generatePdf(DOC_NUMBER, SIGNED_XML))
                .isInstanceOf(ReceiptPdfGenerationException.class)
                .hasMessageContaining("ICC missing");
    }
}
