package com.skillbridge.config;

import com.skillbridge.model.IdCardConverter;
import com.skillbridge.utils.CryptoUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConverterConfig {

    private final CryptoUtil cryptoUtil;

    public ConverterConfig(CryptoUtil cryptoUtil) {
        this.cryptoUtil = cryptoUtil;
    }

    @PostConstruct
    public void init() {
        IdCardConverter.setCryptoUtil(cryptoUtil);
    }
}