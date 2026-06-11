package com.backend.ncba.BE_Demo.client;

import com.backend.ncba.BE_Demo.exception.SoapServiceUnavailableException;
import com.backend.ncba.BE_Demo.model.ContinentDto;
import com.backend.ncba.BE_Demo.model.CountryDto;
import com.backend.ncba.BE_Demo.model.CurrencyDto;
import com.backend.ncba.BE_Demo.model.LanguageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Low-level SOAP client for the CountryInfo web service.
 * Uses RestTemplate + raw XML to avoid JAX-WS code generation.
 * All XML parsing is XXE-hardened per OWASP recommendations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CountryInfoSoapClient {

    private static final String SOAP_NS = "http://www.oorsprong.org/websamples.countryinfo";

    private final RestTemplate restTemplate;

    @Value("${rdas.soap.endpoint}")
    private String soapEndpoint;

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Fetches the full country list from the SOAP service.
     * One call returns ~250 countries with all available fields.
     */
    public List<CountryDto> fetchAllCountries(List<ContinentDto> continents, List<CurrencyDto> currencies) {
        log.debug("Calling SOAP: FullCountryInfoAllCountries");
        String body = "<web:FullCountryInfoAllCountries/>";
        String xml = post(body);
        return parseCountries(xml, buildContinentMap(continents), buildCurrencyMap(currencies));
    }

    /** Fetches all continent codes and names. */
    public List<ContinentDto> fetchContinents() {
        log.debug("Calling SOAP: ListOfContinentsByName");
        String body = "<web:ListOfContinentsByName/>";
        String xml = post(body);
        return parseContinents(xml);
    }

    /** Fetches all currency codes and names. */
    public List<CurrencyDto> fetchCurrencies() {
        log.debug("Calling SOAP: ListOfCurrenciesByName");
        String body = "<web:ListOfCurrenciesByName/>";
        String xml = post(body);
        return parseCurrencies(xml);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HTTP transport
    // ──────────────────────────────────────────────────────────────────────────

    private String post(String bodyContent) {
        String envelope = """
                <?xml version="1.0" encoding="utf-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:web="http://www.oorsprong.org/websamples.countryinfo">
                  <soap:Header/>
                  <soap:Body>
                    %s
                  </soap:Body>
                </soap:Envelope>
                """.formatted(bodyContent);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.set("SOAPAction", "");

        try {
            return restTemplate.postForObject(
                    soapEndpoint, new HttpEntity<>(envelope, headers), String.class);
        } catch (RestClientException ex) {
            log.error("SOAP call failed [endpoint={}]: {}", soapEndpoint, ex.getMessage());
            throw new SoapServiceUnavailableException(
                    "SOAP service unavailable: " + ex.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // XML parsers
    // ──────────────────────────────────────────────────────────────────────────

    private List<CountryDto> parseCountries(String xml,
                                            java.util.Map<String, String> continentNames,
                                            java.util.Map<String, String> currencyNames) {
        Document doc = parse(xml);
        NodeList nodes = doc.getElementsByTagNameNS("*", "tCountryInfo");
        List<CountryDto> result = new ArrayList<>(nodes.getLength());

        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String continentCode = childText(el, "sContinentCode");
            String currencyCode  = childText(el, "sCurrencyISOCode");

            List<LanguageDto> languages = parseLanguages(el);

            result.add(new CountryDto(
                    childText(el, "sISOCode"),
                    childText(el, "sName"),
                    childText(el, "sCapitalCity"),
                    childText(el, "sPhoneCode"),
                    continentCode,
                    continentNames.getOrDefault(continentCode.toUpperCase(), continentCode),
                    currencyCode,
                    currencyNames.getOrDefault(currencyCode.toUpperCase(), currencyCode),
                    childText(el, "sCountryFlag"),
                    languages
            ));
        }
        log.info("Parsed {} countries from SOAP response", result.size());
        return result;
    }

    private List<LanguageDto> parseLanguages(Element countryEl) {
        List<LanguageDto> langs = new ArrayList<>();
        NodeList langNodes = countryEl.getElementsByTagNameNS("*", "tLanguage");
        for (int j = 0; j < langNodes.getLength(); j++) {
            Element lang = (Element) langNodes.item(j);
            langs.add(new LanguageDto(
                    childText(lang, "sISOCode"),
                    childText(lang, "sName")
            ));
        }
        return langs;
    }

    private List<ContinentDto> parseContinents(String xml) {
        Document doc = parse(xml);
        NodeList nodes = doc.getElementsByTagNameNS("*", "tContinent");
        List<ContinentDto> result = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            result.add(new ContinentDto(childText(el, "sCode"), childText(el, "sName")));
        }
        log.info("Parsed {} continents from SOAP response", result.size());
        return result;
    }

    private List<CurrencyDto> parseCurrencies(String xml) {
        Document doc = parse(xml);
        NodeList nodes = doc.getElementsByTagNameNS("*", "tCurrency");
        List<CurrencyDto> result = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            result.add(new CurrencyDto(childText(el, "sISOCode"), childText(el, "sName")));
        }
        log.info("Parsed {} currencies from SOAP response", result.size());
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // XML utilities
    // ──────────────────────────────────────────────────────────────────────────

    /** XXE-hardened XML parser (OWASP A05:2021). */
    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entity processing
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            throw new SoapServiceUnavailableException("Failed to parse SOAP response: " + ex.getMessage());
        }
    }

    /** Returns trimmed text of the first matching direct-child element. */
    private String childText(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && localName.equals(el.getLocalName())) {
                return el.getTextContent().trim();
            }
        }
        return "";
    }

    private java.util.Map<String, String> buildContinentMap(List<ContinentDto> continents) {
        return continents.stream()
                .collect(java.util.stream.Collectors.toMap(
                        c -> c.code().toUpperCase(), ContinentDto::name, (a, b) -> a));
    }

    private java.util.Map<String, String> buildCurrencyMap(List<CurrencyDto> currencies) {
        return currencies.stream()
                .collect(java.util.stream.Collectors.toMap(
                        c -> c.isoCode().toUpperCase(), CurrencyDto::name, (a, b) -> a));
    }
}
