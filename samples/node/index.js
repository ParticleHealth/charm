#!/usr/bin/env node
const fetch = require('cross-fetch')
const Client = require('fhir-kit-client')
const personJson = require('./person.json')
const allResources = require('./allResources.json')
const fs = require('fs')
const yargs = require('yargs/yargs')
const {hideBin} = require('yargs/helpers')
const argv = yargs(hideBin(process.argv)).argv

const FHIR_VERSION = 'R4'
const AUTH_URL = argv.BASE_URL || 'https://sandbox.particlehealth.com'
const URL =
  `${argv.BASE_URL}/${FHIR_VERSION}` ||
  `https://sandbox.particlehealth.com/${FHIR_VERSION}`

const getToken = async () => {
  const response = await fetch(`${AUTH_URL}/auth`, {
    method: 'GET',
    headers: {
      'client-id': argv.CLIENT_ID,
      'client-secret': argv.CLIENT_SECRET,
    },
  })

  if (!response.ok) {
    console.error(`error with token request`, response.status)
    return
  }

  return await response.text()
}

const setUpPerson = async (fhirClient) => {
  // Create the person
  const fhirClientCreateRes = await fhirClient.create({
    resourceType: '/Person',
    body: JSON.stringify(personJson),
  })

  // Setup Query
  await fhirClient.operation({
    resourceType: 'Person',
    name: '$query',
    id: fhirClientCreateRes.id,
    input: {
      resourceType: 'Parameters',
      parameter: [{name: 'purpose', valueString: 'TREATMENT'}],
    },
  })

  let status, queryResponse
  let progress = 'STARTED'
  while (status !== 200) {
    try {
      queryResponse = await fhirClient.operation({
        resourceType: 'Person',
        name: '$query',
        id: fhirClientCreateRes.id,
        method: 'get',
      })
      const {response} = Client.httpFor(queryResponse)

      status = response.status
      progress = response.headers.get('x-progress')

      console.log(
        `waiting for query to complete... progress=${progress} status=${status}`
      )
    } catch (error) {
      console.error('error with waiting on status', error)
      throw new Error(`error with waiting on status ${error}`)
    }
  }

  return {personId: fhirClientCreateRes.id, queryResponse}
}

const getResources = async (
  fhirClient,
  resourceType,
  personId,
  searchParams = {}
) => {
  console.log(`========= Fetching ${resourceType} =========`)
  const searchResponse = await fhirClient.search({
    resourceType,
    searchParams: {
      id: personId,
      ...searchParams,
    },
  })

  const promises = searchResponse.entry.map((entryData) =>
    fhirClient.read({
      resourceType: entryData.resource.resourceType,
      id: entryData.resource.id,
    })
  )

  return await Promise.all(promises)
}

const getEverything = async (fhirClient, personId) => {
  console.log('************** Getting All Resources **************')

  const fileName = 'everything.json'
  const promises = allResources.map((resourceType) =>
    getResources(fhirClient, resourceType, personId)
  )

  const responses = await Promise.allSettled(promises)
  const finalResults = responses
    .filter((result) => result.value.length > 1)
    .map((result) => result.value)

  fs.writeFileSync(fileName, JSON.stringify(finalResults), 'utf-8')

  console.log(`====== ${fileName} file written ======`)
}

;(async () => {
  try {
    const token = await getToken()
    const fhirClient = new Client({
      baseUrl: URL,
      customHeaders: {
        'Content-Type': 'application/json',
        Authorization: token,
      },
    })

    // ------------------------- TOKEN IS READY ------------------------- //

    const {personId} = await setUpPerson(fhirClient)

    console.log('PATIENT IS READY!!!!')

    // ------------------------- PATIENT IS READY ------------------------- //

    // ************** Get Resources **************
    console.log('************** Getting Individual Resource **************')

    const resourceType = 'Encounter'
    const fileName = `all${resourceType}s.json`
    const resource = await getResources(fhirClient, resourceType, personId)
    fs.writeFileSync(fileName, JSON.stringify(resource), 'utf-8')

    console.log(`=============== ${fileName} WRITTEN ===============`)

    console.log(
      '************** Getting MedicationStatement Resources from the last month **************'
    )

    const today = new Date()
    const lastMonth = new Date(
      today.getFullYear(),
      today.getMonth() - 1,
      today.getDate()
    ).toISOString()
    const filteredResource = 'MedicationStatement'
    const filteredResourceFileName = `${filteredResource}-filtered.json`
    const filteredResourceResults = await getResources(
      fhirClient,
      filteredResource,
      personId,
      {
        date: `gt${lastMonth}`,
      }
    )
    fs.writeFileSync(
      filteredResourceFileName,
      JSON.stringify(filteredResourceResults),
      'utf-8'
    )

    console.log(
      `=============== ${filteredResourceFileName} WRITTEN ===============`
    )

    await getEverything(fhirClient, personId)
  } catch (err) {
    console.error('Uh ohhhhhh, somethings wrong')
    console.error(err)
  }
})()
