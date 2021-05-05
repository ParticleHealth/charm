package com.particlehealth.tools.Sample;

import java.io.*;
import java.time.*;
import java.util.*;

import com.particlehealth.tools.models.*;
import com.particlehealth.tools.process.GenerateDocument;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.mdht.uml.cda.util.CDAUtil;
import org.eclipse.mdht.uml.cda.util.ValidationResult;
import org.openhealthtools.mdht.uml.cda.consol.*;


public class Sample {

    public static void main(String[] args){

        GenerateDocument generateDocument = new GenerateDocument();
        ContinuityOfCareDocument2 doc = generateDocument.createCCD(createOrganization(), createPatientData());

        ValidationResult result = new ValidationResult();
      //  CDAUtil.validate(doc, result);

        System.out.println("\n***** Sample validation results *****");
        for (Diagnostic diagnostic : result.getErrorDiagnostics()) {
            System.out.println("ERROR: " + diagnostic.getMessage());
        }

        System.out.println(
                "Number of Schema Validation Diagnostics: " + result.getSchemaValidationDiagnostics().size());
        System.out.println("Number of EMF Resource Diagnostics: " + result.getEMFResourceDiagnostics().size());
        System.out.println("Number of EMF Validation Diagnostics: " + result.getEMFValidationDiagnostics().size());
        System.out.println("Number of Total Diagnostics: " + result.getAllDiagnostics().size());

        if (!result.hasErrors()) {
            System.out.println("Document is valid");
        } else {
            System.out.println("Document is invalid");
        }

        try {
            FileWriter myWriter = new FileWriter("exampleDoc.xml");
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            CDAUtil.save(doc, outStream);
            String unescapedCCD = StringEscapeUtils.unescapeXml(new String(outStream.toByteArray()));
            myWriter.write(unescapedCCD);
            myWriter.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static OrganizationMetadata createOrganization() {
        OrganizationMetadata organizationMetadata = new OrganizationMetadata();
        organizationMetadata.setEmail("fakeTesting@testing.fake");
        organizationMetadata.setName("myFakeCompany");
        organizationMetadata.setProviderTaxonomyCode("FakeCodeFromValueset");
        organizationMetadata.setTelephone("1-234-567-8910");
        Address address = new Address();
        address.setAddressCity("Long Island");
        address.setAddressState("WA");
        address.setPostalCode("12345");
        address.setAddressLines(Arrays.asList("11 Bowery St")); //At least one address line is required
        organizationMetadata.setAddress(address);
        return organizationMetadata;
    }

    private static PatientData createPatientData() {
        PatientData pd = new PatientData();
        Address address = new Address();
        address.setAddressCity("Boston");
        address.setAddressState("CA");
        address.setPostalCode("16545");
        address.setAddressLines(Arrays.asList("123 Main St", "4D"));
        pd.setAddress(address);

        pd.setEmail("testpatient1@test.com");
        pd.setTelephone("1 234-567-8910");
        pd.setDateOfBirth("19531029"); //Should be formatted in YYYMMDD format otherwise will throw an error
        pd.setGender("M"); //Values M or F otherwise will throw an error
        pd.setFamilyName("Aufderhar");
        pd.setGivenName("Federico");
        pd.setPatientId("Patient0");

        pd.setEncounters(createEncounters());
        return pd;
    }

    private static List<PatientEncounter> createEncounters() {
        List<PatientEncounter> encounters = new ArrayList<>();
        Performer genericPerformer = createPerformer();
        PatientEncounter enc1 = new PatientEncounter();
        enc1.setEffectiveTime(OffsetDateTime.of(LocalDateTime.of(LocalDate.of(2020, 1, 10), LocalTime.now()), ZoneOffset.UTC));
        enc1.setEncounterTypeCode("99204");
        enc1.setId(UUID.randomUUID().toString());
        enc1.setPerformer(genericPerformer);
        enc1.setStage("new");

        PatientEncounter enc2 = new PatientEncounter();
        enc2.setEffectiveTime(OffsetDateTime.of(LocalDateTime.of(LocalDate.of(2020, 11, 27), LocalTime.now()), ZoneOffset.UTC));
        enc2.setEncounterTypeCode("98966");
        enc2.setId(UUID.randomUUID().toString());
        enc2.setPerformer(genericPerformer);
        enc2.setStage("pre-op");

        PatientEncounter enc3 = new PatientEncounter();
        enc3.setEffectiveTime(OffsetDateTime.of(LocalDateTime.of(LocalDate.of(2021, 4, 1), LocalTime.now()), ZoneOffset.UTC));
        enc3.setEncounterTypeCode("99205");
        enc3.setId(UUID.randomUUID().toString());
        enc3.setPerformer(genericPerformer);
        enc3.setStage("post-op");

        encounters.add(enc1);
        encounters.add(enc2);
        encounters.add(enc3);
        return encounters;
    }

    private static Performer createPerformer() {
        Performer p = new Performer();
        p.setFamilyName("Medico");
        p.setGivenName("Helga");
        p.setProviderTaxonomyCode("101YP2500X");
        Address address = new Address();
        address.setAddressCity("Wilmington");
        address.setAddressState("AZ");
        address.setPostalCode("09675");
        address.setAddressLines(Arrays.asList("38 Smith St", "Unit 6"));
        p.setNpi("1234567");
        p.setAddress(address);
        return p;
    }

}
