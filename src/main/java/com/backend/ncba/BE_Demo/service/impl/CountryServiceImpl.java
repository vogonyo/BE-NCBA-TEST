package com.backend.ncba.BE_Demo.service.impl;

import com.backend.ncba.BE_Demo.exception.ResourceNotFoundException;
import com.backend.ncba.BE_Demo.model.*;
import com.backend.ncba.BE_Demo.service.CountryService;
import com.backend.ncba.BE_Demo.service.ReferenceDataCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CountryServiceImpl implements CountryService {

    /** Valid sort fields → comparator factories. */
    private static final Map<String, Comparator<CountryDto>> SORT_MAP = Map.of(
            "name",           Comparator.comparing(CountryDto::name,           String.CASE_INSENSITIVE_ORDER),
            "isoCode",        Comparator.comparing(CountryDto::isoCode,        String.CASE_INSENSITIVE_ORDER),
            "capitalCity",    Comparator.comparing(CountryDto::capitalCity,    String.CASE_INSENSITIVE_ORDER),
            "continentCode",  Comparator.comparing(CountryDto::continentCode,  String.CASE_INSENSITIVE_ORDER),
            "continentName",  Comparator.comparing(CountryDto::continentName,  String.CASE_INSENSITIVE_ORDER),
            "currencyIsoCode",Comparator.comparing(CountryDto::currencyIsoCode,String.CASE_INSENSITIVE_ORDER),
            "currencyName",   Comparator.comparing(CountryDto::currencyName,   String.CASE_INSENSITIVE_ORDER),
            "phoneCode",      Comparator.comparing(CountryDto::phoneCode,      String.CASE_INSENSITIVE_ORDER)
    );

    private final ReferenceDataCache cache;

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public PagedResponse<CountryDto> getCountries(
            String name, String continent, String currency, String language,
            int page, int size, String sort, String direction) {

        CachedData data = cache.getData();
        Stream<CountryDto> stream = data.getCountries().stream();

        // Filtering
        if (StringUtils.hasText(name)) {
            String lc = name.toLowerCase();
            stream = stream.filter(c -> c.name().toLowerCase().contains(lc));
        }
        if (StringUtils.hasText(continent)) {
            String lc = continent.toLowerCase();
            stream = stream.filter(c ->
                    c.continentCode().equalsIgnoreCase(continent) ||
                    c.continentName().toLowerCase().contains(lc));
        }
        if (StringUtils.hasText(currency)) {
            stream = stream.filter(c -> c.currencyIsoCode().equalsIgnoreCase(currency));
        }
        if (StringUtils.hasText(language)) {
            String lc = language.toLowerCase();
            stream = stream.filter(c -> c.languages().stream().anyMatch(l ->
                    l.isoCode().equalsIgnoreCase(language) ||
                    l.name().toLowerCase().contains(lc)));
        }

        // Sorting
        Comparator<CountryDto> comparator = resolveComparator(sort);
        if ("DESC".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }
        List<CountryDto> filtered = stream.sorted(comparator).toList();

        return PagedResponse.of(filtered, page, size);
    }

    @Override
    public CountryDto getCountryByIsoCode(String isoCode) {
        CountryDto country = cache.getData().findByIsoCode(isoCode);
        if (country == null) {
            throw new ResourceNotFoundException("Country not found with ISO code: " + isoCode.toUpperCase());
        }
        return country;
    }

    @Override
    public List<CountryDto> getCountriesByCurrency(String currencyCode) {
        List<CountryDto> countries = cache.getData().findByCurrency(currencyCode);
        if (countries.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No countries found using currency: " + currencyCode.toUpperCase());
        }
        return countries;
    }

    @Override
    public List<ContinentDto> getContinents() {
        return cache.getData().getContinents();
    }

    @Override
    public List<CurrencyDto> getCurrencies() {
        return cache.getData().getCurrencies();
    }

    @Override
    public List<LanguageDto> getLanguages() {
        return cache.getData().getLanguages();
    }

    // ──────────────────────────────────────────────────────────────────────────

    private Comparator<CountryDto> resolveComparator(String sort) {
        if (!StringUtils.hasText(sort)) {
            return SORT_MAP.get("name");
        }
        Comparator<CountryDto> comparator = SORT_MAP.get(sort);
        if (comparator == null) {
            throw new IllegalArgumentException(
                    "Invalid sort field: '" + sort + "'. Valid fields: " + SORT_MAP.keySet());
        }
        return comparator;
    }
}
