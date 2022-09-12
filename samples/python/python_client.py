# import relevant packages
import requests
import time
import argparse
import sys
from collections import Counter
from fhirclient import client
from fhirclient import auth
from fhirclient import models


def get_auth(client_id, client_secret, base_url) -> str:
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


def create_person():
    """make example fhir person"""
    example_person = models.person.Person()
    example_person.gender = "Male"

    person_name = models.humanname.HumanName()
    person_name.family = "Klein"
    person_name.given = ["Quinton"]
    example_person.name = [person_name]

    person_address = models.address.Address()
    person_address.city = "Amesbury"
    person_address.line = ["629 Schuster Common"]
    person_address.postalCode = "01913"
    person_address.state = "MA"
    example_person.address = [person_address]

    person_bday = models.fhirdate.FHIRDate()
    person_bday.origval = "1967-10-20"
    example_person.birthDate = person_bday

    person_ident = models.identifier.Identifier()
    person_ident.type = models.codeableconcept.CodeableConcept()
    person_ident.type.text = "SSN"
    person_ident.value = "123-45-6789"
    example_person.identifier = [person_ident]

    person_contact = models.contactpoint.ContactPoint()
    person_contact.system = "phone"
    person_contact.value = "1-234-567-8910"
    example_person.telecom = [person_contact]

    return example_person


def post_person_to_server(example_person, smart_client):
    # post new person to server with client
    person_loaded = models.person.Person.create(example_person, smart_client.server)
    patient_id = person_loaded["id"]

    return patient_id


def post_query(smart_client, patient_id):
    # post query for new person with client:
    path = "Patient/" + patient_id + "/$query"
    data = {
        "resourceType": "Parameters",
        "parameter": [{"name": "purpose", "valueString": "TREATMENT"}],
    }
    post_response = smart_client.server.post_json(path, data)

    return post_response


def get_query_status():
    status_url = "https://sandbox.particlehealth.com/R4/Patient/{patient_id}/$query"
    headers = {"Authorization": "JWT"}
    r = requests.get(status_url, headers=headers)
    print(r.json())


# set up functions for resource retrieval
# TODO replace with patient$everything
def get_patient_everything(patient_id):
    everything_url = f"Patient/{patient_id}/$everything"
    everything_refs = smart_client.server.request_json(everything_url)

    everything_content = []
    for i in everything_refs["entry"]:
        everything_content.append(i["resource"])

    return everything_content


# get medications for test patient from past year:
def get_medications(patient_id, smart_client):
    med_url = (
        "MedicationStatement?person=" + patient_id + "&effective=gt2020-04-29T01:00:00"
    )
    med_refs = smart_client.server.request_json(med_url)

    medication_content = []
    for i in med_refs["entry"]:
        medication_content.append(i["resource"])

    return medication_content


def wait_for_query_status(smart_client, patient_id):
    path = f"Person/{patient_id}/$query"
    start_time = time.time()
    max_time = 900
    get_response = None

    while time.time() - start_time < max_time:
        get_response = smart_client.server._get(path)
        if get_response.status_code == 200:
            return
        time.sleep(30)

    raise Exception("The Query has Timed Out after 15 Minutes")


if __name__ == "__main__":

    # USING AUTHENTICATION ENDPOINT TO GENERATE JWT
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", type=str, required=True)
    parser.add_argument("--client-id", type=str, required=True)
    parser.add_argument("--client-secret", type=str, required=True)

    args = parser.parse_args(sys.argv[1:])

    jwt = get_auth(args.client_id, args.client_secret, args.base_url)
    smart_client = connect_to_server(args.base_url, jwt)
    person = create_person()
    patient_id = post_person_to_server(person, smart_client)
    wait_for_query_status(smart_client, patient_id)

    patient_everything = get_patient_everything(patient_id, smart_client)
    medications = get_medications(patient_id, smart_client)
