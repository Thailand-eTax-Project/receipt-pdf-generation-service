package com.wpanther.receipt.pdf.infrastructure.adapter.out.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RestTemplateSignedXmlFetcherTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private RestTemplateSignedXmlFetcher fetcher;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        fetcher = new RestTemplateSignedXmlFetcher(restTemplate);
    }

    @Test
    void fetch_success_returnsXml() {
        mockServer.expect(requestTo("http://minio/xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("<receipt/>", MediaType.APPLICATION_XML));

        String result = fetcher.fetch("http://minio/xml");

        assertThat(result).isEqualTo("<receipt/>");
        mockServer.verify();
    }

    @Test
    void fetch_emptyResponse_throwsException() {
        mockServer.expect(requestTo("http://minio/empty"))
                .andRespond(withSuccess("", MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> fetcher.fetch("http://minio/empty"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void fetch_serverError_throwsException() {
        mockServer.expect(requestTo("http://minio/error"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> fetcher.fetch("http://minio/error"))
                .isInstanceOf(Exception.class);
    }
}
