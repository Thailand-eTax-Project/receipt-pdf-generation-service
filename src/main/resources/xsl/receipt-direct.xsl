<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:rsm="urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2"
    xmlns:ram="urn:etda:uncefact:data:standard:Receipt_ReusableAggregateBusinessInformationEntity:2">

    <xsl:output method="xml" indent="yes"/>

    <!-- Injected by Java: ThaiAmountWordsConverter output -->
    <xsl:param name="amountInWords"/>

    <!-- Page dimensions -->
    <xsl:variable name="page-width">210mm</xsl:variable>
    <xsl:variable name="page-height">297mm</xsl:variable>
    <xsl:variable name="margin">15mm</xsl:variable>
    <xsl:variable name="font-family">THSarabunNew, NotoSansThai, Helvetica, sans-serif</xsl:variable>
    <xsl:variable name="font-size">11pt</xsl:variable>
    <xsl:variable name="font-size-small">9pt</xsl:variable>
    <xsl:variable name="font-size-large">14pt</xsl:variable>
    <xsl:variable name="font-size-title">18pt</xsl:variable>

    <!-- Root template: match signed XML root element -->
    <xsl:template match="/rsm:Receipt_CrossIndustryInvoice">
        <!-- Shorthand variables for deeply nested paths -->
        <xsl:variable name="doc"        select="rsm:ExchangedDocument"/>
        <xsl:variable name="txn"        select="rsm:SupplyChainTradeTransaction"/>
        <xsl:variable name="agreement"  select="$txn/ram:ApplicableHeaderTradeAgreement"/>
        <xsl:variable name="settlement" select="$txn/ram:ApplicableHeaderTradeSettlement"/>
        <xsl:variable name="summation"  select="$settlement/ram:SpecifiedTradeSettlementHeaderMonetarySummation"/>
        <xsl:variable name="seller"     select="$agreement/ram:SellerTradeParty"/>
        <xsl:variable name="buyer"      select="$agreement/ram:BuyerTradeParty"/>

        <fo:root>
            <fo:layout-master-set>
                <fo:simple-page-master master-name="taxinvoice-page"
                    page-width="{$page-width}" page-height="{$page-height}"
                    margin-top="{$margin}" margin-bottom="{$margin}"
                    margin-left="{$margin}" margin-right="{$margin}">
                    <fo:region-body margin-top="20mm" margin-bottom="20mm"/>
                    <fo:region-before extent="20mm"/>
                    <fo:region-after extent="20mm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <fo:page-sequence master-reference="taxinvoice-page">
                <!-- Header -->
                <fo:static-content flow-name="xsl-region-before">
                    <fo:block font-family="{$font-family}" font-size="{$font-size-small}"
                        color="#666666" border-bottom="0.5pt solid #cccccc" padding-bottom="2mm">
                        <fo:table width="100%" table-layout="fixed">
                            <fo:table-column column-width="50%"/>
                            <fo:table-column column-width="50%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block text-align="left">
                                            <xsl:value-of select="$seller/ram:Name"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block text-align="right">
                                            e-Tax Tax Invoice / ใบรับ / RECEIPTอิเล็กทรอนิกส์
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block>
                </fo:static-content>

                <!-- Footer -->
                <fo:static-content flow-name="xsl-region-after">
                    <fo:block font-family="{$font-family}" font-size="{$font-size-small}"
                        color="#666666" border-top="0.5pt solid #cccccc" padding-top="2mm">
                        <fo:table width="100%" table-layout="fixed">
                            <fo:table-column column-width="33%"/>
                            <fo:table-column column-width="34%"/>
                            <fo:table-column column-width="33%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <fo:block text-align="left">เอกสารนี้จัดทำด้วยระบบคอมพิวเตอร์</fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block text-align="center">
                                            หน้า <fo:page-number/> / <fo:page-number-citation ref-id="last-page"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell>
                                        <fo:block text-align="right">
                                            <xsl:value-of select="$doc/ram:ID"/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </fo:block>
                </fo:static-content>

                <!-- Body -->
                <fo:flow flow-name="xsl-region-body">
                    <!-- Title -->
                    <fo:block font-family="{$font-family}" font-size="{$font-size-title}"
                        font-weight="bold" text-align="center" space-after="5mm" color="#333333">
                        ใบเสร็จรับเงิน / ใบกำกับภาษี / RECEIPT / TAX INVOICE
                    </fo:block>
                    <fo:block font-family="{$font-family}" font-size="{$font-size}"
                        text-align="center" space-after="10mm" color="#666666">
                        (ต้นฉบับ / Original)
                    </fo:block>

                    <!-- Parties -->
                    <fo:table width="100%" table-layout="fixed" space-after="8mm">
                        <fo:table-column column-width="50%"/>
                        <fo:table-column column-width="50%"/>
                        <fo:table-body>
                            <fo:table-row>
                                <!-- Seller -->
                                <fo:table-cell padding-right="5mm">
                                    <fo:block font-family="{$font-family}" font-size="{$font-size}"
                                        background-color="#f5f5f5" padding="3mm" border="0.5pt solid #dddddd">
                                        <fo:block font-weight="bold" space-after="2mm">ผู้ขาย / Seller</fo:block>
                                        <fo:block><xsl:value-of select="$seller/ram:Name"/></fo:block>
                                        <fo:block>
                                            เลขประจำตัวผู้เสียภาษี: <xsl:value-of select="$seller/ram:SpecifiedTaxRegistration/ram:ID"/>
                                        </fo:block>
                                        <xsl:if test="$seller/ram:PostalTradeAddress">
                                            <fo:block>
                                                <xsl:value-of select="$seller/ram:PostalTradeAddress/ram:BuildingNumber"/>
                                                <xsl:if test="$seller/ram:PostalTradeAddress/ram:StreetName">
                                                    <xsl:text> </xsl:text>
                                                    <xsl:value-of select="$seller/ram:PostalTradeAddress/ram:StreetName"/>
                                                </xsl:if>
                                                <xsl:if test="$seller/ram:PostalTradeAddress/ram:PostcodeCode">
                                                    <xsl:text> </xsl:text>
                                                    <xsl:value-of select="$seller/ram:PostalTradeAddress/ram:PostcodeCode"/>
                                                </xsl:if>
                                            </fo:block>
                                        </xsl:if>
                                    </fo:block>
                                </fo:table-cell>
                                <!-- Buyer -->
                                <fo:table-cell padding-left="5mm">
                                    <fo:block font-family="{$font-family}" font-size="{$font-size}"
                                        background-color="#f5f5f5" padding="3mm" border="0.5pt solid #dddddd">
                                        <fo:block font-weight="bold" space-after="2mm">ผู้ซื้อ / Buyer</fo:block>
                                        <fo:block><xsl:value-of select="$buyer/ram:Name"/></fo:block>
                                        <fo:block>
                                            เลขประจำตัวผู้เสียภาษี: <xsl:value-of select="$buyer/ram:SpecifiedTaxRegistration/ram:ID"/>
                                        </fo:block>
                                        <xsl:if test="$buyer/ram:PostalTradeAddress">
                                            <fo:block>
                                                <xsl:value-of select="$buyer/ram:PostalTradeAddress/ram:BuildingNumber"/>
                                                <xsl:if test="$buyer/ram:PostalTradeAddress/ram:StreetName">
                                                    <xsl:text> </xsl:text>
                                                    <xsl:value-of select="$buyer/ram:PostalTradeAddress/ram:StreetName"/>
                                                </xsl:if>
                                                <xsl:if test="$buyer/ram:PostalTradeAddress/ram:PostcodeCode">
                                                    <xsl:text> </xsl:text>
                                                    <xsl:value-of select="$buyer/ram:PostalTradeAddress/ram:PostcodeCode"/>
                                                </xsl:if>
                                            </fo:block>
                                        </xsl:if>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                        </fo:table-body>
                    </fo:table>

                    <!-- Invoice details -->
                    <fo:table width="100%" table-layout="fixed" space-after="8mm"
                        font-family="{$font-family}" font-size="{$font-size}">
                        <fo:table-column column-width="25%"/>
                        <fo:table-column column-width="25%"/>
                        <fo:table-column column-width="25%"/>
                        <fo:table-column column-width="25%"/>
                        <fo:table-body>
                            <fo:table-row>
                                <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#e8e8e8">
                                    <fo:block font-weight="bold">เลขที่เอกสาร</fo:block>
                                    <fo:block font-size="{$font-size-small}">Tax Invoice No.</fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                                    <fo:block><xsl:value-of select="$doc/ram:ID"/></fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#e8e8e8">
                                    <fo:block font-weight="bold">วันที่</fo:block>
                                    <fo:block font-size="{$font-size-small}">Date</fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                                    <fo:block>
                                        <xsl:value-of select="substring($doc/ram:IssueDateTime, 1, 10)"/>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                            <xsl:if test="$settlement/ram:SpecifiedTradePaymentTerms/ram:DueDateDateTime">
                                <fo:table-row>
                                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#e8e8e8">
                                        <fo:block font-weight="bold">วันครบกำหนดชำระ</fo:block>
                                        <fo:block font-size="{$font-size-small}">Due Date</fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" number-columns-spanned="3">
                                        <fo:block>
                                            <xsl:value-of select="substring($settlement/ram:SpecifiedTradePaymentTerms/ram:DueDateDateTime, 1, 10)"/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </xsl:if>
                        </fo:table-body>
                    </fo:table>

                    <!-- Line items -->
                    <fo:table width="100%" table-layout="fixed" space-after="5mm"
                        font-family="{$font-family}" font-size="{$font-size}">
                        <fo:table-column column-width="8%"/>
                        <fo:table-column column-width="37%"/>
                        <fo:table-column column-width="12%"/>
                        <fo:table-column column-width="10%"/>
                        <fo:table-column column-width="15%"/>
                        <fo:table-column column-width="18%"/>
                        <fo:table-header>
                            <fo:table-row background-color="#4a4a4a" color="white" font-weight="bold">
                                <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                                    <fo:block text-align="center">ลำดับ</fo:block>
                                    <fo:block text-align="center" font-size="{$font-size-small}">No.</fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                                    <fo:block>รายการ</fo:block>
                                    <fo:block font-size="{$font-size-small}">Description</fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                                    <fo:block text-align="right">จำนวน</fo:block>
                                    <fo:block text-align="right" font-size="{$font-size-small}">Qty</fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                                    <fo:block text-align="center">หน่วย</fo:block>
                                    <fo:block text-align="center" font-size="{$font-size-small}">Unit</fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                                    <fo:block text-align="right">ราคา/หน่วย</fo:block>
                                    <fo:block text-align="right" font-size="{$font-size-small}">Unit Price</fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="3mm" border="0.5pt solid #333333">
                                    <fo:block text-align="right">จำนวนเงิน</fo:block>
                                    <fo:block text-align="right" font-size="{$font-size-small}">Amount</fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                        </fo:table-header>
                        <fo:table-body>
                            <xsl:for-each select="$txn/ram:IncludedSupplyChainTradeLineItem">
                                <fo:table-row>
                                    <xsl:attribute name="background-color">
                                        <xsl:choose>
                                            <xsl:when test="position() mod 2 = 0">#f9f9f9</xsl:when>
                                            <xsl:otherwise>white</xsl:otherwise>
                                        </xsl:choose>
                                    </xsl:attribute>
                                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                                        <fo:block text-align="center">
                                            <xsl:value-of select="ram:AssociatedDocumentLineDocument/ram:LineID"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                                        <fo:block><xsl:value-of select="ram:SpecifiedTradeProduct/ram:Name"/></fo:block>
                                        <xsl:if test="ram:SpecifiedTradeProduct/ram:ID">
                                            <fo:block font-size="{$font-size-small}" color="#666666">
                                                รหัส: <xsl:value-of select="ram:SpecifiedTradeProduct/ram:ID"/>
                                            </fo:block>
                                        </xsl:if>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                                        <fo:block text-align="right">
                                            <xsl:value-of select="format-number(number(ram:SpecifiedLineTradeDelivery/ram:BilledQuantity), '#,##0.##')"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                                        <fo:block text-align="center">
                                            <xsl:value-of select="ram:SpecifiedLineTradeDelivery/ram:BilledQuantity/@unitCode"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                                        <fo:block text-align="right">
                                            <xsl:value-of select="format-number(number(ram:SpecifiedLineTradeAgreement/ram:GrossPriceProductTradePrice/ram:ChargeAmount), '#,##0.00')"/>
                                        </fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                                        <fo:block text-align="right">
                                            <xsl:value-of select="format-number(number(ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:NetLineTotalAmount), '#,##0.00')"/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </xsl:for-each>
                        </fo:table-body>
                    </fo:table>

                    <!-- Totals -->
                    <fo:table width="100%" table-layout="fixed" space-after="8mm"
                        font-family="{$font-family}" font-size="{$font-size}">
                        <fo:table-column column-width="60%"/>
                        <fo:table-column column-width="22%"/>
                        <fo:table-column column-width="18%"/>
                        <fo:table-body>
                            <fo:table-row>
                                <fo:table-cell><fo:block/></fo:table-cell>
                                <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#f5f5f5">
                                    <fo:block text-align="right">รวมเงิน / Subtotal</fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                                    <fo:block text-align="right">
                                        <xsl:value-of select="format-number(number($summation/ram:LineTotalAmount), '#,##0.00')"/>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                            <xsl:if test="number($summation/ram:AllowanceTotalAmount) != 0">
                                <fo:table-row>
                                    <fo:table-cell><fo:block/></fo:table-cell>
                                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#f5f5f5">
                                        <fo:block text-align="right">ส่วนลด / Discount</fo:block>
                                    </fo:table-cell>
                                    <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                                        <fo:block text-align="right" color="red">
                                            -<xsl:value-of select="format-number(number($summation/ram:AllowanceTotalAmount), '#,##0.00')"/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                            </xsl:if>
                            <fo:table-row>
                                <fo:table-cell><fo:block/></fo:table-cell>
                                <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#f5f5f5">
                                    <fo:block text-align="right">มูลค่าก่อนภาษี / Amount before VAT</fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                                    <fo:block text-align="right">
                                        <xsl:value-of select="format-number(number($summation/ram:TaxBasisTotalAmount), '#,##0.00')"/>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                            <fo:table-row>
                                <fo:table-cell><fo:block/></fo:table-cell>
                                <fo:table-cell padding="2mm" border="0.5pt solid #dddddd" background-color="#f5f5f5">
                                    <fo:block text-align="right">
                                        ภาษีมูลค่าเพิ่ม <xsl:value-of select="$settlement/ram:ApplicableTradeTax/ram:CalculatedRate"/>% / VAT
                                    </fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="2mm" border="0.5pt solid #dddddd">
                                    <fo:block text-align="right">
                                        <xsl:value-of select="format-number(number($summation/ram:TaxTotalAmount), '#,##0.00')"/>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                            <fo:table-row font-weight="bold" font-size="{$font-size-large}">
                                <fo:table-cell><fo:block/></fo:table-cell>
                                <fo:table-cell padding="3mm" border="0.5pt solid #333333"
                                    background-color="#4a4a4a" color="white">
                                    <fo:block text-align="right">ยอดรวมทั้งสิ้น / Grand Total</fo:block>
                                </fo:table-cell>
                                <fo:table-cell padding="3mm" border="0.5pt solid #333333"
                                    background-color="#f0f0f0">
                                    <fo:block text-align="right">
                                        <xsl:value-of select="format-number(number($summation/ram:GrandTotalAmount), '#,##0.00')"/>
                                    </fo:block>
                                </fo:table-cell>
                            </fo:table-row>
                        </fo:table-body>
                    </fo:table>

                    <!-- Amount in words (XSLT parameter) -->
                    <xsl:if test="$amountInWords != ''">
                        <fo:block font-family="{$font-family}" font-size="{$font-size}" space-after="5mm"
                            padding="3mm" background-color="#fffde7" border="0.5pt solid #ffc107">
                            <fo:inline font-weight="bold">จำนวนเงินเป็นตัวอักษร: </fo:inline>
                            <xsl:value-of select="$amountInWords"/>
                        </fo:block>
                    </xsl:if>

                    <!-- Notes -->
                    <xsl:if test="$doc/ram:IncludedNote/ram:Subject">
                        <fo:block font-family="{$font-family}" font-size="{$font-size-small}"
                            space-after="5mm" padding="3mm"
                            background-color="#f5f5f5" border="0.5pt solid #dddddd">
                            <fo:block font-weight="bold" space-after="2mm">หมายเหตุ / Notes</fo:block>
                            <fo:block><xsl:value-of select="$doc/ram:IncludedNote/ram:Subject"/></fo:block>
                        </fo:block>
                    </xsl:if>

                    <!-- End marker for page counting -->
                    <fo:block id="last-page"/>
                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

</xsl:stylesheet>
