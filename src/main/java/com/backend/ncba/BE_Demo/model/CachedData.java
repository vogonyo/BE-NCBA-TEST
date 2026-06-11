package com.backend.ncba.BE_Demo.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Thread-safe snapshot of all reference data fetched from the SOAP service.
 * Immutable once constructed; provides O(1) lookup maps built at construction time.
 */
public class CachedData {

    private final List<CountryDto> countries;
    private final List<ContinentDto> continents;
    private final List<CurrencyDto> currencies;
    private final List<LanguageDto> languages;

    // Pre-built indices for O(1) / O(k) lookups
    private final Map<String, CountryDto> countryByIsoCode;
    private final Map<String, List<CountryDto>> countriesByCurrency;
    private final Map<String, List<CountryDto>> countriesByContinent;

    private final Instant lastUpdated;

    public CachedData(List<CountryDto> countries,
                      List<ContinentDto> continents,
                      List<CurrencyDto> currencies) {
        this.countries = Collections.unmodifiableList(new ArrayList<>(countries));
        this.continents = Collections.unmodifiableList(new ArrayList<>(continents));
        this.currencies = Collections.unmodifiableList(new ArrayList<>(currencies));

        this.countryByIsoCode = countries.stream()
                .collect(Collectors.toUnmodifiableMap(
                        c -> c.isoCode().toUpperCase(),
                        Function.identity(),
                        (a, b) -> a));

        this.countriesByCurrency = countries.stream()
                .filter(c -> c.currencyIsoCode() != null && !c.currencyIsoCode().isBlank())
                .collect(Collectors.groupingBy(
                        c -> c.currencyIsoCode().toUpperCase(),
                        Collectors.toUnmodifiableList()));

        this.countriesByContinent = countries.stream()
                .filter(c -> c.continentCode() != null && !c.continentCode().isBlank())
                .collect(Collectors.groupingBy(
                        c -> c.continentCode().toUpperCase(),
                        Collectors.toUnmodifiableList()));

        // Derive unique languages from country data
        this.languages = countries.stream()
                .flatMap(c -> c.languages().stream())
                .collect(Collectors.toMap(
                        LanguageDto::isoCode,
                        Function.identity(),
                        (a, b) -> a))
                .values().stream()
                .sorted(java.util.Comparator.comparing(LanguageDto::name))
                .toList();

        this.lastUpdated = Instant.now();
    }

    public List<CountryDto> getCountries() { return countries; }
    public List<ContinentDto> getContinents() { return continents; }
    public List<CurrencyDto> getCurrencies() { return currencies; }
    public List<LanguageDto> getLanguages() { return languages; }
    public Instant getLastUpdated() { return lastUpdated; }

    public CountryDto findByIsoCode(String isoCode) {
        return countryByIsoCode.get(isoCode.toUpperCase());
    }

    public List<CountryDto> findByCurrency(String currencyCode) {
        return countriesByCurrency.getOrDefault(currencyCode.toUpperCase(), List.of());
    }

    public List<CountryDto> findByContinent(String continentCode) {
        return countriesByContinent.getOrDefault(continentCode.toUpperCase(), List.of());
    }

    public Collection<List<CountryDto>> allCurrencyGroups() {
        return countriesByCurrency.values();
    }
}
