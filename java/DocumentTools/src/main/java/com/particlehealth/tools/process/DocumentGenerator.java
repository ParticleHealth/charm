package com.particlehealth.tools.process;

import com.particlehealth.tools.models.*;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.mdht.uml.cda.*;
import org.eclipse.mdht.uml.cda.util.CDAUtil;
import org.eclipse.mdht.uml.hl7.datatypes.*;
import org.eclipse.mdht.uml.hl7.vocab.*;
import org.openhealthtools.mdht.uml.cda.consol.*;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DocumentGenerator {

    /*
        When CDAUtil.save is run it escapes the HTML tags in the encounters.text
        generateDocument wraps the creation of the document and unescapes the tags
        Can be written to an stream and saved from here
     */
    public String generateDocument(OrganizationData orgData, PatientData patientData) {
        ContinuityOfCareDocument2 doc = createCCD(orgData, patientData);
        String unescapedCCD = "";
        try {
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            CDAUtil.save(doc, outStream);
            unescapedCCD = StringEscapeUtils.unescapeXml(new String(outStream.toByteArray()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return unescapedCCD;
    }

    public ContinuityOfCareDocument2 createCCD(OrganizationData orgData, PatientData patientData) {
        //CreationTime to be used throughout the doc
        String creationTime = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String firstEncounterTime = patientData.getFirstEncounterTime();
        // create and initialize an instance of the ContinuityOfCareDocument class
        ContinuityOfCareDocument2 doc = ConsolFactory.eINSTANCE.createContinuityOfCareDocument2().init();

        initializeHeaders(doc, patientData.getPatientId(), creationTime);
        initializeAuthor(doc, orgData, creationTime);
        initializeCustodian(doc, orgData);
        initializeDocumentationOf(doc, firstEncounterTime, creationTime);
        initializeRecordTarget(doc, patientData);
        initializeComponent(doc, patientData);

        return doc;
    }


    private void initializeHeaders(ContinuityOfCareDocument2 ccdDocument, String uuid, String creationTime) {
        //2.16.840.1.113883.10.20.22.1.1:2015-08-01
        InfrastructureRootTypeId typeId = CDAFactory.eINSTANCE.createInfrastructureRootTypeId();
        typeId.setExtension("POCD_HD000040");
        ccdDocument.setTypeId(typeId);

        ccdDocument.setLanguageCode(DatatypesFactory.eINSTANCE.createCS("en-US"));

        II id = DatatypesFactory.eINSTANCE.createII(uuid);
        ccdDocument.setId(id);

        II templateId1 = DatatypesFactory.eINSTANCE.createII("2.16.840.1.113883.10.20.22.1.1", "2015-08-01");
        II templateId2 = DatatypesFactory.eINSTANCE.createII("2.16.840.1.113883.10.20.22.1.2", "2015-08-01");

        ccdDocument.getTemplateIds().clear();
        ccdDocument.getTemplateIds().add(templateId1);
        ccdDocument.getTemplateIds().add(templateId2);

        CE code = DatatypesFactory.eINSTANCE.createCE(
                "34133-9", "2.16.840.1.113883.6.1", "LOINC", "Summarization of Episode Note");
        ccdDocument.setCode(code);

        ST title = DatatypesFactory.eINSTANCE.createST("Patient Encounters");
        ccdDocument.setTitle(title);

        TS effectiveTime = DatatypesFactory.eINSTANCE.createTS(creationTime);
        ccdDocument.setEffectiveTime(effectiveTime);
        CE confidentialityCode = DatatypesFactory.eINSTANCE.createCE("N", "2.16.840.1.113883.5.25");
        confidentialityCode.setCodeSystemName("Confidentiality");
        confidentialityCode.setDisplayName("Normal");
        ccdDocument.setConfidentialityCode(confidentialityCode);
    }

    private void initializeRecordTarget(ContinuityOfCareDocument2 ccdDocument, PatientData patientData) {
        RecordTarget recordTarget = CDAFactory.eINSTANCE.createRecordTarget();
        PatientRole patientRole = CDAFactory.eINSTANCE.createPatientRole();
        Patient patient = CDAFactory.eINSTANCE.createPatient();
        ccdDocument.getRecordTargets().add(recordTarget);
        recordTarget.setPatientRole(patientRole);
        patientRole.setPatient(patient);

        patientRole.getIds().add(DatatypesFactory.eINSTANCE.createII(patientData.getPatientId()));

        patientRole.getAddrs().add(patientData.getAddress().createAddress());
        patientRole.getTelecoms().addAll(createTelecoms(patientData.getEmail(), patientData.getTelephone()));

        PN name = DatatypesFactory.eINSTANCE.createPN();
        name.addFamily(patientData.getFamilyName());
        name.addGiven(patientData.getGivenName());
        patient.getNames().add(name);

        patient.setAdministrativeGenderCode(DatatypesFactory.eINSTANCE.createCE(patientData.getGender(), "2.16.840.1.113883.5.1"));
        patient.setBirthTime(DatatypesFactory.eINSTANCE.createTS(patientData.getDateOfBirth()));

        CE raceCode = DatatypesFactory.eINSTANCE.createCE();
        raceCode.setNullFlavor(NullFlavor.UNK);
        patient.setRaceCode(raceCode);

        CE ethnicGroupCode = DatatypesFactory.eINSTANCE.createCE();
        ethnicGroupCode.setNullFlavor(NullFlavor.UNK);
        patient.setEthnicGroupCode(ethnicGroupCode);

    }

    private void initializeAuthor(ContinuityOfCareDocument2 cda, OrganizationData orgData, String creationTime) {
        Author author = CDAFactory.eINSTANCE.createAuthor();
        AssignedAuthor assignedAuthor = CDAFactory.eINSTANCE.createAssignedAuthor();

        //Set ID to NullFlavor.NA when there is a RepresentedOrganization and no AssignedPerson/Device
        assignedAuthor.getIds().add(DatatypesFactory.eINSTANCE.createII(NullFlavor.NA));
        //NPI Unknown root
        II npiRoot = DatatypesFactory.eINSTANCE.createII();
        npiRoot.setNullFlavor(NullFlavor.UNK);
        npiRoot.setRoot("2.16.840.1.113883.4.6");
        author.getTemplateIds().add(npiRoot);

        IVL_TS effectiveTime = DatatypesFactory.eINSTANCE.createIVL_TS(creationTime);
        author.setTime(effectiveTime);

        CE assigningAuthorCode = DatatypesFactory.eINSTANCE.createCE();
        assigningAuthorCode.setCodeSystem("2.16.840.1.113883.6.10.1");
        String code = orgData.getProviderTaxonomyCode();
        String codeSystemName = "Healthcare Provider Taxonomy (HIPAA)";
        assigningAuthorCode.setCode(code);
        assigningAuthorCode.setCodeSystemName(codeSystemName);
        assignedAuthor.setCode(assigningAuthorCode);

        assignedAuthor.getTelecoms().addAll(createTelecoms(orgData.getEmail(), orgData.getTelephone()));
        assignedAuthor.setRepresentedOrganization(orgData.createOrganization());
        assignedAuthor.getAddrs().add(orgData.getAddress().createAddress());

        author.setAssignedAuthor(assignedAuthor);
        cda.getAuthors().add(author);
    }

    private void initializeCustodian(ContinuityOfCareDocument2 ccdDocument, OrganizationData orgData) {
        Custodian custodian = CDAFactory.eINSTANCE.createCustodian();
        AssignedCustodian assignedCustodian = CDAFactory.eINSTANCE.createAssignedCustodian();
        CustodianOrganization custodianOrganization = CDAFactory.eINSTANCE.createCustodianOrganization();

        ON oname = DatatypesFactory.eINSTANCE.createON();
        oname.addText(orgData.getName());
        custodianOrganization.setName(oname);

        custodianOrganization.setAddr(orgData.getAddress().createAddress());
        //CustodianOrganization only takes 1 TEL object, either email or telephone will be used. At least 1 is required
        custodianOrganization.setTelecom(createTelecoms(orgData.getEmail(), orgData.getTelephone()).get(0));

        //NPI Unknown root
        II npiId = DatatypesFactory.eINSTANCE.createII();
        npiId.setNullFlavor(NullFlavor.UNK);
        npiId.setRoot("2.16.840.1.113883.4.6");
        custodianOrganization.getIds().add(npiId);

        assignedCustodian.setRepresentedCustodianOrganization(custodianOrganization);
        custodian.setAssignedCustodian(assignedCustodian);
        ccdDocument.setCustodian(custodian);
    }

    private void initializeDocumentationOf(ContinuityOfCareDocument2 ccdDocument, String firstEncounterTime, String creationTime) {
        DocumentationOf documentationOf = CDAFactory.eINSTANCE.createDocumentationOf();
        documentationOf.setTypeCode(ActRelationshipType.DOC);

        ServiceEvent serviceEvent = CDAFactory.eINSTANCE.createServiceEvent();
        serviceEvent.setClassCode(ActClassRoot.PCPR);

        //This maps the time from the first encounter to the time the document is created.
        IVL_TS effectiveTime = DatatypesFactory.eINSTANCE.createIVL_TS();
        IVXB_TS t1 = DatatypesFactory.eINSTANCE.createIVXB_TS();
        IVXB_TS t2 = DatatypesFactory.eINSTANCE.createIVXB_TS();
        if (firstEncounterTime.isEmpty())
            firstEncounterTime = creationTime;
        t1.setValue(firstEncounterTime);
        t2.setValue(creationTime);
        effectiveTime.setLow(t1);
        effectiveTime.setHigh(t2);
        serviceEvent.setEffectiveTime(effectiveTime);

        documentationOf.setServiceEvent(serviceEvent);
        ccdDocument.getDocumentationOfs().add(documentationOf);
    }

    private void initializeComponent(ContinuityOfCareDocument2 ccdDocument, PatientData patientData) {
        //Allergies
        initializeAllergiesSection(ccdDocument);

        //Medications
        initializeMedicationsSection(ccdDocument);

        //ProblemSection
        initializeProblemSection(ccdDocument);

        //Results
        initializeResultsSection(ccdDocument);

        //Social History
        initializeSocialHistory(ccdDocument);

        //Vital Signs
        initializeVitalSigns(ccdDocument);

        //Generate Encounters
        if (patientData.getEncounters() != null)
            initializeEncounters(ccdDocument, patientData.getEncounters());
    }


    private void initializeAllergiesSection(ContinuityOfCareDocument2 ccdDocument) {
        AllergiesSection2 section = ConsolFactory.eINSTANCE.createAllergiesSection2().init();
        ccdDocument.addSection(section);
        section.setTitle(DatatypesFactory.eINSTANCE.createST("Allergies Section"));
        StrucDocText text = CDAFactory.eINSTANCE.createStrucDocText();
        text.addText("No Allergies Data");
        section.setText(text);
        section.setNullFlavor(NullFlavor.NI);
        section.setCode(DatatypesFactory.eINSTANCE
                .createCE("48765-2", "2.16.840.1.113883.6.1", "LOINC", "Allergies, adverse reactions, alerts"));
    }

    private void initializeMedicationsSection(ContinuityOfCareDocument2 ccdDocument) {
        MedicationsSection2 section = ConsolFactory.eINSTANCE.createMedicationsSection2().init();
        ccdDocument.addSection(section);
        section.setTitle(DatatypesFactory.eINSTANCE.createST("Medications Section"));
        StrucDocText text = CDAFactory.eINSTANCE.createStrucDocText();
        text.addText("No Medications Data");
        section.setText(text);
        section.setNullFlavor(NullFlavor.NI);
        section.setCode(DatatypesFactory.eINSTANCE
                .createCE("10160-0", "2.16.840.1.113883.6.1", "LOINC", "History of medication use"));
    }

    private void initializeProblemSection(ContinuityOfCareDocument2 ccdDocument) {
        ProblemSection2 section = ConsolFactory.eINSTANCE.createProblemSection2().init();
        ccdDocument.addSection(section);
        section.setTitle(DatatypesFactory.eINSTANCE.createST("Problem Section"));
        StrucDocText text = CDAFactory.eINSTANCE.createStrucDocText();
        text.addText("No Problems Data");
        section.setText(text);
        section.setNullFlavor(NullFlavor.NI);
        section.setCode(DatatypesFactory.eINSTANCE
                .createCE("11450-4", "2.16.840.1.113883.6.1", "LOINC", "Problem list"));
    }

    private void initializeResultsSection(ContinuityOfCareDocument2 ccdDocument) {
        ResultsSection2 section = ConsolFactory.eINSTANCE.createResultsSection2().init();
        ccdDocument.addSection(section);
        section.setTitle(DatatypesFactory.eINSTANCE.createST("Results Section"));
        StrucDocText text = CDAFactory.eINSTANCE.createStrucDocText();
        text.addText("No Results Data");
        section.setText(text);
        section.setNullFlavor(NullFlavor.NI);
        section.setCode(DatatypesFactory.eINSTANCE
                .createCE("30954-2", "2.16.840.1.113883.6.1", "LOINC", "Relevant diagnostic tests and/or laboratory data"));
    }

    private void initializeSocialHistory(ContinuityOfCareDocument2 ccdDocument) {
        SocialHistorySection2 section = ConsolFactory.eINSTANCE.createSocialHistorySection2().init();
        ccdDocument.addSection(section);
        section.setTitle(DatatypesFactory.eINSTANCE.createST("Social History Section"));
        StrucDocText text = CDAFactory.eINSTANCE.createStrucDocText();
        text.addText("No Social History Data");
        section.setText(text);
        section.setCode(DatatypesFactory.eINSTANCE
                .createCE("29762-2", "2.16.840.1.113883.6.1", "LOINC", "Social History"));
    }

    private void initializeVitalSigns(ContinuityOfCareDocument2 ccdDocument) {
        VitalSignsSection2 section = ConsolFactory.eINSTANCE.createVitalSignsSection2().init();
        ccdDocument.addSection(section);
        section.setTitle(DatatypesFactory.eINSTANCE.createST("Vital Signs Section"));
        StrucDocText text = CDAFactory.eINSTANCE.createStrucDocText();
        text.addText("No Vital Signs Data");
        section.setText(text);
        section.setNullFlavor(NullFlavor.NI);
        section.setCode(DatatypesFactory.eINSTANCE
                .createCE("8716-3", "2.16.840.1.113883.6.1", "LOINC", "Vital Signs"));
    }

    private void initializeEncounters(ContinuityOfCareDocument2 ccdDocument, List<PatientEncounter> encounters) {
        EncountersSection2 section = ConsolFactory.eINSTANCE.createEncountersSection2().init();
        ccdDocument.addSection(section);
        section.setTitle(DatatypesFactory.eINSTANCE.createST("Encounters Section"));
        StrucDocText text = CDAFactory.eINSTANCE.createStrucDocText();
        section.setCode(DatatypesFactory.eINSTANCE
                .createCE("46240-8", "2.16.840.1.113883.6.1", "LOINC", "Encounters"));

        int count = 1;
        StringBuilder sb = prepareTable();
        for (PatientEncounter patientEncounter : encounters) {
            section.addEncounter(createEncounterActivity(patientEncounter, count));

            sb.append("<tr ID=\"#Encounter").append(count).append("\">");
            sb.append("<td>").append(patientEncounter.getPerformer().getGivenName() + patientEncounter.getPerformer().getFamilyName()).append("</td>");
            sb.append("<td>").append(patientEncounter.getStage()).append("</td>");
            sb.append("<td>").append(patientEncounter.getEffectiveTime()).append("</td></tr>");

            count++;
        }

        text.addText(closeTable(sb));
        section.setText(text);
    }

    private EncounterActivity2 createEncounterActivity(PatientEncounter enc, Integer i) {
        EncounterActivity2 activity = ConsolFactory.eINSTANCE.createEncounterActivity2().init();
        activity.setClassCode(ActClass.ENC);
        activity.setMoodCode(x_DocumentEncounterMood.EVN);
        activity.getIds().add(DatatypesFactory.eINSTANCE.createII(enc.getId()));
        String effectiveTime = OffsetDateTime.ofInstant(enc.getEffectiveTime().toInstant(), ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        activity.setEffectiveTime(DatatypesFactory.eINSTANCE.createIVL_TS(effectiveTime));

        CD code = DatatypesFactory.eINSTANCE.createCD();
        code.setCode(enc.getEncounterTypeCode());
        code.setCodeSystem("2.16.840.1.113883.6.12");
        ED ed = DatatypesFactory.eINSTANCE.createED();
        ed.addText("#Encounter" + i);
        code.setOriginalText(DatatypesFactory.eINSTANCE.createED());
        activity.setCode(DatatypesFactory.eINSTANCE.createCD());

        activity.getPerformers().add(enc.getPerformer().createPerformer());
        return activity;
    }

    private List<TEL> createTelecoms(String email, String telephone) {
        List<TEL> tels = new ArrayList<>();
        if (email != null) {
            TEL tel = DatatypesFactory.eINSTANCE.createTEL(email);
            tel.getUses().add(TelecommunicationAddressUse.H);
            tels.add(tel);
        }
        if (telephone != null) {
            TEL tel = DatatypesFactory.eINSTANCE.createTEL(telephone);
            tel.getUses().add(TelecommunicationAddressUse.HP);
            tels.add(tel);
        }
        return tels;
    }

    private StringBuilder prepareTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("<table>");
        sb.append("<tr>");
        sb.append("<th>Doctor</th>");
        sb.append("<th>Stage</th>");
        sb.append("<th>Encounter Date</th>");
        sb.append("</tr>");
        return sb;
    }

    private String closeTable(StringBuilder sb) {
        sb.append("</table>");
        return sb.toString();
    }

}
