using System;
using Hl7.Fhir.Rest;
using FHIRModel = Hl7.Fhir.Model;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Threading.Tasks;
using Hl7.Fhir.Serialization;

namespace csharp_fhir_client
{
    class Program
    {
        private static readonly HttpClient client = new HttpClient();
        static async Task Main(string[] args)
        {
            string jwt = await getJWTAsync();
            var particleHost = Environment.GetEnvironmentVariable("PARTICLE_HOST");
            var settings = new FhirClientSettings
            {
                Timeout = 6000,
                PreferredFormat = ResourceFormat.Json,
                VerifyFhirVersion = false,
                PreferredReturn = Prefer.ReturnMinimal
            };

            using (var handler = new HttpClientEventHandler())
            {
                using (FhirClient fhirClient = new FhirClient(particleHost + "/R4/", settings, handler))
                {
                    var respStatus = 0;
                    handler.OnBeforeRequest += (sender, e) =>
                    {
                        // Console.WriteLine("Sent request: " + e.RawRequest.ToString());
                        e.RawRequest.Headers.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
                    };

                    handler.OnAfterResponse += (sender, e) =>
                    {
                        respStatus = (int)e.RawResponse.StatusCode;
                        // Console.WriteLine("Received response: " + e.RawResponse.Content.ReadAsStringAsync().Result);
                    };

                    /*** Example 1: Creating a Patient ***/
                    var patientID = CreatePatient(fhirClient);
                    Console.WriteLine("patientID: {0}\n", patientID);

                    /*** Example 2: Querying a Patient ***/
                    var queryURI = await QueryPatientAsync(fhirClient, patientID);
                    do
                    {
                        var taskDelay = Task.Delay(5000);
                        await taskDelay;
                        try
                        {
                            await fhirClient.OperationAsync(queryURI, "query", null, true);
                        }
                        catch (Exception e)
                        {
                            if (respStatus != 202) Console.WriteLine(e);
                            continue;
                        }
                    } while (respStatus != 200);
                    Console.WriteLine("Success! Query completed.\n");

                    /*** Example 3: Fetching All Medication Statements for a Patient ***/
                    var medicationStatementsFile = await GetMedicationStatementsAsync(fhirClient, patientID);
                    if (medicationStatementsFile != null) Console.WriteLine("file: {0}\n", medicationStatementsFile);

                    /*** Example 4: Fetching All Resources for a Patient ***/
                    var patientEverythingFile = await GetPatientEverythingAsync(fhirClient, patientID);
                    if (patientEverythingFile != null) Console.WriteLine("file: {0}\n", patientEverythingFile);
                }
            }
        }
        private static async Task<String> getJWTAsync()
        {
            string prevJWT = Environment.GetEnvironmentVariable("JWT");
            if (prevJWT != null) return prevJWT;

            string particleHost = Environment.GetEnvironmentVariable("PARTICLE_HOST");
            string clientSecret = Environment.GetEnvironmentVariable("CLIENT_SECRET");
            string clientId = Environment.GetEnvironmentVariable("CLIENT_ID");

            try
            {
                var authRequest = new HttpRequestMessage();
                authRequest.RequestUri = new Uri(particleHost + "/auth");
                authRequest.Method = HttpMethod.Get;
                authRequest.Headers.Add("client-id", clientId);
                authRequest.Headers.Add("client-secret", clientSecret);

                HttpResponseMessage response = await client.SendAsync(authRequest);
                var currJWT = await response.Content.ReadAsStringAsync();
                Environment.SetEnvironmentVariable("JWT", currJWT);
                Console.WriteLine("Authentication successful.\n");
                return currJWT;
            }
            catch (Exception e)
            {
                Console.WriteLine("\nException Caught!");
                Console.WriteLine("Message: {0} ", e.Message);
                return null;
            }
        }
        private static string CreatePatient(FhirClient fhirClient)
        {
            try
            {
                FHIRModel.Patient patient = GeneratePatientData();
                var resp = fhirClient.Create<FHIRModel.Patient>(patient);
                Console.WriteLine("Patient created.");
                return resp.IdElement.ToString();
            }
            catch (Exception e)
            {
                Console.WriteLine("\nException Caught!");
                Console.WriteLine("Message: {0} ", e.ToString());
                return null;
            }
        }
        private static FHIRModel.Patient GeneratePatientData()
        {
            var patient = new FHIRModel.Patient();
            var name = new FHIRModel.HumanName().WithGiven("Quinton").AndFamily("Klein");
            name.Use = FHIRModel.HumanName.NameUse.Official;
            patient.Name.Add(name);
            patient.Gender = FHIRModel.AdministrativeGender.Male;
            patient.BirthDate = "1967-10-20";
            var address = new FHIRModel.Address()
            {
                Line = new string[] { "629 Schuster Common" },
                City = "Amesbury",
                State = "MA",
                PostalCode = "01913",
                Country = "USA"
            };
            patient.Address.Add(address);
            return patient;
        }
        private static async Task<Uri> QueryPatientAsync(FhirClient fhirClient, string patientID)
        {
            try
            {
                Console.WriteLine("Querying patient...");
                string interpolated = Environment.GetEnvironmentVariable("PARTICLE_HOST") + "/R4/Patient/" + patientID;
                var queryURI = new Uri(interpolated);
                var parameters = new FHIRModel.Parameters();
                var treatment = new FHIRModel.FhirString("TREATMENT");
                parameters.Add("purpose", treatment);
                var resp = await fhirClient.OperationAsync(queryURI, "query", parameters, false);
                return queryURI;
            }
            catch (Exception e)
            {
                Console.WriteLine("\nException Caught!");
                Console.WriteLine("Message: {0} ", e.Message);
                return null;
            }
        }
        private static async Task<string> GetMedicationStatementsAsync(FhirClient fhirClient, string patientID)
        {
            try
            {
                Console.WriteLine("Fetching patient's medication statements...\n");
                string q = "patient=" + patientID;
                var results = await fhirClient.SearchAsync<FHIRModel.MedicationStatement>(new string[] { q });
                string filename = @"./medicationStatementsBundle.json";
                var collection = new FHIRModel.Bundle();
                collection.Type = FHIRModel.Bundle.BundleType.Collection;

                while (results != null)
                {
                    if (results.Total == 0)
                    {
                        Console.WriteLine("No results found.\n");
                        return null;
                    }

                    foreach (var entry in results.Entry)
                    {
                        var entryId = entry.Resource.Id;
                        var resourceType = entry.Resource.TypeName.ToString();
                        var location = resourceType + "/" + entryId;
                        var fileData = await fhirClient.ReadAsync<FHIRModel.MedicationStatement>(location);
                        var newEntry = new FHIRModel.Bundle.EntryComponent();
                        newEntry.Resource = fileData;
                        collection.Entry.Add(newEntry);
                    }
                    string json = collection.ToJson();
                    System.IO.File.AppendAllText(filename, json);
                    results = fhirClient.Continue(results);
                }
                Console.WriteLine("Success! Patient's medication statements retrieved.");
                return filename;
            }
            catch (Exception e)
            {
                Console.WriteLine("\nException Caught!");
                Console.WriteLine("Message: {0} ", e.Message);
                return null;
            }
        }

        private static async Task<string> GetPatientEverythingAsync(FhirClient fhirClient, string patientID)
        {
            try
            {
                Console.WriteLine("Fetching all of the patient's resources...\n");
                string interpolated = Environment.GetEnvironmentVariable("PARTICLE_HOST") + "/R4/Patient/" + patientID;
                var queryURI = new Uri(interpolated);
                var bundle = await fhirClient.OperationAsync(queryURI, "everything", null, true);
                string filename = @"./patientEverythingBundle.json";

                if (bundle != null)
                {
                    var jsonData = bundle.ToJson();
                    System.IO.File.AppendAllText(filename, jsonData);
                    Console.WriteLine("Success! Patient's resources retrieved.");
                    return filename;
                }
                else
                {
                    Console.WriteLine("No results found.");
                    return null;
                }
            }
            catch (Exception e)
            {
                Console.WriteLine("\nException Caught!");
                Console.WriteLine("Message: {0} ", e.Message);
                return null;
            }
        }
    }
}
