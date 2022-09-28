# import relevant packages
import requests
import time
import argparse
import sys
from collections import Counter
from fhirclient import client
from fhirclient import auth
from fhirclient.models import (
    patient,
    humanname,
    address,
    fhirdate,
    identifier,
    codeableconcept,
    contactpoint,
)


def get_auth(client_id, client_secret, base_url) -> str:
    """Generate JWT using client id and client secret provided from Particle Health"""
    url = base_url + "/auth/"
    headers = {"client-id": client_id, "client-secret": client_secret}
    r = requests.get(url, headers=headers)
    if r.status_code == 200:
        jwt = r.text
        return jwt
    else:
        raise Exception(
            f"Got status code {r.status_code}. Please ensure your Client ID and Client Secret are Valid"
        )


def connect_to_server(base_url: str, jwt: str):
    """Connect client to R4 server and authenticate with JWT"""
    settings = {"app_id": "Particle", "api_base": base_url + "/R4/"}
    smart_client = client.FHIRClient(settings=settings)
    o_auth = auth.FHIROAuth2Auth()
    o_auth.access_token = jwt
    o_auth.signed_headers(headers={})
    # point client auth to our OAuth2 build
    smart_client.server.auth = o_auth

    return smart_client


def create_patient():
    """make example fhir patient"""
    example_patient = patient.Patient()
    example_patient.gender = "Male"

    person_name = humanname.HumanName()
    person_name.family = "Klein"
    person_name.given = ["Quinton"]
    example_patient.name = [person_name]

    person_address = address.Address()
    person_address.city = "Amesbury"
    person_address.line = ["629 Schuster Common"]
    person_address.postalCode = "01913"
    person_address.state = "MA"
    example_patient.address = [person_address]

    person_bday = fhirdate.FHIRDate()
    person_bday.origval = "1967-10-20"
    example_patient.birthDate = person_bday

    person_ident = identifier.Identifier()
    person_ident.type = codeableconcept.CodeableConcept()
    person_ident.type.text = "SSN"
    person_ident.value = "123-45-6789"
    example_patient.identifier = [person_ident]

    person_contact = contactpoint.ContactPoint()
    person_contact.system = "phone"
    person_contact.value = "1-234-567-8910"
    example_patient.telecom = [person_contact]

    return example_patient


def post_patient_to_server(example_patient, smart_client):
    """POST the created patient to the FHIR server"""
    patient_loaded = example_patient.create(smart_client.server)
    patient_id = patient_loaded["id"]

    return patient_id


def post_query(smart_client, patient_id):
    """POST a query to the Particle Network for the newly generated Patient"""
    path = f"Patient/{patient_id}/$query"
    data = {
        "resourceType": "Parameters",
        "parameter": [{"name": "purpose", "valueString": "TREATMENT"}],
    }
    post_response = smart_client.server.post_json(path, data)

    return post_response


def check_if_more_pages(json_response):
    """Helper function to check if there is a next page link in the response"""
    for i in json_response['link']:
        if i['relation'] == 'next':
            return i['url']
    return None
        
def get_patient_everything(full_json_output, patient_id, smart_client):
    """GET all FHIR resources for the Patient using the patient $everything operation - pagination handling included"""
    everything_url = f"Patient/{patient_id}/$everything"
    json_response = smart_client.server.request_json(everything_url)

    for i in json_response["entry"]:
        full_json_output.append(i["resource"])

    next_url = check_if_more_pages(json_response)
    if next_url:
        get_patient_everything(full_json_output, next_url, patient_id)
    return full_json_output


def get_medications(patient_id, smart_client):
    """GET all medications for test patient since April 29th of 2020"""
    med_url = f"MedicationStatement?patient={patient_id}" + '&effective=gt2020-04-29T01:00:00&_count=1000'
    med_refs = smart_client.server.request_json(med_url)

    medication_content = []
    for i in med_refs["entry"]:
        medication_content.append(i["resource"])

    return medication_content


def wait_for_query_status(smart_client, patient_id, max_time: int = 900):
    """Function that polls Particle for a 200 code to indicate that the POST $query is complete and ready for FHIR resource GET calls"""
    path = f"Patient/{patient_id}/$query"
    get_response = smart_client.server._get(path)
    start_time = time.time()
    get_response = None

    while time.time() - start_time < max_time:
        get_response = smart_client.server._get(path)
        if get_response.status_code == 200:
            return
        print("waiting for query, status: ", get_response.status_code)
        time.sleep(30)

    raise Exception("The Query has Timed Out after 15 Minutes")


if __name__ == "__main__":

    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", type=str, required=True)
    parser.add_argument("--client-id", type=str, required=True)
    parser.add_argument("--client-secret", type=str, required=True)
    parser.add_argument("--timeout-seconds", default=900, type=int)

    args = parser.parse_args(sys.argv[1:])

    jwt = get_auth(args.client_id, args.client_secret, args.base_url)
    smart_client = connect_to_server(args.base_url, jwt)
    patient_posted = create_patient()
    patient_id = post_patient_to_server(patient_posted, smart_client)

    post_query(smart_client, patient_id)
    wait_for_query_status(smart_client, patient_id, args.timeout_seconds)

    full_json_output = []
    full_json_output = get_patient_everything(full_json_output, patient_id, smart_client)
    print(
        f"successfully retrieved {len(full_json_output)} resources from patient everything"
    )

    medications = get_medications(patient_id, smart_client)
    print(
        f"successfully retrieved {len(medications)} medication resources that date from april 29th, 2020"
    )
    print("in your own implementation, you could do something with these!")
