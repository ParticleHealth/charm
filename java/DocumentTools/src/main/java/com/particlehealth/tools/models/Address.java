package com.particlehealth.tools.models;

import lombok.Getter;
import lombok.Setter;
import org.eclipse.mdht.uml.hl7.datatypes.AD;
import org.eclipse.mdht.uml.hl7.datatypes.DatatypesFactory;

import java.util.List;

@Getter @Setter
public class Address {

    String postalCode;
    List<String> addressLines;
    String addressState;
    String addressCity;

    public AD createAddress() {
        AD ad = DatatypesFactory.eINSTANCE.createAD();
        ad.addCountry("USA");
        ad.addState(addressState);
        ad.addCity(addressCity);
        ad.addPostalCode(postalCode);

        if (addressLines != null && addressLines.size() > 0)
            ad.addStreetAddressLine(addressLines.get(0));

        if (addressLines != null && addressLines.size() > 1)
            ad.addStreetAddressLine(addressLines.get(1));
        return ad;
    }
}
