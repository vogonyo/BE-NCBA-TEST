package com.backend.ncba.BE_Demo.service;

import com.backend.ncba.BE_Demo.model.*;

import java.util.List;

public interface CountryService {

    /** Paginated, filtered, sorted country list. */
    PagedResponse<CountryDto> getCountries(
            String name, String continent, String currency, String language,
            int page, int size, String sort, String direction);

    /** Single country by ISO 3166-1 alpha-2 code. */
    CountryDto getCountryByIsoCode(String isoCode);

    /** All countries that use a given currency (ISO 4217). */
    List<CountryDto> getCountriesByCurrency(String currencyCode);

    /** All continents. */
    List<ContinentDto> getContinents();

    /** All currencies. */
    List<CurrencyDto> getCurrencies();

    /** All languages (derived from country data). */
    List<LanguageDto> getLanguages();
}
