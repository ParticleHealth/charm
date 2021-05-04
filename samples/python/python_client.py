#import relevant packages
import requests
import time
import argparse
import sys
from collections import Counter
import fhirclient
from fhirclient import client
from fhirclient import server
from fhirclient import auth
import fhirclient.models.person as p
import fhirclient.models.humanname as hn
import fhirclient.models.address as add
import fhirclient.models.fhirdate as fd
import fhirclient.models.identifier as ident
import fhirclient.models.codeableconcept as cc
import fhirclient.models.contactpoint as cp



#USING AUTHENTICATION ENDPOINT TO GENERATE JWT
parser = argparse.ArgumentParser()
parser.add_argument("--base-url", type=str, required=True)
parser.add_argument("--client-id", type=str, required=True)
parser.add_argument("--client-secret", type=str, required=True)
args = parser.parse_args(sys.argv[1:])

print('\n' + 'Generating JWT using Client ID and Client Secret Provided...')
client_id = args.client_id
client_secret = args.client_secret
url = args.base_url + '/auth/'
headers = {'client-id': client_id,
           'client-secret': client_secret
          }
r = requests.get(url, headers=headers)
if r.status_code == 200:
    jwt = r.text
    print('JWT: \n' + '\t' + str(jwt) + '/n')
else:
    print('Status Code: ' + str(r.status_code))
    print('Please Ensure your Client ID and Client Secret are Valid')
    exit()

#CONNECTING CLIENT TO R4 SERVER AND AUTHENTICATE WITH JWT
print('Authenticating with JWT...')
settings = {
    'app_id': 'Particle',
    'api_base': args.base_url + '/R4/'
}
smart = client.FHIRClient(settings=settings)
o_auth = auth.FHIROAuth2Auth()
o_auth.access_token = jwt
o_auth.signed_headers(headers = {})
#point client auth to our OAuth2 build
smart.server.auth = o_auth
print("Authentication Process Complete" + '\n')



#CREATING NEW PERSON WITH CLIENT:
print('Creating Person JSON to POST...')
my_person = p.Person()
my_person.gender = 'Male'
person_name = hn.HumanName()
person_name.family = 'Klein'
person_name.given = ['Quinton']
my_person.name = [person_name]
person_address = add.Address()
person_address.city = "Amesbury"
person_address.line = ['629 Schuster Common']
person_address.postalCode = '01913'
person_address.state = 'MA'
my_person.address = [person_address]
person_bday = fd.FHIRDate()
person_bday.origval = '1967-10-20'
my_person.birthDate = person_bday
person_ident = ident.Identifier()
person_ident.type = cc.CodeableConcept()
person_ident.type.text = 'SSN'
person_ident.value = '123-45-6789'
my_person.identifier = [person_ident]
person_contact = cp.ContactPoint()
person_contact.system = 'phone'
person_contact.value = '1-234-567-8910'
my_person.telecom = [person_contact]
print('Person to POST: \n')
print('\t' + str(my_person.as_json()) + '\n')



#POST NEW PERSON TO SERVER WITH CLIENT
print('Posting Person to FHIR Server...')
Person_Loaded = p.Person.create(my_person, smart.server)
person_loaded_id = Person_Loaded["id"]
print('Person ID returned from FHIR server: \n')
print('\t' + str(person_loaded_id) + '\n')

#read uploaded person to ensure upload success:
print('Checking Upload Success...')
patient = p.Person.read(person_loaded_id, smart.server)
print('Results from Reading Person Uploaded: \n')
print('\t' + str(patient.as_json()) + '\n')



#POST QUERY FOR NEW PERSON WITH CLIENT:
print("Initiating POST Query for Person...")
test_person_id = person_loaded_id
path = 'Person/' + test_person_id + "/$query"
data = { "resourceType": "Parameters", "parameter": [{ "name": "purpose", "valueString": "TREATMENT" }] }
post_response = smart.server.post_json(path, data)
print('Status of Query for Person: \n')
print('\t' + str(post_response) + '\n')
print('Body: \n')
print('\t' + str(post_response.text) + '\n')



#SET UP FUNCTIONS FOR RESOURCE RETRIEVAL
def get_resources():
    #get all resource references from compositions, factor in pagination:
    print('Getting Resource References from Compositions...' + '\n')
    url_ext = 'Composition?person=' + test_person_id
    composition = smart.server.request_json(url_ext)

    next_page = True
    resource_references = []
    while next_page == True:
        for i in composition['entry']:
            for j in i['resource']['section']:
                for k in (j['entry']):
                    resource_references.append(k['reference'])

        #grab next page endpoint
        for i in composition['link']:
            if (i['relation']) == 'next':
                next_page = True
                next_endpoint = (i['url'][4:])
                #initiate GET call and store returned load for new pages
                composition = smart.server.request_json(next_endpoint)
            else:
                next_page = False

    #get resource counts and contents from all resource references:
    print('Getting All Resource Content from Resource References...' + '\n')
    all_resource_content = []
    all_types = []
    for i in resource_references:
        resource_content = smart.server.request_json(i)
        all_resource_content.append(resource_content)
        all_types.append(i.split('/')[0])

    print(str(len(all_types)) + ' total fhir resources')
    counter = Counter(all_types)
    print(str(len(counter)) + ' unique resource types:')
    for i in counter:
        print("\t" + i + " - " + str(counter[i]) + ' resources')

    print('\n' + 'Bundling Resource Content...' + '\n')
    for i in all_resource_content:
        print(str(i) + '\n')



#GET medications for test patient from past year:
def get_medications():
    print('Gathering Medication from the Past Year for Person...' + '\n')
    #set url with last year effective date parameter
    med_url = 'MedicationStatement?person=' + test_person_id + '&effective=gt2020-04-29T01:00:00'
    med_refs = smart.server.request_json(med_url)

    medication_content = []
    for i in med_refs['entry']:
        medication_content.append(i['resource'])

    for i in medication_content:
        print(str(i) + '\n')



#GET QUERY FOR NEW PERSON:
print('Initiating GET Query for Person...')
test_person_id = person_loaded_id
path = 'Person/' + test_person_id + "/$query"
start_time = time.time()
max_time = 900
get_response = None

while True:
    elapsed_time = time.time() - start_time
    get_response = smart.server._get(path)
    if get_response.status_code == 200:
        print('The Query is Now Complete')
        break
    if elapsed_time > max_time:
        print('The Query has Timed Out after 15 Minutes')
        break
    print('The Query is Still Running...')
    time.sleep(30)

#use results of get_response if successful to get contents and medications
if get_response.status_code == 200:
    print('Status Code: \n')
    print('\t' + str(get_response.status_code) + '\n')
    if get_response.json() is not None:
        get_resources()
        get_medications()
else:
    print("Status Code" + get_response.status_code)
