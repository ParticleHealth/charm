import fetch from "cross-fetch";
import Client from "fhir-kit-client";

const AUTH_URL = `${process.env.NEXT_PUBLIC_BASE_URL}/auth`;
const CLIENT_URL = `${process.env.NEXT_PUBLIC_BASE_URL}/R4`;

export const initFHIRClient = async () => {
  const jwt = await getToken();
  const fhirClient = new Client({
    baseUrl: CLIENT_URL,
    customHeaders: {
      "Content-Type": "application/json",
      Authorization: jwt,
    },
  });

  return fhirClient;
};

export const getToken = async () => {
  const response = await fetch(AUTH_URL, {
    method: "GET",
    headers: {
      "client-id": process.env.NEXT_PUBLIC_CLIENT_ID,
      "client-secret": process.env.NEXT_PUBLIC_CLIENT_SECRET,
    },
  });

  if (!response.ok) {
    console.error(`error with token request`, response.status);
    return;
  }

  return await response.text();
};

export const initiateQuery = async (fhirClient, personData) => {
  const person = await fhirClient.create({
    resourceType: "Person",
    body: JSON.stringify(personData),
  });

  const { id } = person;

  await fhirClient.operation({
    resourceType: "Person",
    name: "$query",
    id,
    input: {
      resourceType: "Parameters",
      parameter: [{ name: "purpose", valueString: "TREATMENT" }],
    },
  });

  let status, queryResponse;
  let progress = "STARTED";
  while (status !== 200) {
    try {
      queryResponse = await fhirClient.operation({
        resourceType: "Person",
        name: "$query",
        id,
        method: "get",
      });

      const { response } = Client.httpFor(queryResponse);

      status = response.status;
      progress = response.headers.get("x-progress");

      console.log(
        `waiting for query to complete progress=${progress} status=${status}`
      );
    } catch (error) {
      console.error("error with waiting on status", error);
      throw new Error(`error with waiting on status ${error}`);
    }
  }

  return { personId: id };
};

export const getResources = async (fhirClient, resourceType, person) => {
  try {
    const searchResponse = await fhirClient.search({
      resourceType,
      searchParams: {
        person,
      },
    });
    return searchResponse.entry;
  } catch (e) {
    console.error(e);
  }
};
