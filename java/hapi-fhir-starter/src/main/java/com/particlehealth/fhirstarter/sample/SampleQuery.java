package com.particlehealth.fhirstarter.sample;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.PerformanceOptionsEnum;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.util.BundleBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.particlehealth.fhirstarter.dto.OperationOutput;
import com.particlehealth.fhirstarter.dto.OperationResult;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.cli.*;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import java.util.*;

public class SampleQuery {
    private static final Logger logger = LoggerFactory.getLogger(SampleQuery.class);

    private static String clientId;
    private static String clientSecret;
    private static String host;
    private static FhirContext fhirContext;


    public static void main(String[] args) throws IOException {
        ParseArguments(args);
        //Generate the JWT to be used for calls to the FHIR server
        String jwt = authorize();
        if (jwt == null) {
            System.out.println("No JWT retrieved from auth endpoint");
            return;
        }
        // Get the client with interceptors and auth token set
        IGenericClient client = getClient(jwt);

        // Create the demographics.
        Person person = createPerson();
        // Register the Person.

        MethodOutcome outcome = client.create().resource(person).prettyPrint().encodedJson().accept("application/fhir+json").execute();
        logger.info("created person with id: " + outcome.getId().getIdPart());

        // Query Particle Health connected networks.
        Parameters inParams = new Parameters();
        inParams.addParameter().setName("purpose").setValue(new StringType("TREATMENT"));
        Parameters outParams = client.operation().onInstance(outcome.getId()).named("$query").withParameters(inParams).execute();

        String queryURL = outParams.getParameter("status").primitiveValue();

        OperationResult result = getPersonQuery(jwt, queryURL);
        //Check contents of results, if not populated return, there's no resources to pull
        if (result == null || result.getOutput() == null)
            return;

        //Loop through the returned resources
        //If we want to check for specific types of resources here we can build a mapping of resources and their urls
        //Currently we need to strip the R4/ from all resource urls
        Map<String, List<String>> resources = new HashMap<>();
        for (OperationOutput resource : result.getOutput()) {
            String url = resource.getUrl().replace("/R4/","");
            resources.computeIfAbsent(resource.getType(), k -> new ArrayList<>()).add(url);
        }

         //Loop through returned resource types and build a master bundle
        BundleBuilder builder = new BundleBuilder(fhirContext);
        for (String key: resources.keySet()) {
            for (String url : resources.get(key)) {
                IBaseResource partial = client.read().resource(key).withUrl(url).execute();
                builder.addCollectionEntry(partial);
                break;
            }
            break;
        }

        //Using the accumulated builder, compile the masterBundle
        IParser parser = fhirContext.newJsonParser();
        parser.setPrettyPrint(true);
        IBaseBundle masterBundle = builder.getBundle();
        System.out.println(parser.encodeResourceToString(masterBundle));
    }

    private static String authorize() {
        OkHttpClient client = new OkHttpClient();
        Request req = new Request.Builder().url(host + "/auth")
                .addHeader("client-id", clientId)
                .addHeader("client-secret", clientSecret)
                .build();

        try (Response resp = client.newCall(req).execute()) {
            return resp.body().string();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static OperationResult getPersonQuery(String jwt, String url) throws IOException {
        OkHttpClient client = new OkHttpClient();
        url = url.replace("R4/","");
        Request req = new Request.Builder().url(host + "/R4" + url).addHeader("Authorization", jwt).build();

        Response resp = null;
        try {
            long startTime = System.currentTimeMillis(); //fetch starting time
            while ((System.currentTimeMillis() - startTime) < 600000) {
                resp = client.newCall(req).execute();
                if (resp.code() != 202)
                    break;
                Thread.sleep(15000);
            }
        } catch(Exception e) {
            System.out.println(e);
        }
        if (resp == null || resp.code() != 200) {
            return null;
        }

        ObjectMapper mapper = new ObjectMapper();
        OperationResult res = mapper.readValue(resp.body().string(), OperationResult.class);
        Objects.requireNonNull(resp.body()).close();
        return res;
    }

    private static void ParseArguments(String[] args) {
        Options options = new Options();

        Option clientIdArg = new Option("id", "client-id", true, "client-id for requesting a JWT");
        clientIdArg.setRequired(true);
        options.addOption(clientIdArg);

        Option clientSecretArg = new Option("secret", "client-secret", true, "client-secret for requesting a JWT");
        clientSecretArg.setRequired(true);
        options.addOption(clientSecretArg);

        Option hostArg = new Option("host", "host", true, "Particle Host");
        hostArg.setRequired(true);
        options.addOption(hostArg);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println("couldn't get command line arguments " + e.getMessage());
            System.exit(1);
        }
        if (cmd == null) {
            System.out.println("couldn't get command line arguments");
            System.exit(1);
        }
        clientId = cmd.getOptionValue("client-id");
        clientSecret = cmd.getOptionValue("client-secret");
        host = cmd.getOptionValue("host");
    }

    private static IGenericClient getClient(String jwt) {
        //This is an expensive operation. Set this to be a singleton object in an actual application
        fhirContext = FhirContext.forR4();
        fhirContext.setPerformanceOptions(PerformanceOptionsEnum.DEFERRED_MODEL_SCANNING);
        IGenericClient client = fhirContext.newRestfulGenericClient(host + "/R4");
        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        loggingInterceptor.setLogRequestSummary(true);
        loggingInterceptor.setLogRequestBody(true);
        loggingInterceptor.setLogResponseHeaders(true);
        loggingInterceptor.setLogResponseBody(true);
        loggingInterceptor.setLogResponseHeaders(true);
        loggingInterceptor.setLogResponseSummary(true);
        client.registerInterceptor(loggingInterceptor);
        BearerTokenAuthInterceptor authInterceptor = new BearerTokenAuthInterceptor(jwt);
        client.registerInterceptor(authInterceptor);
        return client;
    }

    private static Person createPerson() {
        return new Person()
                .addName(new HumanName().setFamily("Aufderhar").addGiven("Federico"))
                .setGender(Enumerations.AdministrativeGender.MALE)
                .addAddress(new Address()
                        .addLine("237 Hegmann Avenue")
                        .setCity("Berkley")
                        .setState("MA")
                        .setPostalCode("02779"))
                .setBirthDate(new Date(81, 6, 12));
    }

}
