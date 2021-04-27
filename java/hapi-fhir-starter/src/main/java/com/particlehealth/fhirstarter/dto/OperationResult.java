package com.particlehealth.fhirstarter.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class OperationResult {

    private String transactionTime;
    private String request;
    private boolean requiresAccessToken;
    private List<OperationOutput> output;

}
