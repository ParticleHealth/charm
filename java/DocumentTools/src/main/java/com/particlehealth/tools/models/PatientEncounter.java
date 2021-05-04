package com.particlehealth.tools.models;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class PatientEncounter {

    String id;
    Performer performer;
    OffsetDateTime effectiveTime;
    String stage;
    //Required code, should come from the valueset: https://vsac.nlm.nih.gov/valueset/2.16.840.1.113883.3.88.12.80.32/expansion
    String encounterTypeCode;

}
