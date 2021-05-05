package com.particlehealth.tools.models;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter @Setter
public class PatientData {

    String patientId;
    String givenName;
    String familyName;
    String dateOfBirth;
    //Gender must be "M" or "F"
    String gender;
    Address address;
    String telephone;
    String email;
    String ssn;
    List<PatientEncounter> encounters;

    public String getFirstEncounterTime() {
        if (encounters == null || encounters.isEmpty())
            return "";
        OffsetDateTime earliest = null;
        for (PatientEncounter enc : encounters) {
            if (earliest == null) {
                earliest = enc.getEffectiveTime();
            } else {
                if (earliest.isAfter(enc.getEffectiveTime()))
                    earliest = enc.getEffectiveTime();
            }
        }
        return OffsetDateTime.ofInstant(earliest.toInstant(), ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

}