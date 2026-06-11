package com.backend.ncba.BE_Demo.controller;

import com.backend.ncba.BE_Demo.exception.ResourceNotFoundException;
import com.backend.ncba.BE_Demo.model.*;
import com.backend.ncba.BE_Demo.service.CountryService;
import com.backend.ncba.BE_Demo.service.ReferenceDataCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {CountryController.class, ReferenceDataController.class, CacheController.class})
class CountryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CountryService countryService;

    @MockitoBean
    private ReferenceDataCache referenceDataCache;

    private final CountryDto kenya = new CountryDto(
            "KE", "Kenya", "Nairobi", "254", "AF", "Africa",
            "KES", "Kenyan Shilling", "http://flag.ke",
            List.of(new LanguageDto("SW", "Swahili")));

    @BeforeEach
    void setUp() {
        PagedResponse<CountryDto> page = new PagedResponse<>(
                List.of(kenya), 0, 20, 1, 1, true, true);
        when(countryService.getCountries(any(), any(), any(), any(),
                anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(page);
        when(countryService.getCountryByIsoCode("KE")).thenReturn(kenya);
        when(countryService.getCountryByIsoCode("XX"))
                .thenThrow(new ResourceNotFoundException("Country not found with ISO code: XX"));
        when(countryService.getCountriesByCurrency("KES")).thenReturn(List.of(kenya));
        when(countryService.getContinents()).thenReturn(List.of(new ContinentDto("AF", "Africa")));
        when(countryService.getCurrencies()).thenReturn(List.of(new CurrencyDto("KES", "Kenyan Shilling")));
        when(countryService.getLanguages()).thenReturn(List.of(new LanguageDto("SW", "Swahili")));
    }

    @Test
    void getCountries_returns200WithPaginatedBody() throws Exception {
        mockMvc.perform(get("/api/v1/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].isoCode").value("KE"));
    }

    @Test
    void getCountries_withNameFilter_callsServiceCorrectly() throws Exception {
        mockMvc.perform(get("/api/v1/countries").param("name", "Ken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Kenya"));
    }

    @Test
    void getCountryByIsoCode_validCode_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/countries/KE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Kenya"));
    }

    @Test
    void getCountryByIsoCode_unknownCode_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/countries/XX"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getCountriesByCurrency_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/countries/currency/KES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isoCode").value("KE"));
    }

    @Test
    void getContinents_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/continents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("AF"));
    }

    @Test
    void getCurrencies_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isoCode").value("KES"));
    }

    @Test
    void getLanguages_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isoCode").value("SW"));
    }

    @Test
    void getCountries_invalidPageSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/countries").param("size", "200"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCountries_invalidDirection_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/countries").param("direction", "SIDEWAYS"))
                .andExpect(status().isBadRequest());
    }
}
