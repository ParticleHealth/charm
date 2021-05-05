package com.particlehealth.tools.models;

import lombok.Getter;
import lombok.Setter;
import org.eclipse.mdht.uml.cda.AssignedEntity;
import org.eclipse.mdht.uml.cda.CDAFactory;
import org.eclipse.mdht.uml.cda.Performer2;
import org.eclipse.mdht.uml.cda.Person;
import org.eclipse.mdht.uml.hl7.datatypes.DatatypesFactory;
import org.eclipse.mdht.uml.hl7.datatypes.II;
import org.eclipse.mdht.uml.hl7.datatypes.PN;
import org.eclipse.mdht.uml.hl7.vocab.NullFlavor;

@Getter @Setter
public class Performer {

    String familyName;
    String givenName;
    String npi;
    //This is a required field that should be derived from this valueset: https://www.hl7.org/fhir/valueset-provider-taxonomy.html
    String providerTaxonomyCode;
    Address address;

    public Performer2 createPerformer() {
        Performer2 p = CDAFactory.eINSTANCE.createPerformer2();
        AssignedEntity assignedEntity = CDAFactory.eINSTANCE.createAssignedEntity();

        if (providerTaxonomyCode != null && providerTaxonomyCode.isEmpty()) {
            assignedEntity.setCode(DatatypesFactory.eINSTANCE.createCE(providerTaxonomyCode, "2.16.840.1.114222.4.11.1066"));
        }

        if (address != null) {
            assignedEntity.getAddrs().add(address.createAddress());
        }

        if (familyName != null && givenName != null) {
            Person person = CDAFactory.eINSTANCE.createPerson();
            PN name = DatatypesFactory.eINSTANCE.createPN();
            name.addFamily(familyName);
            name.addGiven(givenName);
            person.getNames().add(name);
        }

        II assignedEntityId = DatatypesFactory.eINSTANCE.createII("2.16.840.1.113883.4.6");
        if (npi != null) {
            assignedEntityId.setExtension(npi);
        } else {
            assignedEntityId.setNullFlavor(NullFlavor.UNK);
        }
        assignedEntity.getIds().add(assignedEntityId);

        p.setAssignedEntity(assignedEntity);
        return p;
    }

}
