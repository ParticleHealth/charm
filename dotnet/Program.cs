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
            var particle_host = Environment.GetEnvironmentVariable("PARTICLE_HOST");
            var settings = new FhirClientSettings
            {
                Timeout = 6000,
                PreferredFormat = ResourceFormat.Json,
                VerifyFhirVersion = false,
                PreferredReturn = Prefer.ReturnMinimal
            };

            using (var handler = new HttpClientEventHandler())
            {
                using (FhirClient fhir_client = new FhirClient(particle_host + "/R4/",  settings, handler))
                {
                    var result_status = "";
                    handler.OnBeforeRequest += (sender, e) =>
                    {
                        // Console.WriteLine("Sent the following request" + e.RawRequest.ToString());
                        e.RawRequest.Headers.Authorization = new AuthenticationHeaderValue("Bearer", jwt);
                    };

                    handler.OnAfterResponse += (sender, e) =>
                    {
                        result_status = e.RawResponse.ReasonPhrase.ToString();
                        // Console.WriteLine("Received response with content: " + e.RawResponse.Content.ReadAsStringAsync().Result);
                    };

                    var person_id = CreatePerson(fhir_client);
                    Console.WriteLine("person id is: {0}", person_id);

                    var query_uri = await QueryPersonAsync(fhir_client, person_id);
                    Console.WriteLine("type operation for: {0}", query_uri);

                    
                    do {
                      var delayTask = Task.Delay(10000);
                      await delayTask;
                      try {
                          await fhir_client.OperationAsync(query_uri, "query", null, true) ;
                      } catch (Exception e) {
                          //TODO: Figure out this workflow
                          //OperationAsync expects a resource so it always throws an exception?
                          Console.WriteLine(e);
                          continue;
                      }
                    } while (result_status != "OK");

                    Console.WriteLine("query complete! fetching data and writing to files...");
                    var everything_bundle = await GetEverythingAsync(fhir_client, person_id);
                    var medication_bundle = await GetMedicationStatementsAsync(fhir_client, person_id);
                    Console.WriteLine("Done!");
                }
            }
        }


        private static async Task<Uri> QueryPersonAsync(FhirClient fhir_client, string person_id)
        { 
            try {
                string interpolated = Environment.GetEnvironmentVariable("PARTICLE_HOST") + "/R4/Person/" + person_id;
                var query_uri = new Uri(interpolated);
                var parameters = new FHIRModel.Parameters();
                var treatment = new FHIRModel.FhirString("TREATMENT");
                parameters.Add("purpose", treatment);
                var resp = await fhir_client.OperationAsync(query_uri, "query", parameters, false);
                return query_uri; 
            } catch(Exception e) {
                Console.WriteLine("\nException Caught!");
                Console.WriteLine("Message :{0} ",e.Message);
                return null;
            }
        }

        private static async Task<string> GetEverythingAsync(FhirClient fhir_client, string person_id)
        { 
            try {
                string q = "person=" + person_id;
                // var result = fhir_client.WholeSystemSearch(new string[] {q});
                var results = await fhir_client.SearchAsync<FHIRModel.Composition>(new string[] { q });
                string filename = @"./" + person_id + "_everything_bundle.json";
                var collection = new FHIRModel.Bundle();
                collection.Type = FHIRModel.Bundle.BundleType.Collection;

                while( results != null )
                {
                    foreach (var entry in results.Entry)
                    {
                        var entry_id = entry.Resource.Id;
                        var resource_type = entry.Resource.TypeName.ToString();
                        var location = resource_type + "/" + entry_id;
                        var composition_data = await fhir_client.ReadAsync<FHIRModel.Composition>(location);

                        foreach (var section in composition_data.Section)
                        {
                            foreach (var reference in section.Entry)
                            {
                                var read_url = reference.Url;
                                string interpolated = Environment.GetEnvironmentVariable("PARTICLE_HOST") + "/R4/" + read_url;
                                var query_uri = new Uri(interpolated);
                                var data = await fhir_client.GetAsync(query_uri);
                                var new_entry = new FHIRModel.Bundle.EntryComponent();
                                new_entry.Resource = data;
                                collection.Entry.Add(new_entry);
                            }
                        }
                    }
                    var json_data = collection.ToJson();
                    System.IO.File.AppendAllText(filename, json_data);
                    Console.WriteLine("total results = " + results.Total);
                    results = fhir_client.Continue(results);
                }
                return "";
            } catch(Exception e) {
                Console.WriteLine("\nException Caught!");
                Console.WriteLine("Message :{0} ",e.Message);
                return null;
            }
        }

        private static async Task<string> GetMedicationStatementsAsync(FhirClient fhir_client, string person_id)
        { 
            try {
                string q = "person=" + person_id;
                // var result = fhir_client.WholeSystemSearch(new string[] {q});
                var results = await fhir_client.SearchAsync<FHIRModel.MedicationStatement>(new string[] { q });
                string filename = @"./" + person_id + "_medication_bundle.json";
                var collection = new FHIRModel.Bundle();
                collection.Type = FHIRModel.Bundle.BundleType.Collection;
                
                while( results != null )
                {
                    foreach (var entry in results.Entry)
                    {
                        var entry_id = entry.Resource.Id;
                        var resource_type = entry.Resource.TypeName.ToString();
                        var location = resource_type + "/" + entry_id;
                        var file_data = await fhir_client.ReadAsync<FHIRModel.MedicationStatement>(location);
                        var new_entry = new FHIRModel.Bundle.EntryComponent();
                        new_entry.Resource = file_data;
                        collection.Entry.Add(new_entry);
                    }
                    string json = collection.ToJson();
                    System.IO.File.AppendAllText(filename, json);
                    results = fhir_client.Continue(results);
                }
                return filename;
            } catch(Exception e) {
                Console.WriteLine("\nException Caught!");
                Console.WriteLine("Message :{0} ",e.Message);
                return null;
            }
        }

        private static FHIRModel.Person GeneratePersonData() {
            var person = new FHIRModel.Person();
            var name =  new FHIRModel.HumanName().WithGiven("Quinton").AndFamily("Klein");
            name.Use = FHIRModel.HumanName.NameUse.Official;
            person.Name.Add(name);
            person.Gender = FHIRModel.AdministrativeGender.Male;
            person.BirthDate = "1967-10-20";
            var address = new FHIRModel.Address()
            {
                Line = new string[] { "629 Schuster Common" },
                City = "Amesbury",
                State = "MA",
                PostalCode = "01913",
                Country = "USA"
            };
            person.Address.Add(address);
            return person;
        }

        private static string CreatePerson(FhirClient fhir_client)
        {
            try {
                FHIRModel.Person person = GeneratePersonData();
                var resp = fhir_client.Create<FHIRModel.Person>(person);
                return resp.IdElement.ToString(); 
            } catch(Exception e) {
                Console.WriteLine("\nException Caught!");
                Console.WriteLine("Message :{0} ",e.ToString());
                return null;
            }
        }

         private static async Task<String> getJWTAsync()
        {
            string oldjwt = Environment.GetEnvironmentVariable("JWT");
            if (oldjwt != null) 
            {  
                return oldjwt;  
            }

            string particle_host = Environment.GetEnvironmentVariable("PARTICLE_HOST");
            string client_secret = Environment.GetEnvironmentVariable("CLIENT_SECRET");
            string client_id = Environment.GetEnvironmentVariable("CLIENT_ID");
            try {
                var auth_request = new HttpRequestMessage();
                auth_request.RequestUri = new Uri(particle_host + "/auth");
                auth_request.Method = HttpMethod.Get;
                auth_request.Headers.Add("client-id",client_id);
                auth_request.Headers.Add("client-secret", client_secret);

                HttpResponseMessage response = await client.SendAsync(auth_request);
                var newjwt = await response.Content.ReadAsStringAsync();
                Environment.SetEnvironmentVariable("JWT", newjwt);
                return newjwt;
            } catch (Exception e) {
                Console.WriteLine("\nException Caught!");
                Console.WriteLine("Message :{0} ",e.Message);
                return "";
            }
        }
    }
}
