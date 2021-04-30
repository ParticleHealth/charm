package com.particlehealth.fhirstarter.sample;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.util.BundleBuilder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.cli.*;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

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
        Person person = createPerson2();

        // Register the Person.
        MethodOutcome outcome = client.create().resource(person).prettyPrint().encodedJson().accept("application/fhir+json").execute();
        logger.info("created person with id: " + outcome.getId().getIdPart());

        // Query Particle Health connected networks.
        Parameters inParams = new Parameters();
        inParams.addParameter().setName("purpose").setValue(new StringType("TREATMENT"));
        Parameters outParams = client.operation().onInstance(outcome.getId()).named("$query").withParameters(inParams).execute();

        String queryURL = outParams.getParameter("status").primitiveValue();
        String personId = outcome.getId().getIdPart();

        //Check if result is a 200, if so we're good to query for resources
        Integer result = getPersonQuery(jwt, queryURL);
        if (result == null || result != 200)
            return;


        //Once dedup is complete we won't need to handle pagination for composition resources
        List<Bundle> compositionBundles = new ArrayList<>();
        Bundle bundlePage = client.search().byUrl("/Composition?person=" + personId).returnBundle(Bundle.class).execute();
        compositionBundles.add(bundlePage);

        while (bundlePage.getLink(Bundle.LINK_NEXT) != null) {
            bundlePage = client.loadPage().byUrl(host + bundlePage.getLink(Bundle.LINK_NEXT).getUrl()).andReturnBundle(Bundle.class).execute();
            compositionBundles.add(bundlePage);
        }

        //Quadruple nested for loop. Very ugly. We could do this with Java streams but that would greatly reduce code clarity
        List<String> compositionResources = new ArrayList<>();
        for (Bundle compositionBundle : compositionBundles) {
            for (Bundle.BundleEntryComponent component : compositionBundle.getEntry()) {
                Composition b = (Composition) component.getResource();
                for (Composition.SectionComponent sectionComponent : b.getSection()) {
                    for (Reference ref : sectionComponent.getEntry()) {
                        compositionResources.add(ref.getReference());
                    }
                }
            }
        }

        //Read all entries, effectively $everything
        BundleBuilder builder = new BundleBuilder(fhirContext);
        for (String url : compositionResources) {
            IBaseResource partial = client.read().resource(Bundle.class).withUrl(url).execute();
            builder.addCollectionEntry(partial);
        }

        //Using the accumulated builder, compile the masterBundle and output it so a file.
        IParser parser = fhirContext.newJsonParser();
        parser.setPrettyPrint(true);

        IBaseBundle masterBundle = builder.getBundle();
        FileWriter writer = new FileWriter(personId + "_bundle.json");
        parser.encodeResourceToWriter(masterBundle, writer);
        writer.close();
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

    private static Integer getPersonQuery(String jwt, String url) {
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
                Thread.sleep(5000);
            }
        } catch(Exception e) {
            System.out.println(e);
            return null;
        }

        return (resp == null) ? null : resp.code();
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
      //  fhirContext.setPerformanceOptions(PerformanceOptionsEnum.DEFERRED_MODEL_SCANNING);
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

    // ~An important note about the old Java date package~
    // The Month field starts with January at 0, so subtract 1 from the calendar month when inputting the data
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

    //Person2 has composition resources
    private static Person createPerson2() {
        return new Person()
                .addName(new HumanName().setFamily("Klein").addGiven("Quinton"))
                .setGender(Enumerations.AdministrativeGender.MALE)
                .addAddress(new Address()
                        .addLine("629 Schuster Common")
                        .setCity("Amesbury")
                        .setState("MA")
                        .setPostalCode("01913"))
                .setBirthDate(new Date(67, 9, 20));
    }

    //Person3 has composition resources as well as deduped data
    private static Person createPerson3() {
        return new Person()
                .addName(new HumanName().setFamily("OConner").addGiven("Anthony"))
                .setGender(Enumerations.AdministrativeGender.MALE)
                .addAddress(new Address()
                        .addLine("1085 Kub Wynd")
                        .setCity("Boston")
                        .setState("MA")
                        .setPostalCode("02136"))
                .setBirthDate(new Date(42, 2, 22));
    }
}
