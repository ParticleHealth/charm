package com.particlehealth.tools.models;

import lombok.Getter;
import lombok.Setter;
import org.eclipse.mdht.uml.cda.CDAFactory;
import org.eclipse.mdht.uml.cda.Organization;
import org.eclipse.mdht.uml.hl7.datatypes.DatatypesFactory;
import org.eclipse.mdht.uml.hl7.datatypes.ON;

@Getter @Setter
public class OrganizationMetadata {

    String name;
    String npi;

    Address address;
    String telephone;
    String email;
    //This is a required field that should be derived from this valueset: https://www.hl7.org/fhir/valueset-provider-taxonomy.html
    String providerTaxonomyCode;

    public Organization createOrganization() {
        Organization organization = CDAFactory.eINSTANCE.createOrganization();

        ON on = DatatypesFactory.eINSTANCE.createON();
        on.addText(name);
        organization.getNames().add(on);

        organization.getAddrs().add(address.createAddress());
        return organization;
    }
}
