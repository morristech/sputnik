package pl.touk.sputnik.connector.http;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import pl.touk.sputnik.configuration.ConfigurationBuilder;
import pl.touk.sputnik.configuration.GeneralOption;
import pl.touk.sputnik.connector.ConnectorDetails;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.googlecode.catchexception.CatchException.caughtException;
import static com.googlecode.catchexception.apis.CatchExceptionAssertJ.then;
import static com.googlecode.catchexception.apis.CatchExceptionAssertJ.when;
import static org.assertj.core.api.Assertions.assertThat;


public class SslVerificationTest {

    @Rule
    public WireMockRule trustedService = new WireMockRule(wireMockConfig().dynamicPort().dynamicHttpsPort()
            .keystorePath(this.getClass().getResource("/ssl/trusted/keystore.jks").getPath()).keystorePassword("changeit"));
    @Rule
    public WireMockRule untrustedService = new WireMockRule(wireMockConfig().dynamicPort().dynamicHttpsPort()
            .keystorePath(this.getClass().getResource("/ssl/untrusted/keystore.jks").getPath()).keystorePassword("changeit"));

    @Before
    public void setUp() {
        untrustedService.stubFor(get(urlEqualTo("/hello")).willReturn(aResponse().withStatus(200)));
        trustedService.stubFor(get(urlEqualTo("/hello")).willReturn(aResponse().withStatus(200)));
    }

    @Test
    public void doNotVerifySslTrustWhenVerificationIsOff() throws Exception {
        // given
        ConnectorDetails connectorDetails = buildConnectorDetails("localhost", untrustedService.httpsPort(), "true", "false");
        HttpHelper httpHelper = new HttpHelper();
        HttpHost httpHost = httpHelper.buildHttpHost(connectorDetails);
        CloseableHttpClient closeableHttpClient = httpHelper.buildClient(httpHost, connectorDetails);

        // when
        CloseableHttpResponse response = closeableHttpClient.execute(httpHost, new HttpGet("/hello"));

        // then
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
    }

    @Test
    public void doNotVerifyHostnameWhenVerificationIsOff() throws Exception {
        // given
        ConnectorDetails connectorDetails = buildConnectorDetails("127.0.0.1", trustedService.httpsPort(), "true", "false");
        HttpHelper httpHelper = new HttpHelper();
        HttpHost httpHost = httpHelper.buildHttpHost(connectorDetails);
        CloseableHttpClient closeableHttpClient = httpHelper.buildClient(httpHost, connectorDetails);

        // when
        CloseableHttpResponse response = closeableHttpClient.execute(httpHost, new HttpGet("/hello"));

        // then
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
    }

    @Test
    public void verifySslTrust() throws Exception {
        // given
        setSystemTrustStore();
        ConnectorDetails connectorDetails = buildConnectorDetails("localhost", trustedService.httpsPort(), "true", "true");
        HttpHelper httpHelper = new HttpHelper();
        HttpHost httpHost = httpHelper.buildHttpHost(connectorDetails);
        CloseableHttpClient closeableHttpClient = httpHelper.buildClient(httpHost, connectorDetails);

        // when
        CloseableHttpResponse response = closeableHttpClient.execute(httpHost, new HttpGet("/hello"));

        // then
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
    }

    @Test
    public void verifySslTrustThrowsSSLHandshakeException() throws Exception {
        // given
        ConnectorDetails connectorDetails = buildConnectorDetails("localhost", untrustedService.httpsPort(), "true", "true");
        HttpHelper httpHelper = new HttpHelper();
        HttpHost httpHost = httpHelper.buildHttpHost(connectorDetails);
        CloseableHttpClient closeableHttpClient = httpHelper.buildClient(httpHost, connectorDetails);

        // when
        when(closeableHttpClient).execute(httpHost, new HttpGet("/hello"));

        // then
        then(caughtException()).isInstanceOf(SSLHandshakeException.class);
    }

    @Test
    public void useDefaultTrustStoreToVerifySslTrust() throws Exception {
        // given
        ConnectorDetails connectorDetails = buildConnectorDetails("localhost", untrustedService.httpsPort(), "true", "true");
        HttpHelper httpHelper = new HttpHelper();
        HttpHost httpHost = httpHelper.buildHttpHost(connectorDetails);
        CloseableHttpClient closeableHttpClient = httpHelper.buildClient(httpHost, connectorDetails);

        // when
        when(closeableHttpClient).execute(httpHost, new HttpGet("/hello"));

        // then
        then(caughtException()).isInstanceOf(SSLHandshakeException.class);
    }

    @Test
    public void verifyHostnameThrowsSSLPeerUnverifiedExceptionWhenHostDoesNotMatch() throws Exception {
        // given
        setSystemTrustStore();
        ConnectorDetails connectorDetails = buildConnectorDetails("127.0.0.1", trustedService.httpsPort(), "true", "true");
        HttpHelper httpHelper = new HttpHelper();
        HttpHost httpHost = httpHelper.buildHttpHost(connectorDetails);
        CloseableHttpClient closeableHttpClient = httpHelper.buildClient(httpHost, connectorDetails);

        // when
        when(closeableHttpClient).execute(httpHost, new HttpGet("/hello"));

        // then
        then(caughtException()).isInstanceOf(SSLPeerUnverifiedException.class);
    }

    private void setSystemTrustStore() {
        System.setProperty("javax.net.ssl.trustStore", this.getClass().getResource("/ssl/trusted/cacerts.jks").getPath());
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    }

    private ConnectorDetails buildConnectorDetails(String host, int port, String useHttps, String verifySsl) {
        Properties properties = new Properties();
        properties.setProperty(GeneralOption.HOST.getKey(), host);
        properties.setProperty(GeneralOption.PORT.getKey(), Integer.toString(port));
        properties.setProperty(GeneralOption.USE_HTTPS.getKey(), useHttps);
        properties.setProperty(GeneralOption.VERIFY_SSL.getKey(), verifySsl);
        return new ConnectorDetails(ConfigurationBuilder.initFromProperties(properties));
    }

}